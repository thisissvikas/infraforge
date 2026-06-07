import asyncio
import json
import shutil
import tempfile
from pathlib import Path

from pydantic import BaseModel


class LintViolation(BaseModel):
    tool: str
    rule: str
    message: str
    severity: str = "HIGH"


async def run_tfsec(terraform_files: dict[str, str]) -> list[LintViolation]:
    if not shutil.which("tfsec"):
        return []
    with tempfile.TemporaryDirectory() as tmp:
        for name, content in terraform_files.items():
            Path(tmp, name).write_text(content)
        proc = await asyncio.create_subprocess_exec(
            "tfsec",
            tmp,
            "--format",
            "json",
            "--no-color",
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
        )
        stdout, _ = await proc.communicate()
        try:
            data = json.loads(stdout)
            results = data.get("results") or []
            return [
                LintViolation(
                    tool="tfsec",
                    rule=r.get("rule_id", ""),
                    message=r.get("description", ""),
                    severity=r.get("severity", "HIGH"),
                )
                for r in results
            ]
        except Exception:
            return []


async def run_checkov(terraform_files: dict[str, str]) -> list[LintViolation]:
    if not shutil.which("checkov"):
        return []
    with tempfile.TemporaryDirectory() as tmp:
        for name, content in terraform_files.items():
            Path(tmp, name).write_text(content)
        proc = await asyncio.create_subprocess_exec(
            "checkov",
            "-d",
            tmp,
            "-o",
            "json",
            "--quiet",
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
        )
        stdout, _ = await proc.communicate()
        try:
            data = json.loads(stdout)
            failures = []
            for check_type in data.get("results", {}).get("failed_checks", []):
                failures.append(
                    LintViolation(
                        tool="checkov",
                        rule=check_type.get("check_id", ""),
                        message=check_type.get("check_result", {}).get("result", "FAILED"),
                    )
                )
            return failures
        except Exception:
            return []
