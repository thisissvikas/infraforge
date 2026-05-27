import { redirect } from "next/navigation";

// Root redirects to chat — authenticated users land on /chat,
// unauthenticated users are redirected to /login by the middleware.
export default function Home() {
  redirect("/chat");
}
