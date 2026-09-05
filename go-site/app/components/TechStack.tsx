"use client";

import Image from "next/image";
import { Reveal } from "./Reveal";
import { Lock, Router, Smartphone } from "lucide-react";

const pillars = [
  {
    icon: Lock,
    title: "WireGuard VPN security",
    text: "Hardened tunnels for corporate Live TV and secure device paths — split so only what must be protected rides the VPN.",
  },
  {
    icon: Smartphone,
    title: "Automated device provisioning",
    text: "Zero-touch and scripted onboarding for TV kiosks and network endpoints — rooms go live faster, with fewer truck rolls.",
  },
  {
    icon: Router,
    title: "Seamless routing & control",
    text: "Clean guest/staff segmentation, captive portals, and central policy so bandwidth and access stay under your rules.",
  },
];

export function TechStack() {
  return (
    <section id="tech" className="section-pad">
      <div className="grid items-center gap-10 lg:grid-cols-2">
        <Reveal className="relative order-2 overflow-hidden rounded-3xl border border-white/10 shadow-glow lg:order-1">
          <Image
            src="/images/pcncloud-corporate-lobby.jpg"
            alt="Enterprise lobby display representing PCN Cloud secure infrastructure"
            width={1200}
            height={800}
            className="h-full w-full object-cover"
          />
          <div className="absolute inset-0 bg-gradient-to-tr from-ink-950/80 via-transparent to-neon-violet/20" />
        </Reveal>

        <div className="order-1 lg:order-2">
          <Reveal>
            <p className="text-xs font-semibold uppercase tracking-[0.2em] text-neon-cyan">
              Tech stack &amp; security
            </p>
            <h2 className="mt-3 font-display text-3xl font-semibold tracking-tight text-white sm:text-4xl">
              Built like infrastructure —{" "}
              <span className="text-gradient">not a one-off install</span>
            </h2>
            <p className="mt-4 text-slate-300">
              Owners choose PCN Cloud when uptime, auditability, and a single throat to choke
              matter more than juggling five vendors.
            </p>
          </Reveal>

          <div className="mt-8 space-y-4">
            {pillars.map((p, i) => {
              const Icon = p.icon;
              return (
                <Reveal key={p.title} delay={0.1 * i}>
                  <div className="glass flex gap-4 rounded-2xl p-4">
                    <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-neon-blue/15 text-neon-cyan">
                      <Icon className="h-5 w-5" aria-hidden />
                    </div>
                    <div>
                      <h3 className="font-semibold text-white">{p.title}</h3>
                      <p className="mt-1 text-sm text-slate-300">{p.text}</p>
                    </div>
                  </div>
                </Reveal>
              );
            })}
          </div>
        </div>
      </div>
    </section>
  );
}
