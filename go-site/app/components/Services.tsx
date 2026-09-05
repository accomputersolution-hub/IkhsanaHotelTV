"use client";

import Link from "next/link";
import { ArrowUpRight } from "lucide-react";
import { motion, useReducedMotion } from "framer-motion";
import { services } from "@/lib/services";
import { serviceIcons } from "@/lib/icons";
import { Reveal } from "./Reveal";

export function Services() {
  const reduce = useReducedMotion();
  const [featured, ...rest] = services;

  return (
    <section id="services" className="section-pad relative">
      <div className="pointer-events-none absolute right-0 top-20 h-80 w-80 rounded-full bg-neon-violet/10 blur-[120px]" />

      <Reveal>
        <p className="eyebrow">Core offerings</p>
        <h2 className="mt-4 max-w-3xl font-display text-3xl font-semibold tracking-tight text-white sm:text-5xl">
          Six systems.{" "}
          <span className="text-gradient">One accountable partner.</span>
        </h2>
        <p className="mt-4 max-w-2xl text-slate-300">
          Click into any service for package details, use cases, and how we roll it out —
          without turning your GM into a project manager.
        </p>
      </Reveal>

      <div className="mt-12 grid gap-5 lg:grid-cols-12">
        {featured && (
          <motion.div
            initial={reduce ? false : { opacity: 0, y: 24 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, margin: "-8%" }}
            transition={{ duration: 0.55 }}
            className="lg:col-span-7"
          >
            <FeatureCard service={featured} featured />
          </motion.div>
        )}

        <div className="grid gap-5 sm:grid-cols-2 lg:col-span-5 lg:grid-cols-1">
          {rest.slice(0, 2).map((service, i) => (
            <motion.div
              key={service.slug}
              initial={reduce ? false : { opacity: 0, y: 24 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true, margin: "-8%" }}
              transition={{ duration: 0.5, delay: 0.08 * (i + 1) }}
            >
              <FeatureCard service={service} />
            </motion.div>
          ))}
        </div>

        {rest.slice(2).map((service, i) => (
          <motion.div
            key={service.slug}
            initial={reduce ? false : { opacity: 0, y: 24 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, margin: "-8%" }}
            transition={{ duration: 0.5, delay: 0.05 * i }}
            className="lg:col-span-4"
          >
            <FeatureCard service={service} />
          </motion.div>
        ))}
      </div>
    </section>
  );
}

function FeatureCard({
  service,
  featured = false,
}: {
  service: (typeof services)[number];
  featured?: boolean;
}) {
  const Icon = serviceIcons[service.icon];

  return (
    <Link
      href={`/services/${service.slug}`}
      className={`group relative flex h-full flex-col overflow-hidden rounded-[1.75rem] border border-white/10 bg-gradient-to-br from-white/[0.07] to-white/[0.02] p-6 shadow-lift backdrop-blur-xl transition duration-300 hover:-translate-y-1 hover:border-neon-cyan/35 hover:shadow-glow-cyan focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-neon-cyan ${
        featured ? "min-h-[22rem] sm:p-8" : ""
      }`}
    >
      <div className="absolute inset-x-0 top-0 h-px glow-line opacity-40 transition group-hover:opacity-100" />
      <div
        className={`pointer-events-none absolute inset-0 bg-gradient-to-br ${service.accent} opacity-0 transition duration-300 group-hover:opacity-100`}
      />

      <div className="relative flex flex-1 flex-col">
        <div className="mb-5 flex items-start justify-between gap-3">
          <div className="inline-flex h-12 w-12 items-center justify-center rounded-2xl border border-white/10 bg-ink-800 text-neon-cyan transition group-hover:border-neon-cyan/40">
            <Icon className="h-6 w-6" aria-hidden />
          </div>
          <span className="inline-flex items-center gap-1 rounded-full border border-white/10 bg-white/5 px-2.5 py-1 text-[11px] font-medium text-slate-400 transition group-hover:border-neon-cyan/35 group-hover:text-neon-cyan">
            Open
            <ArrowUpRight className="h-3.5 w-3.5" aria-hidden />
          </span>
        </div>

        <h3
          className={`font-display font-semibold text-white ${
            featured ? "text-3xl leading-tight sm:text-4xl" : "text-xl"
          }`}
        >
          {service.shortTitle}
        </h3>
        <p
          className={`mt-3 flex-1 leading-relaxed text-slate-300 ${
            featured ? "text-base" : "text-sm"
          }`}
        >
          {service.summary}
        </p>

        <ul className={`mt-5 space-y-2 border-t border-white/5 pt-4 ${featured ? "grid sm:grid-cols-2 sm:gap-x-4" : ""}`}>
          {(featured ? service.points : service.points.slice(0, 3)).map((p) => (
            <li key={p} className="flex items-center gap-2 text-xs text-slate-400">
              <span className="h-1.5 w-1.5 rounded-full bg-neon-cyan" />
              {p}
            </li>
          ))}
        </ul>
      </div>
    </Link>
  );
}
