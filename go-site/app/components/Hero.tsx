"use client";

import Image from "next/image";
import { motion, useReducedMotion, useScroll, useTransform } from "framer-motion";
import { useRef } from "react";
import { WHATSAPP_URL } from "@/lib/contact";
import { WhatsAppIcon } from "./WhatsAppFloat";

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
            className="object-cover object-[center_28%] sm:object-[center_32%]"
            sizes="100vw"
          />
        </motion.div>
        <div className="absolute inset-0 veil" />
        <div className="absolute inset-0 film-grain" aria-hidden />
      </motion.div>

      <div className="relative flex min-h-[100svh] flex-col justify-end px-4 pb-[max(4.5rem,calc(1.5rem+env(safe-area-inset-bottom)))] pt-24 sm:px-6 sm:pb-16 md:justify-center md:pb-24 md:pt-24 lg:px-8">
        <div className="mx-auto w-full max-w-6xl">
          <motion.p
            initial={reduce ? false : { opacity: 0, y: 18 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6 }}
            className="font-display text-[clamp(2.75rem,13vw,9.5rem)] font-semibold leading-[0.88] tracking-[-0.04em] text-bone"
          >
            PCN Cloud
          </motion.p>

          <div className="mt-6 max-w-2xl sm:mt-8 md:mt-10">
            <motion.h1
              initial={reduce ? false : { opacity: 0, y: 22 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.7, delay: 0.08 }}
              className="max-w-[18ch] text-balance font-display text-[1.35rem] font-medium leading-snug text-bone/95 sm:max-w-none sm:text-3xl md:text-4xl"
            >
              One stack for every room — screens, Wi‑Fi, security, and care.
            </motion.h1>

            <motion.p
              initial={reduce ? false : { opacity: 0, y: 18 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.65, delay: 0.16 }}
              className="mt-4 max-w-lg text-[0.95rem] leading-relaxed text-bone/70 sm:mt-5 sm:text-lg"
            >
              Hotels stop juggling vendors. Guests feel a finished property. Ops get
              one partner that answers at midnight.
            </motion.p>

            <motion.div
              initial={reduce ? false : { opacity: 0, y: 14 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.6, delay: 0.24 }}
              className="mt-7 flex w-full max-w-md flex-col gap-3 sm:mt-9 sm:max-w-none sm:flex-row sm:flex-wrap sm:items-center"
            >
              <a href="#services" className="btn-primary">
                See the stack
              </a>
              <a
                href={WHATSAPP_URL}
                target="_blank"
                rel="noopener noreferrer"
                className="btn-secondary"
              >
                <WhatsAppIcon className="h-4 w-4" />
                WhatsApp us
              </a>
            </motion.div>
          </div>
        </div>
      </div>
    </section>
  );
}
