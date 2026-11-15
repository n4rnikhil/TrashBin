import { AppShell } from "@/components/layout/app-shell";
import { AuthGate } from "@/features/auth";

export default function AppLayout({ children }: LayoutProps<"/">) {
  return (
    <AuthGate>
      <AppShell>{children}</AppShell>
    </AuthGate>
  );
}
