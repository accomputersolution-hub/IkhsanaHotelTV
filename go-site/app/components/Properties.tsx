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
          <h2 className="mt-4 max-w-3xl text-balance font-display text-[2rem] font-semibold tracking-tight text-bone sm:text-5xl md:text-6xl">
            Already trusted in Lonavala —{" "}
            <span className="text-brass-soft">more going live.</span>
          </h2>
          <p className="mt-4 max-w-xl text-[0.95rem] text-bone/65 sm:mt-5 sm:text-base">
            Two properties are live with PCN Cloud. Kaivalyadhama and Larsen &amp; Toubro
            Leadership Development Academy (LDA) are under process.
          </p>
        </Reveal>
      </div>

      <PropertyLogoMarquee />

      <div className="section-pad !pt-8 sm:!pt-10">
        <div className="divide-y divide-bone/10 border-y border-bone/10">
          {properties.map((p, i) => (
            <Reveal key={p.n} delay={0.06 * i}>
              <article className="grid gap-4 py-7 sm:grid-cols-[4.5rem_8rem_1fr_auto] sm:items-center sm:gap-8 sm:py-8">
                <div className="flex items-center justify-between gap-3 sm:contents">
                  <span className="font-display text-sm tracking-[0.2em] text-brass">
                    {p.n}
                  </span>
                  <p className="text-[10px] font-semibold uppercase tracking-[0.2em] text-bone/35 sm:hidden">
                    {p.status === "live" ? "In service" : "In progress"}
                  </p>
                </div>
                <div className="relative flex h-14 w-28 items-center justify-center overflow-hidden rounded-md border border-bone/10 bg-black p-2 sm:h-16 sm:w-32">
                  <Image
                    src={p.logo}
                    alt={`${p.name} logo`}
                    width={140}
                    height={56}
                    className="max-h-10 w-auto object-contain sm:max-h-12"
                  />
                </div>
                <div>
                  <div className="flex flex-wrap items-center gap-2.5 sm:gap-3">
                    <h3 className="font-display text-xl font-semibold leading-snug text-bone sm:text-3xl">
                      {p.name}
                    </h3>
                    {p.status === "live" ? (
                      <span className="rounded-md border border-signal/40 bg-signal/10 px-2.5 py-1 text-[10px] font-semibold uppercase tracking-[0.18em] text-signal-bright sm:text-[11px]">
                        Live
                      </span>
                    ) : (
                      <span className="rounded-md border border-brass/40 bg-brass/10 px-2.5 py-1 text-[10px] font-semibold uppercase tracking-[0.18em] text-brass-soft sm:text-[11px]">
                        Under process
                      </span>
                    )}
                  </div>
                  <p className="mt-2 text-sm text-brass">{p.place}</p>
                  <p className="mt-2 max-w-2xl text-sm leading-relaxed text-bone/55">
                    {p.note}
                  </p>
                </div>
                <p className="hidden text-xs font-semibold uppercase tracking-[0.2em] text-bone/35 sm:block sm:text-right">
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
