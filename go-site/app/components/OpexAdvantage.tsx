"use client";

import { Reveal } from "./Reveal";
import { BadgeIndianRupee, RefreshCcw, ShieldCheck, Wrench } from "lucide-react";

const benefits = [
  {
    icon: BadgeIndianRupee,
    title: "Zero heavy upfront cost",
    text: "Move from CapEx spikes to predictable monthly OpEx — preserve capital for guest experience.",
  },
  {
    icon: RefreshCcw,
    title: "Free hardware replacements",
    text: "Failed APs, gateways, or covered endpoints get replaced under the rental program — not your balance sheet.",
  },
  {
    icon: Wrench,
    title: "Continuous maintenance",
    text: "Configuration, monitoring, and proactive fixes are included so your team is not chasing vendors.",
  },
  {
    icon: ShieldCheck,
    title: "Always-current stack",
    text: "Stay on supported hardware and firmware without another purchase cycle every few years.",
  },
];

export function OpexAdvantage() {
  return (
    <section id="opex" className="relative overflow-hidden">
      <div className="pointer-events-none absolute inset-0 bg-gradient-to-r from-neon-violet/10 via-transparent to-neon-blue/10" />
      <div className="section-pad relative">
        <div className="grid items-center gap-10 lg:grid-cols-[1.1fr_0.9fr]">
          <Reveal>
            <p className="text-xs font-semibold uppercase tracking-[0.2em] text-neon-violet">
              Pricing model
            </p>
            <h2 className="mt-3 font-display text-3xl font-semibold tracking-tight text-white sm:text-4xl">
              OpEx over CapEx —{" "}
              <span className="text-gradient">rent reliability monthly</span>
            </h2>
            <p className="mt-4 max-w-xl text-slate-300">
              Network hardware and IT systems on a month-to-month rental basis. You get
              enterprise-grade Wi‑Fi, security, and maintenance without locking cash into
              depreciating assets.
            </p>
            <a href="#contact" className="btn-primary mt-8">
              Talk rental options
            </a>
          </Reveal>

          <div className="grid gap-4 sm:grid-cols-2">
            {benefits.map((b, i) => {
              const Icon = b.icon;
              return (
                <Reveal key={b.title} delay={0.08 * i}>
                  <div className="glass h-full rounded-3xl p-5 transition hover:border-neon-violet/40 hover:shadow-glow-violet">
                    <Icon className="mb-3 h-6 w-6 text-neon-violet" aria-hidden />
                    <h3 className="font-display text-lg font-semibold text-white">
                      {b.title}
                    </h3>
                    <p className="mt-2 text-sm text-slate-300">{b.text}</p>
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
