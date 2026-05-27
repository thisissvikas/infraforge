/**
 * Typed API client for the Control Plane.
 * All requests carry the Control Plane JWT from the Auth.js session.
 */

export type RequestStateType =
  | "SUBMITTED"
  | "PR_CREATED"
  | "PLAN_RUNNING"
  | "PLAN_APPROVED"
  | "APPLYING"
  | "DEPLOYED"
  | "FAILED";

export interface InfraRequest {
  requestId: string;
  userId: string;
  teamId: string;
  rawIntent: string;
  state: RequestStateType;
  githubPrUrl: string | null;
  githubBranch: string | null;
  estimatedMonthlyCostUsd: number;
  errorMessage: string | null;
  createdAt: string; // ISO-8601
  updatedAt: string; // ISO-8601
}

export interface PagedRequests {
  items: InfraRequest[];
  nextToken: string | null;
}

async function cpFetch<T>(
  path: string,
  token: string,
  init?: RequestInit
): Promise<T> {
  const res = await fetch(`/cp${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
      ...(init?.headers ?? {}),
    },
  });

  if (!res.ok) {
    const text = await res.text().catch(() => res.statusText);
    throw new Error(`Control Plane error ${res.status}: ${text}`);
  }
  return res.json() as Promise<T>;
}

export const controlPlaneApi = {
  listRequests: (token: string, nextToken?: string) =>
    cpFetch<PagedRequests>(
      `/api/requests${nextToken ? `?nextToken=${nextToken}` : ""}`,
      token
    ),

  getRequest: (token: string, requestId: string) =>
    cpFetch<InfraRequest>(`/api/requests/${requestId}`, token),

  getMe: (token: string) =>
    cpFetch<{ userId: string; email: string; login: string }>(`/api/me`, token),
};
