"use client";

import Link from "next/link";
import { ArrowUpRight } from "lucide-react";
import { motion, useReducedMotion } from "framer-motion";
import { services } from "@/lib/services";
import { serviceIcons } from "@/lib/icons";
import { Reveal } from "./Reveal";

export function Services() {
  const reduce = useReducedMotion();

  return (
    <section id="services" className="section-pad relative">
      <Reveal>
        <p className="text-xs font-semibold uppercase tracking-[0.2em] text-neon-cyan">
          Core offerings
        </p>
        <h2 className="mt-3 max-w-3xl font-display text-3xl font-semibold tracking-tight text-white sm:text-4xl">
          Everything your property needs —{" "}
          <span className="text-gradient">one accountable partner</span>
        </h2>
        <p className="mt-4 max-w-2xl text-slate-300">
          From the guest-room TV to the backbone network, PCN Cloud designs, deploys, and
          maintains the stack hotels and enterprises actually rely on. Click any card for a
          deeper dive.
        </p>
      </Reveal>

      <div className="mt-12 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
        {services.map((service, i) => {
          const Icon = serviceIcons[service.icon];
          return (
            <motion.div
              key={service.slug}
              initial={reduce ? false : { opacity: 0, y: 28 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true, margin: "-8%" }}
              transition={{ duration: 0.5, delay: i * 0.06 }}
              whileHover={reduce ? undefined : { y: -8, scale: 1.02 }}
              className="h-full"
            >
              <Link
                href={`/services/${service.slug}`}
                className="group relative flex h-full flex-col overflow-hidden rounded-3xl border border-white/10 bg-white/[0.04] p-6 shadow-glass backdrop-blur-xl transition duration-300 hover:border-neon-blue/50 hover:shadow-glow focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sky-400"
              >
                <div
                  className={`pointer-events-none absolute inset-0 bg-gradient-to-br ${service.accent} opacity-0 transition duration-300 group-hover:opacity-100`}
                />
                <div className="relative flex flex-1 flex-col">
                  <div className="mb-5 flex items-start justify-between gap-3">
                    <div className="inline-flex h-12 w-12 items-center justify-center rounded-2xl border border-white/10 bg-ink-800 text-neon-cyan shadow-glow transition group-hover:border-neon-cyan/40 group-hover:text-white">
                      <Icon className="h-6 w-6" aria-hidden />
                    </div>
                    <span className="inline-flex items-center gap-1 rounded-full border border-white/10 bg-white/5 px-2.5 py-1 text-[11px] font-medium text-slate-400 transition group-hover:border-neon-blue/40 group-hover:text-neon-cyan">
                      Learn more
                      <ArrowUpRight className="h-3.5 w-3.5" aria-hidden />
                    </span>
                  </div>
                  <h3 className="font-display text-xl font-semibold text-white transition group-hover:text-sky-100">
                    {service.shortTitle}
                  </h3>
                  <p className="mt-3 flex-1 text-sm leading-relaxed text-slate-300">
                    {service.summary}
                  </p>
                  <ul className="mt-5 space-y-2">
                    {service.points.map((p) => (
                      <li key={p} className="flex items-center gap-2 text-xs text-slate-400">
                        <span className="h-1.5 w-1.5 rounded-full bg-neon-blue" />
                        {p}
                      </li>
                    ))}
                  </ul>
                </div>
              </Link>
            </motion.div>
          );
        })}
      </div>
    </section>
  );
}
