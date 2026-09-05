import Link from "next/link";
import { Logo } from "./Logo";
import {
  EMAIL_SALES,
  EMAIL_SUPPORT,
  LOCATION_LINE,
  PHONE_DISPLAY,
  PHONE_TEL,
  WHATSAPP_URL,
} from "@/lib/contact";

const footerLinks = [
  { href: "/#properties", label: "Properties" },
  { href: "/#services", label: "Services" },
  { href: "/#leadership", label: "Leadership" },
  { href: "/#opex", label: "Pricing" },
  { href: "/#tech", label: "Platform" },
  { href: "/#contact", label: "Contact" },
  { href: "https://admin.pcncloud.in", label: "Client Login", external: true },
];

export function Footer() {
  return (
    <footer className="relative border-t border-bone/10 bg-void">
      <div className="mx-auto flex max-w-6xl flex-col gap-10 px-4 py-12 sm:px-6 sm:py-14 lg:flex-row lg:justify-between lg:px-8">
        <div>
          <Logo size="sm" className="sm:hidden" showWordmark />
          <Logo size="md" className="hidden sm:inline-flex" showWordmark />
          <p className="mt-4 max-w-sm text-sm leading-relaxed text-bone/50">
            The hospitality infrastructure partner for hotels that want one accountable
            stack — screens, networks, security, and care.
          </p>
          <div className="mt-5 space-y-1 text-sm">
            <a href={`tel:${PHONE_TEL}`} className="block text-signal-bright hover:underline">
              {PHONE_DISPLAY}
            </a>
            <a
              href={WHATSAPP_URL}
              target="_blank"
              rel="noopener noreferrer"
              className="block text-bone/70 hover:text-signal-bright"
            >
              WhatsApp chat
            </a>
            <a href={`mailto:${EMAIL_SALES}`} className="block text-bone/45 hover:text-signal-bright">
              {EMAIL_SALES}
            </a>
            <a
              href={`mailto:${EMAIL_SUPPORT}`}
              className="block text-bone/45 hover:text-signal-bright"
            >
              {EMAIL_SUPPORT}
            </a>
            <p className="pt-2 text-bone/45">{LOCATION_LINE}</p>
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
        © {new Date().getFullYear()} PCN Cloud · {LOCATION_LINE} · pcncloud.in
      </div>
    </footer>
  );
}
