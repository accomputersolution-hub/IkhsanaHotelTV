"use client";

import Link from "next/link";
import Image from "next/image";
import {
  ArrowLeft,
  BadgeIndianRupee,
  CheckCircle2,
  ClipboardList,
  Route,
  Sparkles,
  Users,
} from "lucide-react";
import { motion, useReducedMotion } from "framer-motion";
import type { Service } from "@/lib/services";
import { serviceIcons } from "@/lib/icons";
import { Reveal } from "./Reveal";

type Props = {
  service: Service;
};

const heroBySlug: Record<string, string> = {
  "smart-tv-kiosk": "/images/pcncloud-hotel-room.jpg",
  "enterprise-wifi": "/images/pcncloud-corporate-lobby.jpg",
  "security-surveillance": "/images/pcncloud-admin-control.jpg",
  "hotel-communications": "/images/pcncloud-corporate-lobby.jpg",
  "smart-access": "/images/pcncloud-hotel-room.jpg",
  "it-maintenance": "/images/pcncloud-admin-control.jpg",
};

export function ServiceDetail({ service }: Props) {
  const reduce = useReducedMotion();
  const HeroIcon = serviceIcons[service.icon];
  const heroImage = heroBySlug[service.slug] ?? "/images/pcncloud-hotel-room.jpg";

  return (
    <main>
      <section className="relative isolate overflow-hidden pt-24">
        <div className="absolute inset-0 -z-20">
          <Image
            src={heroImage}
            alt=""
            fill
            priority
            className="object-cover object-center opacity-35"
            sizes="100vw"
          />
          <div className="absolute inset-0 bg-gradient-to-b from-ink-950/70 via-ink-950/85 to-ink-950" />
        </div>
        <div className="pointer-events-none absolute -left-24 top-20 h-72 w-72 rounded-full bg-neon-blue/25 blur-[100px]" />
        <div className="pointer-events-none absolute -right-16 top-40 h-80 w-80 rounded-full bg-neon-purple/20 blur-[110px]" />

        <div className="section-pad relative pb-12 pt-6 md:pb-16">
          <Link
            href="/#services"
            className="inline-flex items-center gap-2 text-sm font-medium text-slate-300 transition hover:text-neon-cyan"
          >
            <ArrowLeft className="h-4 w-4" aria-hidden />
            Back to Home
          </Link>

          <motion.div
            initial={reduce ? false : { opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.55 }}
            className="mt-8 max-w-3xl"
          >
            <div className="mb-6 inline-flex h-14 w-14 items-center justify-center rounded-2xl border border-white/10 bg-ink-800 text-neon-cyan shadow-glow">
              <HeroIcon className="h-7 w-7" aria-hidden />
            </div>
            <p className="text-xs font-semibold uppercase tracking-[0.2em] text-neon-cyan">
              Service detail
            </p>
            <h1 className="mt-3 font-display text-4xl font-semibold tracking-tight text-white sm:text-5xl">
              {service.title}
            </h1>
            <p className="mt-6 text-base leading-relaxed text-slate-300 sm:text-lg">
              {service.heroDescription}
            </p>
            <div className="mt-8 flex flex-wrap gap-3">
              <a href="#contact-sales" className="btn-primary">
                Contact Sales
              </a>
              <Link href="/#opex" className="btn-secondary">
                View pricing model
              </Link>
            </div>
          </motion.div>
        </div>
      </section>

      <section className="section-pad pt-2 md:pt-4">
        <Reveal>
          <div className="glass rounded-3xl p-6 sm:p-8">
            <div className="mb-4 flex items-center gap-2 text-neon-cyan">
              <Users className="h-5 w-5" aria-hidden />
              <p className="text-xs font-semibold uppercase tracking-[0.2em]">Ideal for</p>
            </div>
            <div className="flex flex-wrap gap-2">
              {service.idealFor.map((item) => (
                <span
                  key={item}
                  className="rounded-full border border-white/10 bg-white/5 px-3 py-1.5 text-sm text-slate-200"
                >
                  {item}
                </span>
              ))}
            </div>
          </div>
        </Reveal>
      </section>

      <section className="section-pad pt-8 md:pt-12">
        <div className="grid gap-8 lg:grid-cols-2">
          <Reveal>
            <div className="glass h-full rounded-3xl p-6 sm:p-8">
              <div className="mb-5 flex items-center gap-2 text-neon-cyan">
                <ClipboardList className="h-5 w-5" aria-hidden />
                <p className="text-xs font-semibold uppercase tracking-[0.2em]">
                  What&apos;s included
                </p>
              </div>
              <h2 className="font-display text-2xl font-semibold text-white">
                Package details for{" "}
                <span className="text-gradient">{service.shortTitle}</span>
              </h2>
              <ul className="mt-6 space-y-3">
                {service.included.map((item) => (
                  <li key={item} className="flex items-start gap-3 text-sm text-slate-300">
                    <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-neon-cyan" aria-hidden />
                    <span>{item}</span>
                  </li>
                ))}
              </ul>
            </div>
          </Reveal>

          <Reveal delay={0.08}>
            <div className="glass h-full rounded-3xl p-6 sm:p-8">
              <div className="mb-5 flex items-center gap-2 text-neon-violet">
                <Sparkles className="h-5 w-5" aria-hidden />
                <p className="text-xs font-semibold uppercase tracking-[0.2em]">
                  Common use cases
                </p>
              </div>
              <h2 className="font-display text-2xl font-semibold text-white">
                Where this service pays off
              </h2>
              <ul className="mt-6 space-y-3">
                {service.useCases.map((item) => (
                  <li key={item} className="flex items-start gap-3 text-sm text-slate-300">
                    <span className="mt-2 h-1.5 w-1.5 shrink-0 rounded-full bg-neon-violet" />
                    <span>{item}</span>
                  </li>
                ))}
              </ul>
            </div>
          </Reveal>
        </div>
      </section>

      <section className="section-pad pt-4 md:pt-8">
        <Reveal>
          <p className="text-xs font-semibold uppercase tracking-[0.2em] text-neon-violet">
            Key features &amp; benefits
          </p>
          <h2 className="mt-3 max-w-2xl font-display text-3xl font-semibold text-white">
            What you get with{" "}
            <span className="text-gradient">{service.shortTitle}</span>
          </h2>
        </Reveal>

        <div className="mt-10 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {service.features.map((feature, i) => {
            const Icon = serviceIcons[feature.icon];
            return (
              <Reveal key={feature.title} delay={0.05 * i}>
                <article className="glass h-full rounded-3xl p-6 transition hover:border-neon-blue/40 hover:shadow-glow">
                  <div className="mb-4 inline-flex h-11 w-11 items-center justify-center rounded-xl bg-neon-blue/15 text-neon-cyan">
                    <Icon className="h-5 w-5" aria-hidden />
                  </div>
                  <h3 className="font-display text-lg font-semibold text-white">
                    {feature.title}
                  </h3>
                  <p className="mt-2 text-sm leading-relaxed text-slate-300">
                    {feature.description}
                  </p>
                </article>
              </Reveal>
            );
          })}
        </div>
      </section>

      <section className="section-pad pt-4 md:pt-8">
        <Reveal>
          <div className="mb-8 flex items-center gap-2 text-neon-cyan">
            <Route className="h-5 w-5" aria-hidden />
            <p className="text-xs font-semibold uppercase tracking-[0.2em]">How we deliver</p>
          </div>
          <h2 className="max-w-2xl font-display text-3xl font-semibold text-white">
            A clear path from survey to steady-state
          </h2>
        </Reveal>

        <div className="mt-10 grid gap-5 sm:grid-cols-2">
          {service.process.map((step, i) => (
            <Reveal key={step.title} delay={0.06 * i}>
              <article className="glass relative h-full overflow-hidden rounded-3xl p-6">
                <span className="font-display text-4xl font-semibold text-white/10">
                  {String(i + 1).padStart(2, "0")}
                </span>
                <h3 className="mt-2 font-display text-lg font-semibold text-white">
                  {step.title}
                </h3>
                <p className="mt-2 text-sm leading-relaxed text-slate-300">
                  {step.description}
                </p>
              </article>
            </Reveal>
          ))}
        </div>
      </section>

      <section className="section-pad py-10 md:py-14">
        <Reveal>
          <div className="glass-strong relative overflow-hidden rounded-[2rem] p-8 sm:p-10">
            <div className="pointer-events-none absolute -right-16 -top-16 h-56 w-56 rounded-full bg-neon-violet/25 blur-[80px]" />
            <div className="relative flex flex-col gap-6 md:flex-row md:items-center md:justify-between">
              <div className="max-w-2xl">
                <div className="mb-4 inline-flex h-12 w-12 items-center justify-center rounded-2xl bg-neon-violet/20 text-neon-violet">
                  <BadgeIndianRupee className="h-6 w-6" aria-hidden />
                </div>
                <h2 className="font-display text-2xl font-semibold text-white sm:text-3xl">
                  OpEx advantage —{" "}
                  <span className="text-gradient">monthly, not massive CapEx</span>
                </h2>
                <p className="mt-3 text-slate-300">{service.opexNote}</p>
                <ul className="mt-5 space-y-2">
                  {[
                    "Zero heavy upfront cost",
                    "Hardware replacements under rental/maintenance",
                    "Continuous proactive care included",
                  ].map((item) => (
                    <li key={item} className="flex items-center gap-2 text-sm text-slate-300">
                      <CheckCircle2 className="h-4 w-4 shrink-0 text-neon-cyan" aria-hidden />
                      {item}
                    </li>
                  ))}
                </ul>
              </div>
              <Link href="/#opex" className="btn-secondary shrink-0 self-start md:self-center">
                Explore OpEx model
              </Link>
            </div>
          </div>
        </Reveal>
      </section>

      <section id="contact-sales" className="section-pad pb-24 pt-6">
        <Reveal>
          <div className="relative overflow-hidden rounded-[2rem] border border-white/10 bg-gradient-to-br from-sky-500/20 via-ink-900/80 to-violet-500/20 p-8 text-center sm:p-12">
            <div className="pointer-events-none absolute inset-0 bg-hero-grid bg-grid opacity-30" />
            <div className="relative mx-auto max-w-2xl">
              <h2 className="font-display text-3xl font-semibold text-white sm:text-4xl">
                Ready to upgrade your hotel?
              </h2>
              <p className="mt-4 text-slate-300">
                Talk to PCN Cloud about {service.shortTitle.toLowerCase()} for your property —
                scoping, rental options, and a single accountable rollout plan.
              </p>
              <div className="mt-8 flex flex-wrap justify-center gap-3">
                <a
                  href={`mailto:hello@pcncloud.in?subject=${encodeURIComponent(
                    `PCN Cloud — ${service.shortTitle}`,
                  )}`}
                  className="btn-primary"
                >
                  Contact Sales
                </a>
                <Link href="/#services" className="btn-secondary">
                  All services
                </Link>
              </div>
            </div>
          </div>
        </Reveal>
      </section>
    </main>
  );
}
