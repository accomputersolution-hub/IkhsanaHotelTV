import Link from "next/link";

const footerLinks = [
  { href: "/#services", label: "Services" },
  { href: "/#opex", label: "Pricing" },
  { href: "/#tech", label: "Platform" },
  { href: "/#contact", label: "Contact" },
  { href: "https://admin.pcncloud.in", label: "Client Login", external: true },
];

export function Footer() {
  return (
    <footer className="relative border-t border-white/10">
      <div className="pointer-events-none absolute inset-x-0 top-0 h-px glow-line opacity-60" />
      <div className="mx-auto flex max-w-6xl flex-col gap-10 px-4 py-14 sm:px-6 lg:flex-row lg:justify-between lg:px-8">
        <div>
          <Link href="/" className="font-display text-2xl font-semibold text-white">
            PCN <span className="text-gradient">Cloud</span>
          </Link>
          <p className="mt-3 max-w-sm text-sm leading-relaxed text-slate-400">
            The hospitality infrastructure partner for hotels that want one accountable
            stack — screens, networks, security, and care.
          </p>
          <div className="mt-5 space-y-1 text-sm">
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
          <p className="text-[11px] font-semibold uppercase tracking-[0.22em] text-champagne-dim">
            Navigate
          </p>
          <ul className="mt-4 space-y-2">
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
        © {new Date().getFullYear()} PCN Cloud · pcncloud.in · Crafted for hospitality ops
      </div>
    </footer>
  );
}
