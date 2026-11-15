import { BrandLockup } from "@/components/layout/brand";
import { AnonymousOnly } from "@/features/auth";

/** Centered layout for the sign-in and sign-up pages. */
export default function PublicLayout({ children }: LayoutProps<"/">) {
  return (
    <AnonymousOnly>
      <main className="flex flex-1 flex-col items-center justify-center gap-8 px-4 py-12">
        <BrandLockup />
        <div className="bg-card ring-border w-full max-w-sm rounded-2xl p-6 shadow-sm ring-1 sm:p-8">
          {children}
        </div>
        <p className="text-subtle-foreground type-caption font-normal">
          Nothing is truly trash until you decide it is.
        </p>
      </main>
    </AnonymousOnly>
  );
}
