"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { motion, AnimatePresence } from "framer-motion";
import { Menu, X } from "lucide-react";
import { Logo } from "./Logo";

const links = [
  { href: "/#properties", label: "Properties" },
  { href: "/#services", label: "Services" },
  { href: "/#leadership", label: "Leadership" },
  { href: "/#opex", label: "Pricing" },
  { href: "/#tech", label: "Platform" },
  { href: "/#contact", label: "Contact" },
];

export function Navbar() {
  const [scrolled, setScrolled] = useState(false);
  const [open, setOpen] = useState(false);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 12);
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  useEffect(() => {
    if (!open) return;
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = prev;
    };
  }, [open]);

  return (
    <header
      className={`fixed inset-x-0 top-0 z-50 transition-all duration-500 ${
        scrolled || open
          ? "border-b border-bone/10 bg-void/90 backdrop-blur-xl"
          : "bg-transparent"
      }`}
      style={{ paddingTop: "env(safe-area-inset-top)" }}
    >
      <div className="mx-auto flex h-14 max-w-6xl items-center justify-between px-4 sm:h-[4.25rem] sm:px-6 lg:px-8">
        <Logo size="sm" className="sm:hidden" priority />
        <Logo size="md" className="hidden sm:inline-flex" priority />

        <nav className="hidden items-center gap-1 md:flex">
          {links.map((l) => (
            <Link
              key={l.href}
              href={l.href}
              className="rounded-md px-3.5 py-2 text-sm font-medium text-bone/70 transition hover:bg-bone/5 hover:text-bone"
            >
              {l.label}
            </Link>
          ))}
          <a
            href="https://admin.pcncloud.in"
            target="_blank"
            rel="noopener noreferrer"
            className="btn-primary ml-3 !w-auto !px-5 !py-2.5 text-xs"
          >
            Client Login
          </a>
        </nav>

        <button
          type="button"
          className="inline-flex h-11 w-11 items-center justify-center rounded-md border border-bone/15 text-bone md:hidden"
          aria-expanded={open}
          aria-label={open ? "Close menu" : "Open menu"}
          onClick={() => setOpen((v) => !v)}
        >
          {open ? <X size={20} /> : <Menu size={20} />}
        </button>
      </div>

      <AnimatePresence>
        {open && (
          <motion.div
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: "auto" }}
            exit={{ opacity: 0, height: 0 }}
            className="overflow-hidden border-t border-bone/10 bg-void/98 backdrop-blur-xl md:hidden"
          >
            <nav
              className="flex max-h-[calc(100svh-3.5rem-env(safe-area-inset-top))] flex-col gap-1 overflow-y-auto px-4 py-3 safe-pb"
              aria-label="Mobile"
            >
              {links.map((l) => (
                <Link
                  key={l.href}
                  href={l.href}
                  onClick={() => setOpen(false)}
                  className="rounded-md px-3 py-3.5 text-base font-medium text-bone hover:bg-bone/5"
                >
                  {l.label}
                </Link>
              ))}
              <a
                href="https://admin.pcncloud.in"
                target="_blank"
                rel="noopener noreferrer"
                className="btn-primary mt-2"
                onClick={() => setOpen(false)}
              >
                Client Login
              </a>
            </nav>
          </motion.div>
        )}
      </AnimatePresence>
    </header>
  );
}
