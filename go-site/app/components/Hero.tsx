"use client";

import { motion, useReducedMotion } from "framer-motion";
import Image from "next/image";

export function Hero() {
  const reduce = useReducedMotion();

  return (
    <section id="top" className="relative isolate min-h-[100svh] overflow-hidden pt-16">
      <div className="absolute inset-0 -z-20">
        <Image
          src="/images/pcncloud-hotel-room.jpg"
          alt=""
          fill
          priority
          className="scale-105 object-cover object-center opacity-50"
          sizes="100vw"
        />
        <div className="absolute inset-0 bg-gradient-to-b from-ink-950/55 via-ink-950/75 to-ink-950" />
        <div className="absolute inset-0 bg-gradient-to-r from-ink-950/70 via-transparent to-ink-950/40" />
        <div className="absolute inset-0 bg-hero-grid bg-grid opacity-30" />
      </div>

      <div className="pointer-events-none absolute -left-28 top-16 h-[22rem] w-[22rem] rounded-full bg-sky-500/25 blur-[120px]" />
      <div className="pointer-events-none absolute -right-20 top-32 h-[24rem] w-[24rem] rounded-full bg-violet-500/20 blur-[130px]" />
      <div className="pointer-events-none absolute bottom-0 left-1/3 h-40 w-[36rem] -translate-x-1/2 rounded-full bg-cyan-400/10 blur-[90px]" />

      <div className="section-pad relative flex min-h-[calc(100svh-4rem)] flex-col justify-center pb-24">
        <motion.p
          initial={reduce ? false : { opacity: 0, y: 18 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.55 }}
          className="font-display text-3xl font-semibold tracking-tight text-white sm:text-4xl"
        >
          PCN <span className="text-gradient">Cloud</span>
        </motion.p>

        <motion.h1
          initial={reduce ? false : { opacity: 0, y: 26 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.7, delay: 0.08 }}
          className="mt-5 max-w-4xl font-display text-4xl font-semibold leading-[1.05] tracking-tight text-white sm:text-5xl lg:text-[3.75rem]"
        >
          Elevate Your Hotel&apos;s Tech &amp; Security.
          <br />
          <span className="text-gradient">Zero Hassle. Zero Downtime.</span>
        </motion.h1>

        <motion.p
          initial={reduce ? false : { opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.65, delay: 0.16 }}
          className="mt-6 max-w-2xl text-base leading-relaxed text-slate-300 sm:text-lg"
        >
          The single-point IT, media, and hardware partner for hotels, resorts, and enterprise
          campuses — Smart TV kiosks, Wi‑Fi rental, CCTV, EPABX, smart access, and 24/7
          infrastructure care.
        </motion.p>

        <motion.div
          initial={reduce ? false : { opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.6, delay: 0.24 }}
          className="mt-10 flex flex-wrap gap-3"
        >
          <a href="#services" className="btn-primary">
            Explore Services
          </a>
          <a href="#contact" className="btn-secondary">
            Book a Consultation
          </a>
        </motion.div>
      </div>

      <div className="pointer-events-none absolute inset-x-0 bottom-0 h-px glow-line opacity-70" />
    </section>
  );
}
