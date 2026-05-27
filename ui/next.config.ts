import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Agent API and Control Plane API are separate services.
  // In production these are real URLs; locally they run via docker-compose.
  async rewrites() {
    return [
      {
        source: "/agent/:path*",
        destination: `${process.env.AGENT_API_URL ?? "http://localhost:8000"}/:path*`,
      },
      {
        source: "/cp/:path*",
        destination: `${process.env.CONTROL_PLANE_URL ?? "http://localhost:8080"}/:path*`,
      },
    ];
  },
};

export default nextConfig;
