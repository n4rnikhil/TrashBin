import type { Metadata } from "next";
import { Heading, Text } from "@/components/ui/typography";
import { RegisterForm } from "@/features/auth";

export const metadata: Metadata = { title: "Create account" };

export default function RegisterPage() {
  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-1">
        <Heading level={1} variant="title">
          Create your account
        </Heading>
        <Text tone="muted">A private place for the files you are not ready to lose.</Text>
      </div>
      <RegisterForm />
    </div>
  );
}
