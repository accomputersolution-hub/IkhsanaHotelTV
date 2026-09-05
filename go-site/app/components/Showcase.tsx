"use client";

import Image from "next/image";
import { motion, useReducedMotion, useScroll, useTransform } from "framer-motion";
import { useRef } from "react";
import { Reveal } from "./Reveal";

const chapters = [
  {
    src: "/images/pcncloud-hotel-room.jpg",
    n: "01",
    place: "Guest room",
    title: "The TV should feel like the hotel — not like an Android box.",
  },
  {
    src: "/images/pcncloud-corporate-lobby.jpg",
    n: "02",
    place: "Lobby & public",
    title: "First impression spaces that look intentional from the driveway in.",
  },
  {
    src: "/images/pcncloud-admin-control.jpg",
    n: "03",
    place: "Ops console",
    title: "One command plane for rooms, tickers, networks, and policy.",
  },
];

export function Showcase() {
  return (
    <section id="showcase" className="relative bg-void">
      <div className="section-pad !pb-10">
        <Reveal>
          <p className="eyebrow">Property atmosphere</p>
          <h2 className="mt-4 max-w-3xl font-display text-4xl font-semibold tracking-tight text-bone sm:text-5xl md:text-6xl">
            Built like a hotel brand campaign —
            <span className="text-brass-soft"> not an IT brochure.</span>
          </h2>
        </Reveal>
      </div>

      <div className="space-y-3 pb-8 md:space-y-4 md:pb-16">
        {chapters.map((c, i) => (
          <Chapter key={c.n} chapter={c} index={i} />
        ))}
      </div>
    </section>
  );
}

function Chapter({
  chapter,
  index,
}: {
  chapter: (typeof chapters)[number];
  index: number;
}) {
  const reduce = useReducedMotion();
  const ref = useRef<HTMLElement>(null);
  const { scrollYProgress } = useScroll({
    target: ref,
    offset: ["start end", "end start"],
  });
  const y = useTransform(scrollYProgress, [0, 1], reduce ? ["0%", "0%"] : ["-8%", "8%"]);

  return (
    <article
      ref={ref}
      className="relative mx-auto h-[58svh] max-w-[100rem] overflow-hidden sm:h-[70svh] md:h-[78svh]"
    >
      <motion.div style={{ y }} className="absolute inset-0 scale-110">
        <Image
          src={chapter.src}
          alt={chapter.title}
          fill
          className="object-cover"
          sizes="100vw"
          priority={index === 0}
        />
      </motion.div>
      <div className="absolute inset-0 bg-gradient-to-t from-void via-void/45 to-void/20" />
      <div className="absolute inset-0 film-grain" aria-hidden />

      <div className="absolute inset-x-0 bottom-0 p-5 sm:p-10 md:p-14">
        <Reveal delay={0.05}>
          <p className="text-[10px] font-semibold uppercase tracking-[0.24em] text-brass sm:text-[11px] sm:tracking-[0.28em]">
            {chapter.n} — {chapter.place}
          </p>
          <h3 className="mt-2 max-w-3xl text-balance font-display text-[1.55rem] font-semibold leading-tight text-bone sm:mt-3 sm:text-4xl md:text-5xl">
            {chapter.title}
          </h3>
        </Reveal>
      </div>
    </article>
  );
}
