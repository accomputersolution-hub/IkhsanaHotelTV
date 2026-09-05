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
import { WHATSAPP_URL } from "@/lib/contact";
import { Reveal } from "./Reveal";
import { WhatsAppIcon } from "./WhatsAppFloat";

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
            className="object-cover object-center"
            sizes="100vw"
          />
          <div className="absolute inset-0 veil" />
          <div className="absolute inset-0 film-grain" aria-hidden />
        </div>

        <div className="section-pad relative pb-14 pt-6 md:pb-20">
          <Link
            href="/#services"
            className="inline-flex items-center gap-2 text-sm font-medium text-bone/70 transition hover:text-signal-bright"
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
            <div className="mb-6 inline-flex h-14 w-14 items-center justify-center rounded-md border border-bone/15 bg-void-mute text-signal-bright">
              <HeroIcon className="h-7 w-7" aria-hidden />
            </div>
            <p className="eyebrow">Service detail</p>
            <h1 className="mt-4 text-balance font-display text-[2rem] font-semibold tracking-tight text-bone sm:text-5xl lg:text-6xl">
              {service.title}
            </h1>
            <p className="mt-5 text-[0.95rem] leading-relaxed text-bone/70 sm:mt-6 sm:text-lg">
              {service.heroDescription}
            </p>
            <div className="mt-7 flex w-full max-w-md flex-col gap-3 sm:mt-8 sm:max-w-none sm:flex-row sm:flex-wrap">
              <a
                href={WHATSAPP_URL}
                target="_blank"
                rel="noopener noreferrer"
                className="btn-primary"
              >
                <WhatsAppIcon className="h-4 w-4" />
                WhatsApp us
              </a>
              <Link href="/#opex" className="btn-secondary">
                View pricing model
              </Link>
            </div>
          </motion.div>
        </div>
      </section>

      <section className="bg-bone text-void">
        <div className="section-pad !py-14 md:!py-16">
          <Reveal>
            <div className="mb-4 flex items-center gap-2 text-signal-dim">
              <Users className="h-5 w-5" aria-hidden />
              <p className="text-xs font-semibold uppercase tracking-[0.2em]">Ideal for</p>
            </div>
            <div className="flex flex-wrap gap-2">
              {service.idealFor.map((item) => (
                <span
                  key={item}
                  className="rounded-md border border-void/15 bg-void/[0.04] px-3 py-1.5 text-sm text-void/80"
                >
                  {item}
                </span>
              ))}
            </div>
          </Reveal>
        </div>
      </section>

      <section className="section-pad">
        <div className="grid gap-12 lg:grid-cols-2">
          <Reveal>
            <div className="border-t border-bone/15 pt-8">
              <div className="mb-5 flex items-center gap-2 text-brass">
                <ClipboardList className="h-5 w-5" aria-hidden />
                <p className="text-xs font-semibold uppercase tracking-[0.2em]">
                  What&apos;s included
                </p>
              </div>
              <h2 className="font-display text-2xl font-semibold text-bone sm:text-3xl">
                Package details for {service.shortTitle}
              </h2>
              <ul className="mt-6 space-y-3">
                {service.included.map((item) => (
                  <li key={item} className="flex items-start gap-3 text-sm text-bone/65">
                    <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-signal-bright" aria-hidden />
                    <span>{item}</span>
                  </li>
                ))}
              </ul>
            </div>
          </Reveal>

          <Reveal delay={0.08}>
            <div className="border-t border-bone/15 pt-8">
              <div className="mb-5 flex items-center gap-2 text-brass">
                <Sparkles className="h-5 w-5" aria-hidden />
                <p className="text-xs font-semibold uppercase tracking-[0.2em]">
                  Common use cases
                </p>
              </div>
              <h2 className="font-display text-2xl font-semibold text-bone sm:text-3xl">
                Where this service pays off
              </h2>
              <ul className="mt-6 space-y-3">
                {service.useCases.map((item) => (
                  <li key={item} className="flex items-start gap-3 text-sm text-bone/65">
                    <span className="mt-2 h-1.5 w-1.5 shrink-0 rounded-full bg-signal" />
                    <span>{item}</span>
                  </li>
                ))}
              </ul>
            </div>
          </Reveal>
        </div>
      </section>

      <section className="section-pad !pt-4 md:!pt-8">
        <Reveal>
          <p className="eyebrow">Key features &amp; benefits</p>
          <h2 className="mt-3 max-w-2xl font-display text-3xl font-semibold text-bone sm:text-4xl">
            What you get with {service.shortTitle}
          </h2>
        </Reveal>

        <div className="mt-10 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {service.features.map((feature, i) => {
            const Icon = serviceIcons[feature.icon];
            return (
              <Reveal key={feature.title} delay={0.05 * i}>
                <article className="border-t border-bone/15 pt-5">
                  <div className="mb-4 inline-flex h-11 w-11 items-center justify-center rounded-md border border-bone/10 bg-void-mute text-signal-bright">
                    <Icon className="h-5 w-5" aria-hidden />
                  </div>
                  <h3 className="font-display text-lg font-semibold text-bone">
                    {feature.title}
                  </h3>
                  <p className="mt-2 text-sm leading-relaxed text-bone/55">
                    {feature.description}
                  </p>
                </article>
              </Reveal>
            );
          })}
        </div>
      </section>

      <section className="bg-bone text-void">
        <div className="section-pad">
          <Reveal>
            <div className="mb-8 flex items-center gap-2 text-signal-dim">
              <Route className="h-5 w-5" aria-hidden />
              <p className="text-xs font-semibold uppercase tracking-[0.2em]">How we deliver</p>
            </div>
            <h2 className="max-w-2xl font-display text-3xl font-semibold sm:text-4xl">
              A clear path from survey to steady-state
            </h2>
          </Reveal>

          <div className="mt-10 grid gap-8 sm:grid-cols-2">
            {service.process.map((step, i) => (
              <Reveal key={step.title} delay={0.06 * i}>
                <article className="border-t border-void/15 pt-5">
                  <span className="font-display text-4xl font-semibold text-void/15">
                    {String(i + 1).padStart(2, "0")}
                  </span>
                  <h3 className="mt-2 font-display text-xl font-semibold">{step.title}</h3>
                  <p className="mt-2 text-sm leading-relaxed text-void/60">{step.description}</p>
                </article>
              </Reveal>
            ))}
          </div>
        </div>
      </section>

      <section className="section-pad">
        <Reveal>
          <div className="flex flex-col gap-8 border-t border-bone/15 pt-10 md:flex-row md:items-center md:justify-between">
            <div className="max-w-2xl">
              <div className="mb-4 inline-flex h-12 w-12 items-center justify-center rounded-md border border-bone/10 bg-void-mute text-brass">
                <BadgeIndianRupee className="h-6 w-6" aria-hidden />
              </div>
              <h2 className="font-display text-2xl font-semibold text-bone sm:text-3xl">
                OpEx advantage — monthly, not massive CapEx
              </h2>
              <p className="mt-3 text-bone/65">{service.opexNote}</p>
              <ul className="mt-5 space-y-2">
                {[
                  "Zero heavy upfront cost",
                  "Hardware replacements under rental/maintenance",
                  "Continuous proactive care included",
                ].map((item) => (
                  <li key={item} className="flex items-center gap-2 text-sm text-bone/65">
                    <CheckCircle2 className="h-4 w-4 shrink-0 text-signal-bright" aria-hidden />
                    {item}
                  </li>
                ))}
              </ul>
            </div>
            <Link href="/#opex" className="btn-secondary shrink-0 self-start md:self-center">
              Explore OpEx model
            </Link>
          </div>
        </Reveal>
      </section>

      <section id="contact-sales" className="relative overflow-hidden">
        <div className="absolute inset-0">
          <Image
            src={heroImage}
            alt=""
            fill
            className="object-cover opacity-40"
            sizes="100vw"
          />
          <div className="absolute inset-0 bg-void/85" />
        </div>
        <div className="section-pad relative text-center">
          <Reveal>
            <div className="mx-auto max-w-2xl">
              <h2 className="font-display text-3xl font-semibold text-bone sm:text-5xl">
                Ready to upgrade your hotel?
              </h2>
              <p className="mt-4 text-bone/65">
                Talk to PCN Cloud about {service.shortTitle.toLowerCase()} for your property —
                scoping, rental options, and a single accountable rollout plan.
              </p>
              <div className="mt-8 flex flex-wrap justify-center gap-3">
                <a
                  href={WHATSAPP_URL}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="btn-primary"
                >
                  <WhatsAppIcon className="h-4 w-4" />
                  WhatsApp us
                </a>
                <Link href="/#services" className="btn-secondary">
                  All services
                </Link>
              </div>
            </div>
          </Reveal>
        </div>
      </section>
    </main>
  );
}
