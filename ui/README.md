# infraforge — UI

The developer-facing web portal. Provides a chat interface to the Chat Agent and a request history dashboard backed by the Control Plane.

---

## Responsibilities

| Owns | Does NOT own |
|---|---|
| Developer chat UI | Terraform generation |
| Request history + status display | State machine |
| GitHub OAuth login flow | Email notifications |
| Polling for request status updates | Business logic |

---

## Tech Stack

| Concern | Technology |
|---|---|
| Framework | Next.js 15 (App Router) |
| UI library | React 19 |
| Language | TypeScript 5.x |
| Styling | Tailwind CSS 4 |
| Auth | Auth.js v5 (NextAuth) — GitHub provider |
| Data fetching | TanStack Query v5 (polling) |
| Markdown rendering | react-markdown + remark-gfm |
| Icons | lucide-react |

---

## Architecture

### Authentication Flow

```mermaid
sequenceDiagram
    autonumber
    actor Dev as Developer
    participant UI as Next.js UI
    participant AS as Auth.js (server)
    participant GH as GitHub OAuth
    participant CP as Control Plane

    Dev->>UI: Click "Sign in with GitHub"
    UI->>AS: Server Action: signIn("github")
    AS->>GH: Redirect to github.com/login/oauth/authorize
    GH-->>Dev: Show GitHub consent screen
    Dev->>GH: Approve
    GH-->>AS: Redirect to /api/auth/callback/github?code=...
    AS->>GH: Exchange code → GitHub access token
    AS->>AS: Trigger jwt() callback
    AS->>CP: POST /auth/token {githubAccessToken}
    CP->>GH: GET /user (verify token)
    CP-->>AS: {token: "<CP JWT>", expiresAt: ...}
    AS->>AS: Store CP JWT in encrypted session cookie
    AS-->>UI: Redirect to /chat
    UI->>CP: GET /api/me (Bearer <CP JWT>)
    CP-->>UI: {userId, login, email}
```

---

### Chat Interaction Flow

```mermaid
sequenceDiagram
    autonumber
    actor Dev as Developer
    participant UI as Chat Page
    participant Agent as Chat Agent API
    participant CP as Control Plane

    Dev->>UI: Type message, press Enter
    UI->>UI: Add optimistic user bubble
    UI->>Agent: POST /agent/chat\n{sessionId, message} + Bearer JWT

    Agent->>Agent: Run LangGraph graph\n(may take 5-15s for generation)

    alt Needs clarification
        Agent-->>UI: {message: "Are you expecting PCI data?"}
        UI->>UI: Render assistant bubble
        Dev->>UI: "Yes, PCI"
        UI->>Agent: POST /agent/chat {sessionId, "Yes, PCI"}
    end

    alt Request submitted
        Agent->>CP: POST /internal/requests (service key)
        CP-->>Agent: {requestId: "req-abc"}
        Agent-->>UI: {message: "Submitted ✓", requestId: "req-abc"}
        UI->>UI: Render StatusCard (req-abc)
        loop Poll every 10s while in-flight
            UI->>CP: GET /api/requests/req-abc + Bearer JWT
            CP-->>UI: {state: "PLAN_RUNNING", prUrl: "..."}
            UI->>UI: Update StatusCard badge
        end
    else Normal response
        Agent-->>UI: {message: "...", requestId: null}
        UI->>UI: Render assistant bubble
    end
```

---

### Request History Flow

```mermaid
flowchart TD
    A(["/requests page load"]) --> B["Server Component:\nfetch /api/requests\n(Bearer JWT from cookie)"]
    B --> C{Requests?}
    C -->|"none"| D["Empty state:\n'No requests yet'"]
    C -->|"has requests"| E["RequestList\nTable with state badges"]
    E --> F{User clicks row}
    F --> G["/requests/{id} page"]
    G --> H["Server Component:\nfetch /api/requests/{id}"]
    H --> I["RequestTimeline\nState history with timestamps"]
    I --> J{State in-flight?}
    J -->|"yes: SUBMITTED→APPLYING"| K["TanStack Query\npoll every 10s\nupdate timeline live"]
    J -->|"no: DEPLOYED/FAILED"| L["Static view\n(no polling)"]
```

---

### Route Structure

