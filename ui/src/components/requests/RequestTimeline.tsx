"use client";
import { useRequestStatus } from "@/hooks/useRequestStatus";
import type { InfraRequest } from "@/lib/api/control-plane";

const STATES_ORDER = ["SUBMITTED", "PR_CREATED", "PLAN_RUNNING", "PLAN_APPROVED", "APPLYING", "DEPLOYED"];
const TERMINAL = ["DEPLOYED", "FAILED"];

function TimelineStep({ label, active, done, failed }: { label: string; active: boolean; done: boolean; failed: boolean }) {
  const dot = failed ? "bg-red-500" : done ? "bg-green-500" : active ? "bg-[var(--accent)] animate-pulse" : "bg-[var(--border)]";
  return (
    <div className="flex items-center gap-3 py-2">
      <div className={`w-3 h-3 rounded-full flex-shrink-0 ${dot}`} />
      <span className={`text-sm ${active ? "font-medium text-[var(--foreground)]" : done ? "text-[var(--foreground)]" : "text-[var(--muted)]"}`}>{label}</span>
    </div>
  );
}

function TimelineContent({ request }: { request: InfraRequest }) {
  const currentIdx = STATES_ORDER.indexOf(request.state);
  const isFailed = request.state === "FAILED";

  return (
    <div className="space-y-1">
      <div className="rounded-lg border border-[var(--border)] p-4 space-y-1">
        {STATES_ORDER.map((s, i) => (
          <TimelineStep key={s} label={s.replace("_", " ")} done={i < currentIdx} active={i === currentIdx && !isFailed} failed={isFailed && i === currentIdx} />
        ))}
        {isFailed && <TimelineStep label="FAILED" active={false} done={false} failed={true} />}
      </div>
      {request.githubPrUrl && (
        <a href={request.githubPrUrl} target="_blank" rel="noreferrer" className="block text-sm text-[var(--accent)] hover:underline mt-2">View PR →</a>
      )}
      {request.estimatedMonthlyCostUsd != null && (
        <p className="text-sm text-[var(--muted)] mt-1">Estimated: ${request.estimatedMonthlyCostUsd.toFixed(2)}/month</p>
      )}
      {request.errorMessage && (
        <p className="text-sm text-red-600 mt-1">Error: {request.errorMessage}</p>
      )}
    </div>
  );
}

interface Props { request: InfraRequest }

export function RequestTimeline({ request }: Props) {
  const isInFlight = !TERMINAL.includes(request.state);
  const { data: live } = useRequestStatus(isInFlight ? request.requestId : null);
  const current = live ?? request;
  return <TimelineContent request={current} />;
}
