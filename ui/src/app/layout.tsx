import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "infraforge",
  description: "Talk to your infrastructure. Ship it in minutes.",
};

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
