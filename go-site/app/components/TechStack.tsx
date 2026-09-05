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
    <section id="tech" className="section-pad relative">
      <div className="grid items-center gap-8 lg:grid-cols-12">
        <div className="lg:col-span-5">
          <Reveal>
            <p className="eyebrow">Platform & security</p>
            <h2 className="mt-4 font-display text-3xl font-semibold tracking-tight text-white sm:text-5xl">
              Infrastructure energy —
              <span className="text-gradient"> not gadget energy.</span>
            </h2>
            <p className="mt-4 text-slate-300">
              Owners pick PCN Cloud when uptime, audit trails, and a single throat to choke
              beat a pile of half-integrated vendors.
            </p>
          </Reveal>

          <div className="mt-8 space-y-3">
            {pillars.map((p, i) => {
              const Icon = p.icon;
              return (
                <Reveal key={p.title} delay={0.08 * i}>
                  <div className="glass flex gap-4 rounded-2xl p-4 transition hover:border-neon-cyan/30">
                    <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-neon-cyan/10 text-neon-cyan">
                      <Icon className="h-5 w-5" aria-hidden />
                    </div>
                    <div>
                      <h3 className="font-semibold text-white">{p.title}</h3>
                      <p className="mt-1 text-sm text-slate-400">{p.text}</p>
                    </div>
                  </div>
                </Reveal>
              );
            })}
          </div>
        </div>

        <Reveal className="relative min-h-[28rem] overflow-hidden rounded-[2rem] border border-white/10 shadow-lift lg:col-span-7">
          <Image
            src="/images/pcncloud-corporate-lobby.jpg"
            alt="Lobby systems under PCN Cloud"
            fill
            className="object-cover"
            sizes="(max-width: 1024px) 100vw, 58vw"
          />
          <div className="absolute inset-0 bg-gradient-to-tr from-ink-950 via-ink-950/40 to-neon-violet/20" />
          <div className="absolute bottom-6 left-6 right-6">
            <div className="glass-strong rounded-2xl p-5 sm:p-6">
              <p className="text-[11px] font-semibold uppercase tracking-[0.22em] text-champagne">
                Always-on architecture
              </p>
              <p className="mt-2 font-display text-2xl font-semibold text-white">
                Designed for sold-out weekends — not demo day.
              </p>
            </div>
          </div>
        </Reveal>
      </div>
    </section>
  );
}
