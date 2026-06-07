"use client";
import { useState, useCallback } from "react";
import { useSession } from "next-auth/react";
import { sendChatMessage } from "@/lib/api/agent";

export interface Message {
  id: string;
  role: "user" | "assistant";
  content: string;
}

export function useChatSession(sessionId: string) {
  const { data: session } = useSession();
  const [messages, setMessages] = useState<Message[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [requestId, setRequestId] = useState<string | null>(null);

  const sendMessage = useCallback(async (text: string) => {
    if (!text.trim() || isLoading) return;
    const userMsg: Message = { id: crypto.randomUUID(), role: "user", content: text };
    setMessages(prev => [...prev, userMsg]);
    setIsLoading(true);
    try {
      const token = ((session as unknown) as Record<string, unknown> | null)?.cpToken as string ?? "";
      const resp = await sendChatMessage(token, sessionId, text);
      const assistantMsg: Message = { id: crypto.randomUUID(), role: "assistant", content: resp.message.content };
      setMessages(prev => [...prev, assistantMsg]);
      if (resp.requestId) setRequestId(resp.requestId);
    } catch {
      setMessages(prev => [...prev, { id: crypto.randomUUID(), role: "assistant", content: "Sorry, something went wrong. Please try again." }]);
    } finally {
      setIsLoading(false);
    }
  }, [session, sessionId, isLoading]);

  return { messages, isLoading, requestId, sendMessage };
}
