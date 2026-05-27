import NextAuth from "next-auth";
import GitHub from "next-auth/providers/github";

export const { handlers, auth, signIn, signOut } = NextAuth({
  providers: [
    GitHub({
      clientId: process.env.GITHUB_CLIENT_ID!,
      clientSecret: process.env.GITHUB_CLIENT_SECRET!,
    }),
  ],

  callbacks: {
    async jwt({ token, account }) {
      // On first sign-in, exchange the GitHub access token for a Control Plane JWT.
      // The Control Plane JWT is what all API calls use subsequently.
      if (account?.access_token) {
        const res = await fetch(
          `${process.env.CONTROL_PLANE_URL}/auth/token`,
          {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ githubAccessToken: account.access_token }),
          }
        );
        if (res.ok) {
          const { token: cpToken, expiresAt } = await res.json();
          token.cpToken = cpToken;
          token.cpTokenExpiresAt = expiresAt;
        }
      }
      return token;
    },

    async session({ session, token }) {
      // Expose the Control Plane JWT to client-side API calls.
      session.cpToken = token.cpToken as string | undefined;
      return session;
    },
  },

  pages: {
    signIn: "/login",
  },
});

// Module augmentation so TypeScript knows about cpToken on Session/JWT.
declare module "next-auth" {
  interface Session {
    cpToken?: string;
  }
}

declare module "next-auth/jwt" {
  interface JWT {
    cpToken?: string;
    cpTokenExpiresAt?: number;
  }
}
