"use client";
import { use, useEffect, useRef } from "react";
import { useChatSession } from "@/hooks/useChatSession";
import { MessageBubble } from "@/components/chat/MessageBubble";
import { ChatInput } from "@/components/chat/ChatInput";
import { StatusCard } from "@/components/chat/StatusCard";

export default function ChatPage({ params }: { params: Promise<{ sessionId: string }> }) {
  const { sessionId } = use(params);
  const { messages, isLoading, requestId, sendMessage } = useChatSession(sessionId);
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  return (
    <div className="flex flex-col h-screen bg-[var(--background)]">
      <header className="flex items-center px-6 py-4 border-b border-[var(--border)]">
        <h1 className="text-lg font-semibold text-[var(--foreground)]">infraforge</h1>
        <span className="ml-2 text-sm text-[var(--muted)]">— chat</span>
      </header>

      <div className="flex-1 overflow-y-auto px-4 py-6 max-w-3xl mx-auto w-full">
        {messages.length === 0 && (
          <div className="text-center text-[var(--muted)] mt-20">
            <p className="text-lg font-medium">Talk to your infrastructure.</p>
            <p className="text-sm mt-1">Try: &quot;Create an S3 bucket for my team&quot; or &quot;Set up a PostgreSQL database in dev&quot;</p>
          </div>
        )}
        {messages.map(msg => (
          <MessageBubble key={msg.id} message={msg} />
        ))}
        {requestId && <StatusCard requestId={requestId} />}
        {isLoading && (
          <div className="flex justify-start mb-4">
            <div className="bg-[var(--sidebar-bg)] rounded-2xl rounded-bl-sm px-4 py-3 text-sm text-[var(--muted)]">
              <span className="animate-pulse">Thinking…</span>
            </div>
          </div>
        )}
        <div ref={bottomRef} />
      </div>

      <ChatInput onSend={sendMessage} disabled={isLoading} />
    </div>
  );
}
