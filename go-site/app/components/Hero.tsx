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
          className="object-cover object-center opacity-45"
          sizes="100vw"
        />
        <div className="absolute inset-0 bg-gradient-to-b from-ink-950/70 via-ink-950/80 to-ink-950" />
        <div className="absolute inset-0 bg-hero-grid bg-grid opacity-35" />
      </div>

      <div className="pointer-events-none absolute -left-24 top-24 h-72 w-72 rounded-full bg-neon-blue/30 blur-[100px]" />
      <div className="pointer-events-none absolute -right-16 top-40 h-80 w-80 rounded-full bg-neon-purple/25 blur-[110px]" />

      <div className="section-pad flex min-h-[calc(100svh-4rem)] flex-col justify-center">
        <motion.p
          initial={reduce ? false : { opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5 }}
          className="font-display text-2xl font-semibold tracking-tight text-white sm:text-3xl"
        >
          PCN <span className="text-gradient">Cloud</span>
        </motion.p>

        <motion.h1
          initial={reduce ? false : { opacity: 0, y: 24 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.65, delay: 0.08 }}
          className="mt-5 max-w-4xl font-display text-4xl font-semibold leading-[1.08] tracking-tight text-white sm:text-5xl lg:text-6xl"
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
          The single-point IT, media, and hardware partner for hotels, resorts, and
          enterprise clients — Smart TV kiosks, Wi‑Fi rental, CCTV, EPABX, smart access,
          and 24/7 infrastructure care.
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
    </section>
  );
}
