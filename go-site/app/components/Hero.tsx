"use client";

import Image from "next/image";
import { motion, useReducedMotion, useScroll, useTransform } from "framer-motion";
import { useRef } from "react";

export function Hero() {
  const reduce = useReducedMotion();
  const ref = useRef<HTMLElement>(null);
  const { scrollYProgress } = useScroll({
    target: ref,
    offset: ["start start", "end start"],
  });
  const y = useTransform(scrollYProgress, [0, 1], ["0%", "28%"]);
  const opacity = useTransform(scrollYProgress, [0, 0.85], [1, 0.35]);

  return (
    <section
      id="top"
      ref={ref}
      className="relative isolate min-h-[100svh] overflow-hidden"
    >
      <motion.div
        style={reduce ? undefined : { y, opacity }}
        className="absolute inset-0 -z-20"
      >
        <motion.div
          initial={reduce ? false : { scale: 1.18 }}
          animate={{ scale: 1 }}
          transition={{ duration: 10, ease: [0.22, 1, 0.36, 1] }}
          className="absolute inset-0"
        >
          <Image
            src="/images/pcncloud-hotel-room.jpg"
            alt=""
            fill
            priority
            className="object-cover object-[center_32%]"
            sizes="100vw"
          />
        </motion.div>
        <div className="absolute inset-0 veil" />
        <div className="absolute inset-0 film-grain" aria-hidden />
      </motion.div>

      <div className="relative flex min-h-[100svh] flex-col justify-end px-4 pb-16 pt-28 sm:px-6 md:justify-center md:pb-24 md:pt-24 lg:px-8">
        <div className="mx-auto w-full max-w-6xl">
          <motion.p
            initial={reduce ? false : { opacity: 0, y: 18 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6 }}
            className="font-display text-[clamp(3.4rem,14vw,9.5rem)] font-semibold leading-[0.86] tracking-[-0.04em] text-bone"
          >
            PCN Cloud
          </motion.p>

          <div className="mt-8 max-w-2xl md:mt-10">
            <motion.h1
              initial={reduce ? false : { opacity: 0, y: 22 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.7, delay: 0.08 }}
              className="font-display text-2xl font-medium leading-tight text-bone/95 sm:text-3xl md:text-4xl"
            >
              One stack for every room — screens, Wi‑Fi, security, and care.
            </motion.h1>

            <motion.p
              initial={reduce ? false : { opacity: 0, y: 18 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.65, delay: 0.16 }}
              className="mt-5 max-w-lg text-base leading-relaxed text-bone/70 sm:text-lg"
            >
              Hotels stop juggling vendors. Guests feel a finished property. Ops get
              one partner that answers at midnight.
            </motion.p>

            <motion.div
              initial={reduce ? false : { opacity: 0, y: 14 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.6, delay: 0.24 }}
              className="mt-9 flex flex-wrap items-center gap-3"
            >
              <a href="#services" className="btn-primary">
                See the stack
              </a>
              <a href="#contact" className="btn-secondary">
                Book a walkthrough
              </a>
            </motion.div>
          </div>
        </div>
      </div>
    </section>
  );
}
