"use client";

import Image from "next/image";
import { Reveal } from "./Reveal";
import { Mail, MapPin, Phone } from "lucide-react";

export function Contact() {
  return (
    <section id="contact" className="section-pad">
      <div className="glass-strong relative overflow-hidden rounded-[2rem] p-8 sm:p-12">
        <div className="pointer-events-none absolute inset-0 opacity-30">
          <Image
            src="/images/pcncloud-corporate-lobby.jpg"
            alt=""
            fill
            className="object-cover"
            sizes="100vw"
          />
          <div className="absolute inset-0 bg-ink-950/85" />
        </div>
        <div className="pointer-events-none absolute -right-20 -top-20 h-64 w-64 rounded-full bg-sky-500/25 blur-[90px]" />
        <div className="pointer-events-none absolute -bottom-24 -left-16 h-64 w-64 rounded-full bg-violet-500/20 blur-[90px]" />

        <div className="relative grid gap-10 lg:grid-cols-[1.2fr_0.8fr]">
          <Reveal>
            <p className="text-xs font-semibold uppercase tracking-[0.2em] text-neon-cyan">
              Contact
            </p>
            <h2 className="mt-3 font-display text-3xl font-semibold text-white sm:text-4xl">
              Book a consultation with{" "}
              <span className="text-gradient">PCN Cloud</span>
            </h2>
            <p className="mt-4 max-w-xl text-slate-300">
              Tell us about your property count, current Wi‑Fi/CCTV pain, or TV rollout plan.
              We&apos;ll map a single-vendor roadmap — kiosk, network rental, security, and IT.
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

          <Reveal delay={0.12}>
            <div className="space-y-4 rounded-3xl border border-white/10 bg-ink-950/70 p-6 backdrop-blur-xl">
              <div className="flex items-start gap-3 text-sm text-slate-300">
                <Mail className="mt-0.5 h-5 w-5 text-neon-cyan" aria-hidden />
                <div>
                  <p className="font-semibold text-white">Support &amp; sales</p>
                  <a href="mailto:hello@pcncloud.in" className="hover:text-neon-cyan">
                    hello@pcncloud.in
                  </a>
                </div>
              </div>
              <div className="flex items-start gap-3 text-sm text-slate-300">
                <Phone className="mt-0.5 h-5 w-5 text-neon-cyan" aria-hidden />
                <div>
                  <p className="font-semibold text-white">Consultations</p>
                  <p>Remote or on-property walkthroughs for hotels &amp; campuses</p>
                </div>
              </div>
              <div className="flex items-start gap-3 text-sm text-slate-300">
                <MapPin className="mt-0.5 h-5 w-5 text-neon-cyan" aria-hidden />
                <div>
                  <p className="font-semibold text-white">Coverage</p>
                  <p>Hospitality &amp; enterprise deployments across India</p>
                </div>
              </div>
            </div>
          </Reveal>
        </div>
      </div>
    </section>
  );
}
