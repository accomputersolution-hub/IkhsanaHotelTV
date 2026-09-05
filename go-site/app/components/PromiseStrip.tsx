"use client";

import { Reveal } from "./Reveal";

const promises = [
  {
    n: "01",
    title: "One throat to choke",
    text: "TV, Wi‑Fi, CCTV, phones, locks, IT — one partner when something breaks at 11pm.",
  },
  {
    n: "02",
    title: "Hospitality-native",
    text: "Designed around rooms, shifts, banquets, and guest journeys — not generic office IT.",
  },
  {
    n: "03",
    title: "OpEx-friendly",
    text: "Rent what should be rented. Buy only what must be owned. Keep CapEx for the guest.",
  },
];

export function PromiseStrip() {
  return (
    <section className="relative border-y border-white/5 bg-ink-900/40">
      <div className="section-pad !py-14 md:!py-16">
        <Reveal>
          <p className="eyebrow">Why owners switch</p>
          <h2 className="mt-4 max-w-2xl font-display text-3xl font-semibold text-white sm:text-4xl">
            Less vendor theatre.{" "}
            <span className="text-champagne">More sold-out calm.</span>
          </h2>
        </Reveal>
        <div className="mt-10 grid gap-6 md:grid-cols-3">
          {promises.map((p, i) => (
            <Reveal key={p.n} delay={0.08 * i}>
              <div className="relative border-l border-neon-cyan/30 pl-5">
                <p className="font-display text-sm font-semibold tracking-[0.2em] text-champagne">
                  {p.n}
                </p>
                <h3 className="mt-3 font-display text-xl font-semibold text-white">
                  {p.title}
                </h3>
                <p className="mt-2 text-sm leading-relaxed text-slate-400">{p.text}</p>
              </div>
            </Reveal>
          ))}
        </div>
      </div>
    </section>
  );
}
