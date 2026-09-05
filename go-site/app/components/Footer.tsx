import Link from "next/link";

const footerLinks = [
  { href: "/#services", label: "Services" },
  { href: "/#opex", label: "Pricing Model" },
  { href: "/#tech", label: "Tech & Security" },
  { href: "/#contact", label: "Contact" },
  { href: "https://admin.pcncloud.in", label: "Client Login", external: true },
];

export function Footer() {
  return (
    <footer className="relative border-t border-white/10 bg-ink-950/90">
      <div className="pointer-events-none absolute inset-x-0 top-0 h-px glow-line opacity-50" />
      <div className="mx-auto flex max-w-6xl flex-col gap-8 px-4 py-12 sm:px-6 lg:flex-row lg:items-start lg:justify-between lg:px-8">
        <div>
          <Link href="/" className="font-display text-xl font-semibold text-white">
            PCN <span className="text-gradient">Cloud</span>
          </Link>
          <p className="mt-2 max-w-sm text-sm text-slate-400">
            Single-point IT, media, and hardware partner for hotels, resorts, and enterprise
            campuses.
          </p>
          <div className="mt-4 space-y-1 text-sm">
            <a href="mailto:hello@pcncloud.in" className="block text-neon-cyan hover:underline">
              hello@pcncloud.in
            </a>
            <a
              href="mailto:support@pcncloud.in"
              className="block text-slate-400 hover:text-neon-cyan"
            >
              support@pcncloud.in
            </a>
          </div>
        </div>

        <div>
          <p className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">
            Links
          </p>
          <ul className="mt-3 space-y-2">
            {footerLinks.map((l) => (
              <li key={l.href}>
                {l.external ? (
                  <a
                    href={l.href}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="text-sm text-slate-300 transition hover:text-white"
                  >
                    {l.label}
                  </a>
                ) : (
                  <Link
                    href={l.href}
                    className="text-sm text-slate-300 transition hover:text-white"
                  >
                    {l.label}
                  </Link>
                )}
              </li>
            ))}
          </ul>
        </div>
      </div>
      <div className="border-t border-white/5 py-5 text-center text-xs text-slate-500">
        © {new Date().getFullYear()} PCN Cloud · pcncloud.in · All rights reserved
      </div>
    </footer>
  );
}
