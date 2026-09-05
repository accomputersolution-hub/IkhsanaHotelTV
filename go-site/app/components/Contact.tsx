"use client";

import Image from "next/image";
import { Reveal } from "./Reveal";
import { Mail, MapPin, Phone } from "lucide-react";

export function Contact() {
  return (
    <section id="contact" className="section-pad">
      <div className="relative overflow-hidden rounded-[2rem] border border-white/10 shadow-lift">
        <div className="absolute inset-0">
          <Image
            src="/images/pcncloud-hotel-room.jpg"
            alt=""
            fill
            className="object-cover opacity-40"
            sizes="100vw"
          />
          <div className="absolute inset-0 bg-gradient-to-r from-ink-950 via-ink-950/90 to-ink-950/70" />
        </div>
        <div className="pointer-events-none absolute -right-16 top-0 h-72 w-72 rounded-full bg-neon-cyan/15 blur-[100px]" />
        <div className="pointer-events-none absolute -left-10 bottom-0 h-64 w-64 rounded-full bg-neon-violet/15 blur-[90px]" />

        <div className="relative grid gap-10 p-8 sm:p-12 lg:grid-cols-[1.2fr_0.8fr] lg:p-14">
          <Reveal>
            <p className="eyebrow">Let&apos;s scope it</p>
            <h2 className="mt-4 font-display text-3xl font-semibold tracking-tight text-white sm:text-5xl">
              Ready when your property is.
              <span className="text-gradient"> We&apos;ll bring the stack.</span>
            </h2>
            <p className="mt-5 max-w-xl text-slate-300">
              Tell us rooms, current Wi‑Fi/CCTV pain, or your TV rollout. One conversation —
              a single-vendor plan for kiosk, network rental, security, and IT.
            </p>
            <div className="mt-8 flex flex-wrap gap-3">
              <a
                href="mailto:hello@pcncloud.in?subject=PCN%20Cloud%20consultation"
                className="btn-primary"
              >
                Email hello@pcncloud.in
              </a>
              <a
                href="https://admin.pcncloud.in"
                target="_blank"
                rel="noopener noreferrer"
                className="btn-secondary"
              >
                Client Login
              </a>
            </div>
          </Reveal>

          <Reveal delay={0.1}>
            <div className="glass-strong space-y-5 rounded-[1.5rem] p-6">
              {[
                {
                  icon: Mail,
                  title: "Sales & support",
                  body: (
                    <a href="mailto:hello@pcncloud.in" className="hover:text-neon-cyan">
                      hello@pcncloud.in
                    </a>
                  ),
                },
                {
                  icon: Phone,
                  title: "Consultations",
                  body: "Remote or on-property walkthroughs",
                },
                {
                  icon: MapPin,
                  title: "Coverage",
                  body: "Hotels, resorts & campuses across India",
                },
              ].map((row) => {
                const Icon = row.icon;
                return (
                  <div key={row.title} className="flex items-start gap-3 text-sm text-slate-300">
                    <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-neon-cyan/10 text-neon-cyan">
                      <Icon className="h-4 w-4" aria-hidden />
                    </div>
                    <div>
                      <p className="font-semibold text-white">{row.title}</p>
                      <div className="mt-0.5">{row.body}</div>
                    </div>
                  </div>
                );
              })}
            </div>
          </Reveal>
        </div>
      </div>
    </section>
  );
}
