"use client";
import { useQuery } from "@tanstack/react-query";
import { useSession } from "next-auth/react";
import { controlPlaneApi } from "@/lib/api/control-plane";

const TERMINAL_STATES = ["DEPLOYED", "FAILED"];

export function useRequestStatus(requestId: string | null) {
  const { data: session } = useSession();
  const token = ((session as unknown) as Record<string, unknown> | null)?.cpToken as string ?? "";

  return useQuery({
    queryKey: ["request", requestId],
    queryFn: () => controlPlaneApi.getRequest(token, requestId!),
    enabled: !!requestId && !!token,
    refetchInterval: (query) => {
      const state = query.state.data?.state;
      return state && TERMINAL_STATES.includes(state) ? false : 10_000;
    },
  });
}
