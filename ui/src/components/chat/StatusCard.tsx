"use client";
import { useRequestStatus } from "@/hooks/useRequestStatus";

const STATE_COLORS: Record<string, string> = {
  SUBMITTED: "bg-blue-100 text-blue-800",
  PR_CREATED: "bg-purple-100 text-purple-800",
  PLAN_RUNNING: "bg-yellow-100 text-yellow-800",
  PLAN_APPROVED: "bg-green-100 text-green-800",
  APPLYING: "bg-orange-100 text-orange-800",
  DEPLOYED: "bg-green-200 text-green-900",
  FAILED: "bg-red-100 text-red-800",
};

export function StatusCard({ requestId }: { requestId: string }) {
  const { data, isLoading } = useRequestStatus(requestId);

  if (isLoading && !data) {
    return <div className="mt-2 rounded-lg border border-[var(--border)] p-3 text-sm text-[var(--muted)]">Loading status…</div>;
  }
  if (!data) return null;

  const colorClass = STATE_COLORS[data.state] ?? "bg-gray-100 text-gray-800";

  return (
    <div className="mt-2 rounded-lg border border-[var(--border)] bg-[var(--sidebar-bg)] p-3 text-sm space-y-1">
      <div className="flex items-center gap-2">
        <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${colorClass}`}>{data.state}</span>
        <span className="text-[var(--muted)] text-xs">{requestId}</span>
      </div>
      {data.githubPrUrl && (
        <a href={data.githubPrUrl} target="_blank" rel="noreferrer" className="text-[var(--accent)] hover:underline text-xs">
          View PR →
        </a>
      )}
      {data.estimatedMonthlyCostUsd != null && (
        <p className="text-[var(--muted)] text-xs">${data.estimatedMonthlyCostUsd.toFixed(2)}/month</p>
      )}
    </div>
  );
}
