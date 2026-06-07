FROM python:3.13-slim
WORKDIR /app

# Install uv
COPY --from=ghcr.io/astral-sh/uv:0.5.21 /uv /uvx /usr/local/bin/

# Copy dependency files first for layer caching
COPY pyproject.toml ./
RUN uv sync --no-dev

# Copy source
COPY agent/ agent/

EXPOSE 8000
CMD ["uv", "run", "uvicorn", "agent.api.main:app", "--host", "0.0.0.0", "--port", "8000"]
