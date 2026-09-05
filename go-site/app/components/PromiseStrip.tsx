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
    <section className="relative bg-bone text-void">
      <div className="section-pad !py-16 md:!py-20">
        <Reveal>
          <p className="eyebrow-dark">Why owners switch</p>
          <h2 className="mt-4 max-w-3xl font-display text-3xl font-semibold tracking-tight sm:text-5xl">
            Less vendor theatre.{" "}
            <span className="text-signal-dim">More sold-out calm.</span>
          </h2>
        </Reveal>
        <div className="mt-12 grid gap-10 md:grid-cols-3 md:gap-8">
          {promises.map((p, i) => (
            <Reveal key={p.n} delay={0.08 * i}>
              <div className="border-t border-void/15 pt-6">
                <p className="font-display text-sm font-semibold tracking-[0.2em] text-signal-dim">
                  {p.n}
                </p>
                <h3 className="mt-4 font-display text-2xl font-semibold">{p.title}</h3>
                <p className="mt-3 text-sm leading-relaxed text-void/65">{p.text}</p>
              </div>
            </Reveal>
          ))}
        </div>
      </div>
    </section>
  );
}
