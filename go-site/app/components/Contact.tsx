"use client";

import Image from "next/image";
import { Reveal } from "./Reveal";
import { Logo } from "./Logo";
import { WhatsAppIcon } from "./WhatsAppFloat";
import { Mail, MapPin, Phone } from "lucide-react";
import {
  COVERAGE_LINE,
  EMAIL_SALES,
  LOCATION_LINE,
  PHONE_DISPLAY,
  PHONE_TEL,
  WHATSAPP_URL,
} from "@/lib/contact";

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

        <div className="relative mx-auto grid max-w-6xl gap-10 px-4 py-16 sm:gap-12 sm:px-6 sm:py-24 md:py-32 lg:grid-cols-[1.2fr_0.8fr] lg:px-8">
          <Reveal>
            <p className="eyebrow">Let&apos;s scope it</p>
            <h2 className="mt-4 text-balance font-display text-[2rem] font-semibold tracking-tight text-bone sm:text-5xl md:text-6xl">
              Ready when your property is.
            </h2>
            <p className="mt-4 max-w-xl text-base text-bone/65 sm:mt-5 sm:text-lg">
              Tell us rooms, current Wi‑Fi/CCTV pain, or your TV rollout. One conversation —
              a single-vendor plan for kiosk, network rental, security, and IT.
            </p>
            <div className="mt-8 flex w-full max-w-md flex-col gap-3 sm:mt-9 sm:max-w-none sm:flex-row sm:flex-wrap">
              <a
                href={WHATSAPP_URL}
                target="_blank"
                rel="noopener noreferrer"
                className="btn-primary"
              >
                <WhatsAppIcon className="h-4 w-4" />
                WhatsApp us
              </a>
              <a href={`tel:${PHONE_TEL}`} className="btn-secondary">
                Call {PHONE_DISPLAY}
              </a>
            </div>
          </Reveal>

          <Reveal delay={0.1}>
            <div className="space-y-8 border-t border-bone/15 pt-8 lg:border-l lg:border-t-0 lg:pl-10 lg:pt-0">
              <Logo size="lg" className="mb-2" />
              {[
                {
                  icon: Phone,
                  title: "Phone & WhatsApp",
                  body: (
                    <div className="space-y-1">
                      <a href={`tel:${PHONE_TEL}`} className="block hover:text-signal-bright">
                        {PHONE_DISPLAY}
                      </a>
                      <a
                        href={WHATSAPP_URL}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="block text-signal-bright hover:underline"
                      >
                        Chat on WhatsApp
                      </a>
                    </div>
                  ),
                },
                {
                  icon: Mail,
                  title: "Sales & support",
                  body: (
                    <a href={`mailto:${EMAIL_SALES}`} className="hover:text-signal-bright">
                      {EMAIL_SALES}
                    </a>
                  ),
                },
                {
                  icon: MapPin,
                  title: "Base & coverage",
                  body: (
                    <div className="space-y-1">
                      <p>{LOCATION_LINE}</p>
                      <p className="text-bone/50">{COVERAGE_LINE}</p>
                    </div>
                  ),
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
