import { auth } from "@/lib/auth";
import { controlPlaneApi } from "@/lib/api/control-plane";
import { RequestList } from "@/components/requests/RequestList";
import { redirect } from "next/navigation";
import type { InfraRequest } from "@/lib/api/control-plane";

export default async function RequestsPage() {
  const session = await auth();
  if (!session) redirect("/login");

  let requests: InfraRequest[] = [];
  try {
    const token = ((session as unknown) as Record<string, unknown>).cpToken as string ?? "";
    const data = await controlPlaneApi.listRequests(token);
    requests = data.items ?? [];
  } catch {
    requests = [];
  }

  return (
    <div className="max-w-5xl mx-auto px-6 py-8">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-semibold text-[var(--foreground)]">Infrastructure Requests</h1>
        <a href="/chat" className="text-sm text-[var(--accent)] hover:underline">+ New request</a>
      </div>
      <RequestList requests={requests} />
    </div>
  );
}
