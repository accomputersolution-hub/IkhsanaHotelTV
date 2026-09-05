"use client";

import Image from "next/image";
import { Reveal } from "./Reveal";

const panels = [
  {
    src: "/images/pcncloud-hotel-room.jpg",
    label: "Guest experience",
    title: "Branded room TV that stays locked and on-brand",
  },
  {
    src: "/images/pcncloud-corporate-lobby.jpg",
    label: "Property presence",
    title: "Lobby-to-floor systems that feel enterprise-grade",
  },
  {
    src: "/images/pcncloud-admin-control.jpg",
    label: "Central command",
    title: "One cloud console for fleets, tickers, and policy",
  },
];

export function Showcase() {
  return (
    <section className="relative overflow-hidden pb-6 pt-4 md:pb-10">
      <div className="section-pad !py-12 md:!py-16">
        <Reveal>
          <p className="text-xs font-semibold uppercase tracking-[0.2em] text-neon-cyan">
            Built for hospitality
          </p>
          <h2 className="mt-3 max-w-3xl font-display text-3xl font-semibold tracking-tight text-white sm:text-4xl">
            Premium ops energy —{" "}
            <span className="text-gradient">from the room to the rack</span>
          </h2>
          <p className="mt-4 max-w-2xl text-slate-300">
            The environments we actually wire: guest rooms, lobbies, and the control plane that
            keeps every property consistent.
          </p>
        </Reveal>

        <div className="mt-10 grid gap-4 md:grid-cols-3">
          {panels.map((panel, i) => (
            <Reveal key={panel.title} delay={0.08 * i}>
              <figure className="group relative aspect-[4/5] overflow-hidden rounded-[1.75rem] border border-white/10 shadow-glow">
                <Image
                  src={panel.src}
                  alt={panel.title}
                  fill
                  className="object-cover transition duration-700 group-hover:scale-105"
                  sizes="(max-width: 768px) 100vw, 33vw"
                />
                <div className="absolute inset-0 bg-gradient-to-t from-ink-950 via-ink-950/35 to-transparent" />
                <figcaption className="absolute inset-x-0 bottom-0 p-5 sm:p-6">
                  <p className="text-[11px] font-semibold uppercase tracking-[0.18em] text-neon-cyan">
                    {panel.label}
                  </p>
                  <p className="mt-2 font-display text-lg font-semibold leading-snug text-white">
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
