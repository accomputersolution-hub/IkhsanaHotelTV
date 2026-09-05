"use client";

import Image from "next/image";
import { motion, useReducedMotion } from "framer-motion";
import { ArrowDownRight } from "lucide-react";

export function Hero() {
  const reduce = useReducedMotion();

  return (
    <section id="top" className="relative isolate min-h-[100svh] overflow-hidden">
      <div className="absolute inset-0 -z-20">
        <motion.div
          initial={reduce ? false : { scale: 1.12 }}
          animate={{ scale: 1 }}
          transition={{ duration: 8, ease: [0.22, 1, 0.36, 1] }}
          className="absolute inset-0"
        >
          <Image
            src="/images/pcncloud-hotel-room.jpg"
            alt=""
            fill
            priority
            className="object-cover object-[center_35%] opacity-55"
            sizes="100vw"
          />
        </motion.div>
        <div className="absolute inset-0 bg-gradient-to-b from-ink-950/40 via-ink-950/70 to-ink-950" />
        <div className="absolute inset-0 bg-gradient-to-r from-ink-950/85 via-ink-950/35 to-transparent" />
        <div className="absolute inset-0 bg-hero-grid bg-grid opacity-[0.22]" />
        <div className="absolute inset-0 bg-noise-fade" />
      </div>

      <div className="pointer-events-none absolute -left-32 top-10 h-[28rem] w-[28rem] rounded-full bg-neon-blue/20 blur-[130px]" />
      <div className="pointer-events-none absolute right-0 top-24 h-[26rem] w-[26rem] rounded-full bg-neon-violet/20 blur-[140px]" />

      <div className="section-pad relative flex min-h-[100svh] flex-col justify-end pb-16 pt-28 md:justify-center md:pb-28 md:pt-24">
        <div className="grid items-end gap-10 lg:grid-cols-[1.15fr_0.85fr]">
          <div>
            <motion.p
              initial={reduce ? false : { opacity: 0, y: 16 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.55 }}
              className="eyebrow"
            >
              Hospitality infrastructure partner
            </motion.p>

            <motion.h1
              initial={reduce ? false : { opacity: 0, y: 28 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.75, delay: 0.06 }}
              className="mt-5 max-w-3xl font-display text-[2.75rem] font-semibold leading-[0.98] tracking-tight text-white sm:text-6xl lg:text-[4.35rem]"
            >
              <span className="block text-champagne">PCN Cloud</span>
              <span className="mt-2 block">
                Tech that disappears.
                <br />
                <span className="text-gradient">Hospitality that shines.</span>
              </span>
            </motion.h1>

            <motion.p
              initial={reduce ? false : { opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.65, delay: 0.14 }}
              className="mt-6 max-w-xl text-base leading-relaxed text-slate-300 sm:text-lg"
            >
              One vendor for Smart TV kiosks, Wi‑Fi rental, CCTV, EPABX, smart locks, and
              24/7 IT — so owners run hotels, not a zoo of tech partners.
            </motion.p>

            <motion.div
              initial={reduce ? false : { opacity: 0, y: 16 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.6, delay: 0.22 }}
              className="mt-10 flex flex-wrap items-center gap-3"
            >
              <a href="#services" className="btn-primary">
                Explore the stack
              </a>
              <a href="#contact" className="btn-secondary">
                Talk to sales
              </a>
            </motion.div>
          </div>

          <motion.div
            initial={reduce ? false : { opacity: 0, y: 30 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8, delay: 0.2 }}
            className="relative hidden lg:block"
          >
            <div className="absolute -inset-6 rounded-[2rem] bg-gradient-to-br from-neon-cyan/20 via-transparent to-neon-violet/20 blur-2xl" />
            <div className="glass-strong relative overflow-hidden rounded-[1.75rem] p-3 shadow-lift">
              <div className="relative aspect-[4/5] overflow-hidden rounded-[1.35rem]">
                <Image
                  src="/images/pcncloud-admin-control.jpg"
                  alt="PCN Cloud command console"
                  fill
                  className="object-cover"
                  sizes="420px"
                />
                <div className="absolute inset-0 bg-gradient-to-t from-ink-950 via-transparent to-transparent" />
                <div className="absolute bottom-0 left-0 right-0 p-5">
                  <p className="text-[11px] font-semibold uppercase tracking-[0.22em] text-neon-cyan">
                    Live command plane
                  </p>
                  <p className="mt-2 font-display text-xl font-semibold text-white">
                    Rooms, networks, and security — one throat to choke.
                  </p>
                </div>
              </div>
            </div>
            <div className="absolute -bottom-5 -left-5 glass rounded-2xl px-4 py-3 shadow-lift">
              <p className="text-[10px] uppercase tracking-[0.2em] text-champagne-dim">Coverage</p>
              <p className="font-display text-lg font-semibold text-white">India-wide hospitality</p>
            </div>
          </motion.div>
        </div>

        <motion.a
          href="#showcase"
          initial={reduce ? false : { opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ delay: 0.8 }}
          className="mt-14 inline-flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.22em] text-slate-400 transition hover:text-neon-cyan"
        >
          Scroll the experience
          <ArrowDownRight className="h-4 w-4" />
        </motion.a>
      </div>

      <div className="pointer-events-none absolute inset-x-0 bottom-0 h-px glow-line opacity-80" />
    </section>
  );
}
