import type { Metadata } from "next";
import { Heading, Text } from "@/components/ui/typography";
import { LoginForm } from "@/features/auth";

export const metadata: Metadata = { title: "Sign in" };

export default function LoginPage() {
  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-1">
        <Heading level={1} variant="title">
          Welcome back
        </Heading>
        <Text tone="muted">Sign in to your archive.</Text>
      </div>
      <LoginForm />
    </div>
  );
}
