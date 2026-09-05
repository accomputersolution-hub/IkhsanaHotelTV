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
    <section id="services" className="relative bg-void">
      <div className="section-pad">
        <Reveal>
          <p className="eyebrow">Core offerings</p>
          <h2 className="mt-4 max-w-3xl text-balance font-display text-[2rem] font-semibold tracking-tight text-bone sm:text-5xl md:text-6xl">
            Six systems.{" "}
            <span className="text-brass-soft">One accountable partner.</span>
          </h2>
          <p className="mt-4 max-w-xl text-[0.95rem] text-bone/65 sm:mt-5 sm:text-base">
            Open any line for packages, use cases, and how we roll it out — without
            turning your GM into a project manager.
          </p>
        </Reveal>

        <div className="mt-10 divide-y divide-bone/10 border-y border-bone/10 sm:mt-14">
          {services.map((service, i) => {
            const Icon = serviceIcons[service.icon];
            return (
              <motion.div
                key={service.slug}
                initial={reduce ? false : { opacity: 0, y: 20 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true, margin: "-8%" }}
                transition={{ duration: 0.5, delay: 0.04 * i }}
              >
                <Link
                  href={`/services/${service.slug}`}
                  className="group grid gap-3 py-6 pr-14 transition duration-300 hover:bg-bone/[0.03] sm:grid-cols-[4.5rem_1fr_auto] sm:items-center sm:gap-8 sm:py-8 sm:pr-0"
                >
                  <div className="flex items-center justify-between gap-3 sm:contents">
                    <span className="font-display text-sm tracking-[0.2em] text-brass">
                      {String(i + 1).padStart(2, "0")}
                    </span>
                    <span className="inline-flex items-center gap-2 text-[10px] font-semibold uppercase tracking-[0.2em] text-bone/40 transition group-hover:text-signal-bright sm:hidden">
                      Open
                      <ArrowUpRight className="h-3.5 w-3.5" />
                    </span>
                  </div>
                  <div className="flex items-start gap-3.5 sm:items-center sm:gap-4">
                    <span className="mt-0.5 inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-md border border-bone/10 bg-void-mute text-signal-bright transition group-hover:border-signal/40 sm:mt-0 sm:h-11 sm:w-11">
                      <Icon className="h-5 w-5" aria-hidden />
                    </span>
                    <div>
                      <h3 className="font-display text-xl font-semibold text-bone transition group-hover:text-brass-soft sm:text-3xl">
                        {service.shortTitle}
                      </h3>
                      <p className="mt-1.5 max-w-2xl text-sm leading-relaxed text-bone/55 sm:mt-2 sm:text-base">
                        {service.summary}
                      </p>
                    </div>
                  </div>
                  <span className="hidden items-center gap-2 self-start text-xs font-semibold uppercase tracking-[0.2em] text-bone/40 transition group-hover:text-signal-bright sm:inline-flex sm:self-center">
                    Open
                    <ArrowUpRight className="h-4 w-4 transition group-hover:translate-x-0.5 group-hover:-translate-y-0.5" />
                  </span>
                </Link>
              </motion.div>
            );
          })}
        </div>
      </div>
    </section>
  );
}
