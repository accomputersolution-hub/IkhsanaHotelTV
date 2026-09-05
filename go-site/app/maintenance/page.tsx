import type { Metadata } from "next";
import Image from "next/image";
import { MAINTENANCE_EMAIL } from "@/lib/maintenance";

export const metadata: Metadata = {
  title: "PCN Cloud — Upgrading Infrastructure",
  description:
    "PCN Cloud is temporarily upgrading IT infrastructure. We will be back online shortly.",
  robots: { index: false, follow: false },
};

export default function MaintenancePage() {
  return (
    <main className="relative flex min-h-[100svh] items-center justify-center overflow-hidden bg-void px-6 py-16 text-bone">
      <div
        className="pointer-events-none absolute inset-0"
        style={{
          background:
            "radial-gradient(ellipse 70% 50% at 50% -10%, rgba(26,155,142,0.18), transparent 55%), radial-gradient(ellipse 45% 40% at 100% 100%, rgba(198,163,107,0.1), transparent 50%)",
        }}
      />
      <div className="pointer-events-none absolute inset-0 film-grain" aria-hidden />

      <div className="relative z-10 mx-auto flex w-full max-w-2xl flex-col items-center text-center">
        <div className="relative mb-10 h-20 w-20 overflow-hidden rounded-full border border-bone/15 shadow-deep sm:h-24 sm:w-24">
          <Image
            src="/images/pcncloud-logo.jpg"
            alt="PCN Cloud"
            fill
            priority
            className="object-cover"
            sizes="96px"
          />
        </div>

        <p className="eyebrow">Under maintenance</p>

        <h1 className="mt-5 font-display text-4xl font-semibold tracking-tight text-bone sm:text-5xl md:text-6xl">
          PCN Cloud
        </h1>

        <p className="mt-6 max-w-xl text-lg leading-relaxed text-bone/75 sm:text-xl">
          Upgrading our IT Infrastructure.
          <br className="hidden sm:block" /> We will be back online shortly.
        </p>

        <div className="mt-10 h-px w-28 bg-gradient-to-r from-transparent via-brass/70 to-transparent" />

        <p className="mt-8 text-xs font-semibold uppercase tracking-[0.24em] text-bone/40">
          Urgent inquiries
        </p>
        <a
          href={`mailto:${MAINTENANCE_EMAIL}`}
          className="mt-3 font-display text-xl text-signal-bright transition hover:text-signal sm:text-2xl"
        >
          {MAINTENANCE_EMAIL}
        </a>
      </div>
    </main>
  );
}
