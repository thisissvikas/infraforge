import { auth } from "@/lib/auth";
import { controlPlaneApi } from "@/lib/api/control-plane";
import { RequestTimeline } from "@/components/requests/RequestTimeline";
import { redirect, notFound } from "next/navigation";

export default async function RequestDetailPage({ params }: { params: Promise<{ requestId: string }> }) {
  const { requestId } = await params;
  const session = await auth();
  if (!session) redirect("/login");

  try {
    const token = ((session as unknown) as Record<string, unknown>).cpToken as string ?? "";
    const request = await controlPlaneApi.getRequest(token, requestId);
    if (!request) notFound();

    return (
      <div className="max-w-3xl mx-auto px-6 py-8">
        <div className="mb-6">
          <a href="/requests" className="text-sm text-[var(--muted)] hover:text-[var(--accent)]">← All requests</a>
          <h1 className="text-xl font-semibold text-[var(--foreground)] mt-2">{request.rawIntent}</h1>
          <p className="text-sm text-[var(--muted)] mt-1">Request ID: {request.requestId}</p>
        </div>
        <RequestTimeline request={request} />
      </div>
    );
  } catch {
    notFound();
  }
}
