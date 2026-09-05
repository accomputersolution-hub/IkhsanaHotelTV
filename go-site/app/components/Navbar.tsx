"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { motion, AnimatePresence } from "framer-motion";
import { Menu, X } from "lucide-react";
import { Logo } from "./Logo";

const links = [
  { href: "/#properties", label: "Properties" },
  { href: "/#services", label: "Services" },
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

  return (
    <header
      className={`fixed inset-x-0 top-0 z-50 transition-all duration-500 ${
        scrolled
          ? "border-b border-bone/10 bg-void/80 backdrop-blur-xl"
          : "bg-transparent"
      }`}
    >
      <div className="mx-auto flex h-[4.25rem] max-w-6xl items-center justify-between px-4 sm:px-6 lg:px-8">
        <Logo size="md" priority />

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
            className="btn-primary ml-3 !px-5 !py-2.5 text-xs"
          >
            Client Login
          </a>
        </nav>

        <button
          type="button"
          className="rounded-md border border-bone/15 p-2 text-bone md:hidden"
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
            className="border-t border-bone/10 bg-void/95 backdrop-blur-xl md:hidden"
          >
            <div className="flex flex-col gap-1 px-4 py-4">
              {links.map((l) => (
                <Link
                  key={l.href}
                  href={l.href}
                  onClick={() => setOpen(false)}
                  className="rounded-md px-3 py-3 text-sm font-medium text-bone hover:bg-bone/5"
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
