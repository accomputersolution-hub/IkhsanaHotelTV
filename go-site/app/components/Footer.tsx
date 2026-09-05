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
    <footer className="relative border-t border-bone/10 bg-void">
      <div className="mx-auto flex max-w-6xl flex-col gap-10 px-4 py-14 sm:px-6 lg:flex-row lg:justify-between lg:px-8">
        <div>
          <Link href="/" className="font-display text-2xl font-semibold text-bone">
            PCN Cloud
          </Link>
          <p className="mt-3 max-w-sm text-sm leading-relaxed text-bone/50">
            The hospitality infrastructure partner for hotels that want one accountable
            stack — screens, networks, security, and care.
          </p>
          <div className="mt-5 space-y-1 text-sm">
            <a href="mailto:hello@pcncloud.in" className="block text-signal-bright hover:underline">
              hello@pcncloud.in
            </a>
            <a
              href="mailto:support@pcncloud.in"
              className="block text-bone/45 hover:text-signal-bright"
            >
              support@pcncloud.in
            </a>
          </div>
        </div>

        <div>
          <p className="text-[11px] font-semibold uppercase tracking-[0.22em] text-brass">
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
                    className="text-sm text-bone/60 transition hover:text-bone"
                  >
                    {l.label}
                  </a>
                ) : (
                  <Link
                    href={l.href}
                    className="text-sm text-bone/60 transition hover:text-bone"
                  >
                    {l.label}
                  </Link>
                )}
              </li>
            ))}
          </ul>
        </div>
      </div>
      <div className="border-t border-bone/5 py-5 text-center text-xs text-bone/35">
        © {new Date().getFullYear()} PCN Cloud · pcncloud.in · Crafted for hospitality ops
      </div>
    </footer>
  );
}