```mermaid
graph TD
    root["/"] -->|redirect| chat

    subgraph "Protected (middleware)"
        chat["/chat"]
        chat_session["/chat/{sessionId}"]
        requests["/requests"]
        request_detail["/requests/{requestId}"]
    end

    subgraph "Public"
        login["/login\n(GitHub sign-in button)"]
        auth_callback["/api/auth/[...nextauth]\n(Auth.js handlers)"]
    end

    root -->|"unauthenticated"| login
    chat --> chat_session
    requests --> request_detail
```

---

### API Client Design

The UI talks to two backends using typed clients in `src/lib/api/`:

```mermaid
graph LR
    subgraph "Next.js App"
        SC["Server Components\n(history, static data)"]
        CC["Client Components\n(chat, live status)"]
    end

    subgraph "src/lib/api/"
        CP_CLIENT["control-plane.ts\ncontrolPlaneApi.{listRequests,\ngetRequest, getMe}"]
        AGENT_CLIENT["agent.ts\nsendChatMessage()"]
    end

    subgraph "Backends"
        CP["Control Plane\n:8080 → /cp/** (rewrite)"]
        AGENT["Chat Agent\n:8000 → /agent/** (rewrite)"]
    end

    SC -->|"JWT from session\n(server-side)"| CP_CLIENT
    CC -->|"JWT from session\n(TanStack Query)"| CP_CLIENT
    CC -->|"JWT from session"| AGENT_CLIENT
    CP_CLIENT --> CP
    AGENT_CLIENT --> AGENT
```

Next.js rewrites in `next.config.ts` proxy `/cp/**` → Control Plane and `/agent/**` → Chat Agent, so the browser never needs to know the backend URLs.

---

## Package Structure

```
ui/
├── package.json
├── next.config.ts          # Rewrites: /cp/** → CP, /agent/** → agent
├── tsconfig.json
├── postcss.config.mjs      # Tailwind 4
├── .env.example
└── src/
    ├── middleware.ts        # Redirect unauthenticated users to /login
    ├── app/
    │   ├── layout.tsx
    │   ├── page.tsx         # Redirects to /chat
    │   ├── globals.css      # Tailwind + CSS variables
    │   ├── (auth)/
    │   │   └── login/
    │   │       └── page.tsx         # GitHub sign-in button
    │   ├── chat/                    # (Phase 4)
    │   │   └── [sessionId]/
    │   │       └── page.tsx
    │   ├── requests/                # (Phase 4)
    │   │   ├── page.tsx
    │   │   └── [requestId]/
    │   │       └── page.tsx
    │   └── api/
    │       └── auth/
    │           └── [...nextauth]/
    │               └── route.ts     # Auth.js route handler
    ├── components/                  # (Phase 4)
    │   ├── chat/
    │   │   ├── ChatInput.tsx
    │   │   ├── MessageBubble.tsx
    │   │   └── StatusCard.tsx       # Embedded request status in chat
    │   └── requests/
    │       ├── RequestList.tsx
    │       └── RequestTimeline.tsx
    └── lib/
        ├── auth.ts          # Auth.js config: GitHub → CP JWT exchange
        └── api/
            ├── control-plane.ts  # Typed CP API client
            └── agent.ts          # Typed agent API client
```

---

## Running Locally

```bash
cd ui
cp .env.example .env.local
# Fill in GITHUB_CLIENT_ID, GITHUB_CLIENT_SECRET, AUTH_SECRET

npm install
npm run dev     # http://localhost:3000

# Type checking
npm run type-check
```

**GitHub OAuth App setup:**
1. Go to GitHub → Settings → Developer settings → OAuth Apps → New OAuth App
2. Homepage URL: `http://localhost:3000`
3. Authorization callback URL: `http://localhost:3000/api/auth/callback/github`

---

## Environment Variables

| Variable | Description |
|---|---|
| `GITHUB_CLIENT_ID` | GitHub OAuth App client ID |
| `GITHUB_CLIENT_SECRET` | GitHub OAuth App client secret |
| `AUTH_SECRET` | Auth.js session encryption key (`openssl rand -base64 32`) |
| `CONTROL_PLANE_URL` | Control Plane base URL (default: `http://localhost:8080`) |
| `AGENT_API_URL` | Chat Agent base URL (default: `http://localhost:8000`) |
