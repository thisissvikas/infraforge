"""Abstract port for policy retrieval — decouples the graph from Bedrock KB."""
from __future__ import annotations

from abc import ABC, abstractmethod
from dataclasses import dataclass


@dataclass(frozen=True)
class PolicyContext:
    chunks: list[str]
    approved_modules: list[str]
    budget_ceiling_usd: float


class PolicyStorePort(ABC):
    """Retrieve org policy context relevant to a given intent."""

    @abstractmethod
    async def retrieve(self, team_id: str, intent_summary: str) -> PolicyContext:
        """Return policy chunks and module list most relevant to the request."""
        ...
