"use client";

import Image from "next/image";
import { useReducedMotion } from "framer-motion";

const logos = [
  {
    src: "/images/logo-regenta.png",
    alt: "Regenta / Royal Orchid Hotels",
    href: "#properties",
    status: "Live",
  },
  {
    src: "/images/logo-kumar-resort.png",
    alt: "Kumar Resort by Turtle",
    href: "#properties",
    status: "Live",
  },
  {
    src: "/images/logo-kaivalyadhama.png",
    alt: "Kaivalyadhama",
    href: "#properties",
    status: "Under process",
  },
];

export function PropertyLogoMarquee() {
  const reduce = useReducedMotion();
  // Repeat so the strip feels continuous even with only 3 marks.
  const row = [...logos, ...logos, ...logos, ...logos];

  return (
    <div className="relative overflow-hidden border-y border-bone/10 bg-void-soft py-6">
      <div className="pointer-events-none absolute inset-y-0 left-0 z-10 w-16 bg-gradient-to-r from-void-soft to-transparent sm:w-24" />
      <div className="pointer-events-none absolute inset-y-0 right-0 z-10 w-16 bg-gradient-to-l from-void-soft to-transparent sm:w-24" />

      <div
        className={`flex w-max items-center gap-5 sm:gap-8 ${
          reduce ? "" : "animate-marquee"
        }`}
        style={reduce ? undefined : { animationDuration: "28s" }}
      >
        {row.map((logo, i) => (
          <a
            key={`${logo.alt}-${i}`}
            href={logo.href}
            className="group inline-flex shrink-0 items-center gap-3 rounded-md border border-bone/10 bg-bone px-5 py-3 transition hover:border-brass/40"
            aria-label={`${logo.alt} — ${logo.status}`}
          >
            <span className="relative h-10 w-[9.5rem] sm:h-11 sm:w-44">
              <Image
                src={logo.src}
                alt={logo.alt}
                fill
                className="object-contain object-center"
                sizes="180px"
              />
            </span>
            <span
              className={`hidden text-[10px] font-semibold uppercase tracking-[0.16em] sm:inline ${
                logo.status === "Live" ? "text-signal-dim" : "text-brass"
              }`}
            >
              {logo.status}
            </span>
          </a>
        ))}
      </div>
    </div>
  );
}
