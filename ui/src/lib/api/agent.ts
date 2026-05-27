/**
 * Typed API client for the Chat Agent (FastAPI / LangGraph).
 */

export interface ChatMessage {
  role: "user" | "assistant";
  content: string;
}

export interface ChatResponse {
  sessionId: string;
  message: ChatMessage;
  requestId: string | null; // set when the agent has submitted a request
}

export async function sendChatMessage(
  token: string,
  sessionId: string,
  userMessage: string
): Promise<ChatResponse> {
  const res = await fetch("/agent/chat", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify({ sessionId, message: userMessage }),
  });

  if (!res.ok) {
    const text = await res.text().catch(() => res.statusText);
    throw new Error(`Agent error ${res.status}: ${text}`);
  }
  return res.json() as Promise<ChatResponse>;
}
