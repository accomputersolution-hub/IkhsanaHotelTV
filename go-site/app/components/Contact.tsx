"use client";

import Image from "next/image";
import { Reveal } from "./Reveal";
import { Logo } from "./Logo";
import { Mail, MapPin, Phone } from "lucide-react";

export function Contact() {
  return (
    <section id="contact" className="relative">
      <div className="relative min-h-[70svh] overflow-hidden">
        <Image
          src="/images/pcncloud-hotel-room.jpg"
          alt=""
          fill
          className="object-cover"
          sizes="100vw"
        />
        <div className="absolute inset-0 bg-void/80" />
        <div className="absolute inset-0 film-grain" aria-hidden />

        <div className="relative mx-auto grid max-w-6xl gap-12 px-4 py-24 sm:px-6 md:py-32 lg:grid-cols-[1.2fr_0.8fr] lg:px-8">
          <Reveal>
            <p className="eyebrow">Let&apos;s scope it</p>
            <h2 className="mt-4 font-display text-4xl font-semibold tracking-tight text-bone sm:text-5xl md:text-6xl">
              Ready when your property is.
            </h2>
            <p className="mt-5 max-w-xl text-lg text-bone/65">
              Tell us rooms, current Wi‑Fi/CCTV pain, or your TV rollout. One conversation —
              a single-vendor plan for kiosk, network rental, security, and IT.
            </p>
            <div className="mt-9 flex flex-wrap gap-3">
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
            <div className="space-y-8 border-t border-bone/15 pt-8 lg:border-l lg:border-t-0 lg:pl-10 lg:pt-0">
              <Logo size="lg" className="mb-2" />
              {[
                {
                  icon: Mail,
                  title: "Sales & support",
                  body: (
                    <a href="mailto:hello@pcncloud.in" className="hover:text-signal-bright">
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
                  <div key={row.title} className="flex items-start gap-4 text-sm text-bone/70">
                    <Icon className="mt-0.5 h-5 w-5 shrink-0 text-brass" aria-hidden />
                    <div>
                      <p className="font-display text-lg font-semibold text-bone">{row.title}</p>
                      <div className="mt-1">{row.body}</div>
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
