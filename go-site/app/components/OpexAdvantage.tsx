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
    <section id="opex" className="relative overflow-hidden bg-bone text-void">
      <div className="section-pad relative">
        <div className="grid items-stretch gap-8 lg:grid-cols-12 lg:gap-10">
          <Reveal className="relative min-h-[22rem] overflow-hidden sm:min-h-[28rem] lg:col-span-5">
            <Image
              src="/images/pcncloud-admin-control.jpg"
              alt="Ops console for rental infrastructure"
              fill
              className="object-cover"
              sizes="(max-width: 1024px) 100vw, 40vw"
            />
            <div className="absolute inset-0 bg-gradient-to-t from-void via-void/50 to-transparent" />
            <div className="relative flex h-full min-h-[22rem] flex-col justify-end p-5 sm:min-h-[28rem] sm:p-9">
              <p className="text-[10px] font-semibold uppercase tracking-[0.28em] text-brass sm:text-[11px]">
                Pricing posture
              </p>
              <h2 className="mt-3 text-balance font-display text-[1.75rem] font-semibold text-bone sm:mt-4 sm:text-4xl">
                Rent the stack.
                <br />
                Own the outcome.
              </h2>
            </div>
          </Reveal>

          <div className="flex flex-col justify-between gap-8 lg:col-span-7">
            <Reveal>
              <p className="eyebrow-dark">OpEx over CapEx</p>
              <h3 className="mt-4 max-w-xl text-balance font-display text-[1.75rem] font-semibold tracking-tight sm:text-4xl">
                Reliability as a monthly operating decision
              </h3>
              <p className="mt-4 max-w-xl text-[0.95rem] text-void/65 sm:text-base">
                Enterprise Wi‑Fi, security, and care without locking the balance sheet into
                aging hardware. Predictable. Replaceable. Accountable.
              </p>
              <a href="#contact" className="btn-dark mt-7 sm:mt-8 sm:!w-fit">
                Get a rental sketch
              </a>
            </Reveal>

            <div className="grid gap-5 sm:grid-cols-2 sm:gap-6">
              {benefits.map((b, i) => {
                const Icon = b.icon;
                return (
                  <Reveal key={b.title} delay={0.06 * i}>
                    <div className="border-t border-void/15 pt-5">
                      <Icon className="mb-3 h-5 w-5 text-signal-dim" aria-hidden />
                      <h4 className="font-display text-lg font-semibold sm:text-xl">{b.title}</h4>
                      <p className="mt-2 text-sm leading-relaxed text-void/60">{b.text}</p>
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
