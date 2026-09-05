"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { motion, AnimatePresence } from "framer-motion";
import { Menu, X } from "lucide-react";

const links = [
  { href: "/#services", label: "Services" },
  { href: "/#opex", label: "Pricing Model" },
  { href: "/#tech", label: "Tech & Security" },
  { href: "/#contact", label: "Contact Us" },
];

export function Navbar() {
  const [scrolled, setScrolled] = useState(false);
  const [open, setOpen] = useState(false);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 16);
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  return (
    <header
      className={`fixed inset-x-0 top-0 z-50 transition-all duration-300 ${
        scrolled ? "border-b border-white/10 bg-ink-950/75 backdrop-blur-xl" : "bg-transparent"
      }`}
    >
      <div className="mx-auto flex h-16 max-w-6xl items-center justify-between px-4 sm:px-6 lg:h-[4.25rem] lg:px-8">
        <Link
          href="/"
          className="font-display text-lg font-semibold tracking-tight text-white sm:text-xl"
        >
          PCN <span className="text-gradient">Cloud</span>
        </Link>

        <nav className="hidden items-center gap-8 md:flex">
          {links.map((l) => (
            <Link
              key={l.href}
              href={l.href}
              className="text-sm font-medium text-slate-300 transition hover:text-white"
            >
              {l.label}
            </Link>
          ))}
          <a
            href="https://admin.pcncloud.in"
            target="_blank"
            rel="noopener noreferrer"
            className="btn-primary !px-5 !py-2.5 text-xs"
          >
            Client Login
          </a>
        </nav>

        <button
          type="button"
          className="rounded-lg border border-white/10 p-2 text-white md:hidden"
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
            className="border-t border-white/10 bg-ink-950/95 backdrop-blur-xl md:hidden"
          >
            <div className="flex flex-col gap-1 px-4 py-4">
              {links.map((l) => (
                <Link
                  key={l.href}
                  href={l.href}
                  onClick={() => setOpen(false)}
                  className="rounded-lg px-3 py-3 text-sm font-medium text-slate-200 hover:bg-white/5"
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
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </header>
  );
}
