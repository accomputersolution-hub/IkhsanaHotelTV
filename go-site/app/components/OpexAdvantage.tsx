"use client";

import Image from "next/image";
import { Reveal } from "./Reveal";
import { BadgeIndianRupee, RefreshCcw, ShieldCheck, Wrench } from "lucide-react";

const benefits = [
  {
    icon: BadgeIndianRupee,
    title: "Zero heavy CapEx",
    text: "Shift networking and systems to predictable monthly OpEx — keep capital for guest experience.",
  },
  {
    icon: RefreshCcw,
    title: "Replacements included",
    text: "Failed APs, gateways, or covered endpoints get swapped under the rental program.",
  },
  {
    icon: Wrench,
    title: "Always maintained",
    text: "Monitoring, config hygiene, and proactive fixes — your team stops chasing vendors.",
  },
  {
    icon: ShieldCheck,
    title: "Never obsolete",
    text: "Stay on supported hardware without forcing another purchase cycle every few years.",
  },
];

export function OpexAdvantage() {
  return (
    <section id="opex" className="relative overflow-hidden">
      <div className="pointer-events-none absolute inset-0 bg-gradient-to-b from-transparent via-neon-violet/5 to-transparent" />
      <div className="section-pad relative">
        <div className="grid items-stretch gap-6 lg:grid-cols-12">
          <Reveal className="relative min-h-[26rem] overflow-hidden rounded-[2rem] border border-white/10 lg:col-span-5">
            <Image
              src="/images/pcncloud-admin-control.jpg"
              alt="Ops console for rental infrastructure"
              fill
              className="object-cover"
              sizes="(max-width: 1024px) 100vw, 40vw"
            />
            <div className="absolute inset-0 bg-gradient-to-t from-ink-950 via-ink-950/50 to-ink-950/20" />
            <div className="relative flex h-full min-h-[26rem] flex-col justify-end p-7 sm:p-8">
              <p className="eyebrow">Pricing posture</p>
              <h2 className="mt-4 font-display text-3xl font-semibold text-white sm:text-4xl">
                Rent the stack.
                <br />
                <span className="text-champagne">Own the outcome.</span>
              </h2>
              <p className="mt-4 max-w-sm text-sm leading-relaxed text-slate-300">
                Month-to-month infrastructure for hotels that refuse to burn cash on gear
                that depreciates the day it ships.
              </p>
            </div>
          </Reveal>

          <div className="flex flex-col justify-between gap-5 lg:col-span-7">
            <Reveal>
              <p className="eyebrow">OpEx over CapEx</p>
              <h3 className="mt-4 max-w-xl font-display text-3xl font-semibold tracking-tight text-white sm:text-4xl">
                Reliability as a{" "}
                <span className="text-gradient">monthly operating decision</span>
              </h3>
              <p className="mt-4 max-w-xl text-slate-300">
                Enterprise Wi‑Fi, security, and care without locking the balance sheet into
                aging hardware. Predictable. Replaceable. Accountable.
              </p>
              <a href="#contact" className="btn-primary mt-8 w-fit">
                Get a rental sketch
              </a>
            </Reveal>

            <div className="grid gap-4 sm:grid-cols-2">
              {benefits.map((b, i) => {
                const Icon = b.icon;
                return (
                  <Reveal key={b.title} delay={0.06 * i}>
                    <div className="glass h-full rounded-3xl p-5 transition hover:border-neon-cyan/30">
                      <Icon className="mb-3 h-5 w-5 text-champagne" aria-hidden />
                      <h4 className="font-display text-lg font-semibold text-white">
                        {b.title}
                      </h4>
                      <p className="mt-2 text-sm leading-relaxed text-slate-400">{b.text}</p>
                    </div>
                  </Reveal>
                );
              })}
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
