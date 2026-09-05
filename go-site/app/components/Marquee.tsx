"use client";

import { useReducedMotion } from "framer-motion";

const items = [
  "Smart TV Kiosk",
  "Enterprise Wi‑Fi Rental",
  "CCTV & Surveillance",
  "Hotel Communications",
  "Smart Access",
  "24/7 IT Care",
  "Live at Regenta Lonavala",
  "Live at Kumar Resort by Turtle",
  "Kaivalyadhama — under process",
  "L&T LDA Lonavala — under process",
  "Single throat to choke",
];

export function Marquee() {
  const reduce = useReducedMotion();
  const row = [...items, ...items];

  return (
    <div className="relative overflow-hidden border-y border-bone/10 bg-void-soft py-3.5 sm:py-4">
      <div
        className={`flex w-max gap-8 whitespace-nowrap sm:gap-10 ${
          reduce ? "" : "animate-marquee"
        }`}
      >
        {row.map((item, i) => (
          <span
            key={`${item}-${i}`}
            className="inline-flex items-center gap-8 font-display text-base tracking-wide text-bone/55 sm:gap-10 sm:text-xl"
          >
            {item}
            <span className="h-1.5 w-1.5 rounded-full bg-signal" aria-hidden />
          </span>
        ))}
      </div>
    </div>
  );
}
