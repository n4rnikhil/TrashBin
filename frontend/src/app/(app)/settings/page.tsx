import type { Metadata } from "next";
import { PageHeader } from "@/components/patterns/page-header";
import { PasswordCard, ProfileCard, SessionsCard } from "@/features/auth";

export const metadata: Metadata = { title: "Settings" };

export default function SettingsPage() {
  return (
    <>
      <PageHeader
        title="Settings"
        description="Manage your profile, password and signed-in devices."
      />
      <div className="flex max-w-3xl flex-col gap-6">
        <ProfileCard />
        <PasswordCard />
        <SessionsCard />
      </div>
    </>
  );
}
