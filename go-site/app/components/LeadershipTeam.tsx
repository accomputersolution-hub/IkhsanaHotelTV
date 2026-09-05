"use client";

import Image from "next/image";
import { Reveal } from "./Reveal";

const founders = [
  {
    name: "Linga Bhandari",
    role: "Co-Founder / Managing Director (MD)",
    image: "/md-photo.jpg",
    alt: "Linga Bhandari, Co-Founder and Managing Director of PCN Cloud",
  },
  {
    name: "Mohammed Chaudhary",
    role: "Co-Founder / Chief Technology Officer (CTO)",
    image: "/cto-photo.png",
    alt: "Mohammed Chaudhary, Co-Founder and Chief Technology Officer of PCN Cloud",
  },
];

export function LeadershipTeam() {
  return (
    <section id="leadership" className="relative overflow-hidden bg-void">
      <div
        className="pointer-events-none absolute inset-0"
        style={{
          background:
            "radial-gradient(ellipse 60% 45% at 50% 0%, rgba(26,155,142,0.12), transparent 55%), radial-gradient(ellipse 40% 35% at 100% 80%, rgba(198,163,107,0.08), transparent 50%)",
        }}
      />
      <div className="pointer-events-none absolute inset-0 film-grain" aria-hidden />

      <div className="section-pad relative">
        <Reveal>
          <div className="mx-auto max-w-3xl text-center">
            <p className="eyebrow">People behind the stack</p>
            <h2 className="mt-4 font-display text-4xl font-semibold tracking-tight text-bone sm:text-5xl md:text-6xl">
              <span className="text-gradient">LEADERSHIP TEAM</span>
            </h2>
            <p className="mx-auto mt-8 max-w-2xl text-base leading-relaxed text-bone/70 sm:text-lg">
              PCN Cloud was founded with a unified vision: to eliminate the IT and
              networking headaches of the hospitality industry. By combining strong
              strategic backing with deep, hands-on expertise in network architecture and
              IT infrastructure, we built a company that acts as your single, accountable
              tech partner. From the fiber connection coming into your property to the
              smart TV kiosks and RFID locks, our leadership ensures seamless deployment
              and 24/7 maintenance—so you can focus entirely on your guests.
            </p>
          </div>
        </Reveal>

        <div className="mx-auto mt-14 grid max-w-5xl gap-6 md:grid-cols-2 md:gap-8">
          {founders.map((person, i) => (
            <Reveal key={person.name} delay={0.08 * i}>
              <article className="group relative overflow-hidden rounded-2xl border border-bone/10 bg-white/[0.04] p-4 shadow-deep backdrop-blur-xl transition duration-500 hover:-translate-y-1 hover:scale-[1.02] hover:border-signal/35 hover:bg-white/[0.06] hover:shadow-[0_0_40px_rgba(46,196,182,0.12)] sm:p-5">
                <div className="pointer-events-none absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-signal/50 to-transparent opacity-60 transition group-hover:opacity-100" />

                <div className="relative aspect-[4/5] overflow-hidden rounded-xl border border-bone/10 bg-void-mute">
                  <Image
                    src={person.image}
                    alt={person.alt}
                    fill
                    className="object-cover object-top transition duration-700 group-hover:scale-[1.03]"
                    sizes="(max-width: 768px) 100vw, 420px"
                    priority={i === 0}
                  />
                  <div className="absolute inset-0 bg-gradient-to-t from-void via-void/20 to-transparent" />
                </div>

                <div className="relative px-1 pb-1 pt-5 text-center sm:pt-6">
                  <h3 className="font-display text-2xl font-semibold tracking-tight text-bone sm:text-3xl">
                    {person.name}
                  </h3>
                  <p className="mt-2 text-xs font-semibold uppercase tracking-[0.2em] text-brass-soft sm:text-[13px]">
                    {person.role}
                  </p>
                </div>
              </article>
            </Reveal>
          ))}
        </div>
      </div>
    </section>
  );
}
