import Link from "next/link";
import type { InfraRequest } from "@/lib/api/control-plane";

const STATE_COLORS: Record<string, string> = {
  SUBMITTED: "bg-blue-100 text-blue-800",
  PR_CREATED: "bg-purple-100 text-purple-800",
  PLAN_RUNNING: "bg-yellow-100 text-yellow-800",
  PLAN_APPROVED: "bg-green-100 text-green-800",
  APPLYING: "bg-orange-100 text-orange-800",
  DEPLOYED: "bg-green-200 text-green-900",
  FAILED: "bg-red-100 text-red-800",
};

export function RequestList({ requests }: { requests: InfraRequest[] }) {
  if (!requests.length) {
    return <p className="text-[var(--muted)] text-center py-20">No requests yet. <a href="/chat" className="text-[var(--accent)] hover:underline">Start a conversation</a>.</p>;
  }
  return (
    <div className="rounded-lg border border-[var(--border)] overflow-hidden">
      <table className="w-full text-sm">
        <thead className="bg-[var(--sidebar-bg)]">
          <tr>
            <th className="text-left px-4 py-3 font-medium text-[var(--muted)]">Intent</th>
            <th className="text-left px-4 py-3 font-medium text-[var(--muted)]">State</th>
            <th className="text-left px-4 py-3 font-medium text-[var(--muted)]">Cloud</th>
            <th className="text-right px-4 py-3 font-medium text-[var(--muted)]">Cost/mo</th>
            <th className="text-right px-4 py-3 font-medium text-[var(--muted)]">Date</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-[var(--border)]">
          {requests.map(r => (
            <tr key={r.requestId} className="hover:bg-[var(--sidebar-bg)] transition-colors">
              <td className="px-4 py-3">
                <Link href={`/requests/${r.requestId}`} className="hover:text-[var(--accent)]">
                  {r.rawIntent.length > 60 ? r.rawIntent.slice(0, 60) + "…" : r.rawIntent}
                </Link>
              </td>
              <td className="px-4 py-3">
                <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${STATE_COLORS[r.state] ?? "bg-gray-100 text-gray-800"}`}>{r.state}</span>
              </td>
              <td className="px-4 py-3 text-[var(--muted)]">{"targetCloud" in r ? String((r as Record<string, unknown>).targetCloud) : "—"}</td>
              <td className="px-4 py-3 text-right text-[var(--muted)]">{r.estimatedMonthlyCostUsd != null ? `$${r.estimatedMonthlyCostUsd.toFixed(2)}` : "—"}</td>
              <td className="px-4 py-3 text-right text-[var(--muted)]">{new Date(r.createdAt).toLocaleDateString()}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
