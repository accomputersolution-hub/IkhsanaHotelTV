"use client";

import Image from "next/image";
import { Reveal } from "./Reveal";

const panels = [
  {
    src: "/images/pcncloud-hotel-room.jpg",
    label: "01 — Guest room",
    title: "Locked, branded TV experiences guests actually notice.",
    className: "md:col-span-2 md:row-span-2 min-h-[22rem] md:min-h-[34rem]",
  },
  {
    src: "/images/pcncloud-corporate-lobby.jpg",
    label: "02 — Lobby",
    title: "Public spaces that feel intentional — not improvised.",
    className: "min-h-[16rem]",
  },
  {
    src: "/images/pcncloud-admin-control.jpg",
    label: "03 — Control",
    title: "Central ops for fleets, tickers, and policy.",
    className: "min-h-[16rem]",
  },
];

export function Showcase() {
  return (
    <section id="showcase" className="relative overflow-hidden">
      <div className="section-pad">
        <Reveal>
          <div className="flex flex-col gap-6 md:flex-row md:items-end md:justify-between">
            <div className="max-w-2xl">
              <p className="eyebrow">The atmosphere we build</p>
              <h2 className="mt-4 font-display text-3xl font-semibold tracking-tight text-white sm:text-5xl">
                Not another IT brochure —
                <span className="text-gradient"> a property that feels finished.</span>
              </h2>
            </div>
            <p className="max-w-sm text-sm leading-relaxed text-slate-400 md:pb-1">
              Room screens, lobby presence, and the ops console that keeps every property
              singing the same tune.
            </p>
          </div>
        </Reveal>

        <div className="mt-12 grid gap-4 md:grid-cols-2">
          {panels.map((panel, i) => (
            <Reveal key={panel.label} delay={0.08 * i} className={panel.className}>
              <figure className="group relative h-full overflow-hidden rounded-[1.75rem] border border-white/10 shadow-lift">
                <Image
                  src={panel.src}
                  alt={panel.title}
                  fill
                  className="object-cover transition duration-[1.1s] group-hover:scale-[1.04]"
                  sizes="(max-width: 768px) 100vw, 50vw"
                />
                <div className="absolute inset-0 bg-gradient-to-t from-ink-950 via-ink-950/25 to-transparent" />
                <div className="absolute inset-x-0 top-0 h-px glow-line opacity-0 transition group-hover:opacity-100" />
                <figcaption className="absolute inset-x-0 bottom-0 p-6 sm:p-8">
                  <p className="text-[11px] font-semibold uppercase tracking-[0.24em] text-champagne">
                    {panel.label}
                  </p>
                  <p className="mt-3 max-w-md font-display text-2xl font-semibold leading-snug text-white sm:text-3xl">
                    {panel.title}
                  </p>
                </figcaption>
              </figure>
            </Reveal>
          ))}
        </div>
      </div>
    </section>
  );
}
