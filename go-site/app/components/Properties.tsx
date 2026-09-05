"use client";

import Image from "next/image";
import { Reveal } from "./Reveal";
import { PropertyLogoMarquee } from "./PropertyLogoMarquee";

const properties = [
  {
    n: "01",
    name: "Regenta, SG’s GreenOtel",
    place: "Lonavala, Maharashtra",
    status: "live" as const,
    note: "PCN Cloud stack live on property — screens, network, and care.",
    logo: "/images/logo-regenta.png",
  },
  {
    n: "02",
    name: "Kumar Resort by Turtle",
    place: "Lonavala, Maharashtra",
    status: "live" as const,
    note: "Hospitality systems running with PCN Cloud as the accountable partner.",
    logo: "/images/logo-kumar-resort.png",
  },
  {
    n: "03",
    name: "Kaivalyadhama",
    place: "Lonavala, Maharashtra",
    status: "process" as const,
    note: "Rollout under process — scoping and deployment in motion.",
    logo: "/images/logo-kaivalyadhama.png",
  },
  {
    n: "04",
    name: "Larsen & Toubro Leadership Development Academy",
    place: "Lonavala, Maharashtra",
    status: "process" as const,
    note: "L&T LDA Lonavala — PCN Cloud rollout under process.",
    logo: "/images/logo-lt-lda.png",
  },
];

export function Properties() {
  return (
    <section id="properties" className="relative bg-void">
      <div className="section-pad !pb-10">
        <Reveal>
          <p className="eyebrow">On property</p>
          <h2 className="mt-4 max-w-3xl font-display text-4xl font-semibold tracking-tight text-bone sm:text-5xl md:text-6xl">
            Already trusted in Lonavala —{" "}
            <span className="text-brass-soft">more going live.</span>
          </h2>
          <p className="mt-5 max-w-xl text-bone/65">
            Two properties are live with PCN Cloud. Kaivalyadhama and Larsen &amp; Toubro
            Leadership Development Academy (LDA) are under process.
          </p>
        </Reveal>
      </div>

      <PropertyLogoMarquee />

      <div className="section-pad !pt-10">
        <div className="divide-y divide-bone/10 border-y border-bone/10">
          {properties.map((p, i) => (
            <Reveal key={p.n} delay={0.06 * i}>
              <article className="grid gap-5 py-8 sm:grid-cols-[4.5rem_8rem_1fr_auto] sm:items-center sm:gap-8">
                <span className="font-display text-sm tracking-[0.2em] text-brass">
                  {p.n}
                </span>
                <div className="relative flex h-16 w-32 items-center justify-center overflow-hidden rounded-md border border-bone/10 bg-black p-2">
                  <Image
                    src={p.logo}
                    alt={`${p.name} logo`}
                    width={140}
                    height={56}
                    className="max-h-12 w-auto object-contain"
                  />
                </div>
                <div>
                  <div className="flex flex-wrap items-center gap-3">
                    <h3 className="font-display text-2xl font-semibold text-bone sm:text-3xl">
                      {p.name}
                    </h3>
                    {p.status === "live" ? (
                      <span className="rounded-md border border-signal/40 bg-signal/10 px-2.5 py-1 text-[11px] font-semibold uppercase tracking-[0.18em] text-signal-bright">
                        Live
                      </span>
                    ) : (
                      <span className="rounded-md border border-brass/40 bg-brass/10 px-2.5 py-1 text-[11px] font-semibold uppercase tracking-[0.18em] text-brass-soft">
                        Under process
                      </span>
                    )}
                  </div>
                  <p className="mt-2 text-sm text-brass">{p.place}</p>
                  <p className="mt-2 max-w-2xl text-sm leading-relaxed text-bone/55">
                    {p.note}
                  </p>
                </div>
                <p className="text-xs font-semibold uppercase tracking-[0.2em] text-bone/35 sm:text-right">
                  {p.status === "live" ? "In service" : "In progress"}
                </p>
              </article>
            </Reveal>
          ))}
        </div>
      </div>
    </section>
  );
}
