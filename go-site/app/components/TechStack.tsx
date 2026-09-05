"use client";

import Image from "next/image";
import { Reveal } from "./Reveal";
import { Lock, Router, Smartphone } from "lucide-react";

const pillars = [
  {
    icon: Lock,
    title: "WireGuard VPN paths",
    text: "Hardened tunnels for Live TV and secure device routes — split so only protected traffic rides the VPN.",
  },
  {
    icon: Smartphone,
    title: "Zero-touch provisioning",
    text: "TVs and endpoints enroll into policy automatically — fewer truck rolls, cleaner fleets.",
  },
  {
    icon: Router,
    title: "Guest / staff segmentation",
    text: "Captive portals, VLANs, and bandwidth rules that match how hotels actually operate.",
  },
];

export function TechStack() {
  return (
    <section id="tech" className="relative bg-void">
      <div className="section-pad">
        <div className="grid items-center gap-10 lg:grid-cols-12">
          <div className="lg:col-span-5">
            <Reveal>
              <p className="eyebrow">Platform & security</p>
              <h2 className="mt-4 text-balance font-display text-[2rem] font-semibold tracking-tight text-bone sm:text-5xl">
                Infrastructure energy —
                <span className="text-brass-soft"> not gadget energy.</span>
              </h2>
              <p className="mt-4 text-[0.95rem] text-bone/65 sm:mt-5 sm:text-base">
                Owners pick PCN Cloud when uptime, audit trails, and a single throat to choke
                beat a pile of half-integrated vendors.
              </p>
            </Reveal>

            <div className="mt-8 space-y-5 sm:mt-10 sm:space-y-6">
              {pillars.map((p, i) => {
                const Icon = p.icon;
                return (
                  <Reveal key={p.title} delay={0.08 * i}>
                    <div className="flex gap-3.5 border-l border-signal/40 pl-4 sm:gap-4 sm:pl-5">
                      <div className="shrink-0 text-signal-bright">
                        <Icon className="h-5 w-5" aria-hidden />
                      </div>
                      <div>
                        <h3 className="font-display text-base font-semibold text-bone sm:text-lg">
                          {p.title}
                        </h3>
                        <p className="mt-1 text-sm leading-relaxed text-bone/55">{p.text}</p>
                      </div>
                    </div>
                  </Reveal>
                );
              })}
            </div>
          </div>

          <Reveal className="relative min-h-[22rem] overflow-hidden sm:min-h-[28rem] lg:col-span-7">
            <Image
              src="/images/pcncloud-corporate-lobby.jpg"
              alt="Lobby systems under PCN Cloud"
              fill
              className="object-cover"
              sizes="(max-width: 1024px) 100vw, 58vw"
            />
            <div className="absolute inset-0 bg-gradient-to-tr from-void via-void/35 to-transparent" />
            <div className="absolute inset-0 film-grain" aria-hidden />
            <div className="absolute bottom-0 left-0 right-0 p-5 sm:p-9">
              <p className="text-[10px] font-semibold uppercase tracking-[0.28em] text-brass sm:text-[11px]">
                Always-on architecture
              </p>
              <p className="mt-2 max-w-md text-balance font-display text-xl font-semibold text-bone sm:mt-3 sm:text-3xl">
                Designed for sold-out weekends — not demo day.
              </p>
            </div>
          </Reveal>
        </div>
      </div>
    </section>
  );
}
