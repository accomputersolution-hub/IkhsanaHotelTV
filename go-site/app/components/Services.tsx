"use client";

import {
  Tv,
  Wifi,
  Cctv,
  Phone,
  KeyRound,
  ServerCog,
  type LucideIcon,
} from "lucide-react";
import { motion, useReducedMotion } from "framer-motion";
import { Reveal } from "./Reveal";

type Service = {
  title: string;
  summary: string;
  points: string[];
  icon: LucideIcon;
  accent: string;
};

const services: Service[] = [
  {
    title: "Smart Hotel TV Kiosk",
    summary:
      "Custom Android TV launcher with centralized remote management, zero-touch provisioning, and full hotel branding.",
    points: ["Branded guest home", "Remote fleet control", "Zero-touch deploy"],
    icon: Tv,
    accent: "from-neon-blue/30 to-transparent",
  },
  {
    title: "Enterprise Wi‑Fi & Networking",
    summary:
      "AP and gateway install, configuration, and proactive maintenance on an affordable month-to-month rental model.",
    points: ["Monthly OpEx rental", "AP + gateway setup", "Proactive upkeep"],
    icon: Wifi,
    accent: "from-neon-cyan/30 to-transparent",
  },
  {
    title: "Security & Surveillance",
    summary:
      "HD IP CCTV installation, NVR cloud management, and secure remote monitoring for lobbies, floors, and perimeters.",
    points: ["HD IP cameras", "NVR / cloud view", "Remote monitoring"],
    icon: Cctv,
    accent: "from-neon-violet/30 to-transparent",
  },
  {
    title: "Hotel Communications",
    summary:
      "Intercom and EPABX / IP PBX systems for seamless room-to-reception and internal staff connectivity.",
    points: ["IP PBX / EPABX", "Room intercom", "Staff extensions"],
    icon: Phone,
    accent: "from-neon-purple/30 to-transparent",
  },
  {
    title: "Smart Access & Guest Automation",
    summary:
      "RFID smart door locks and guest Wi‑Fi captive portals with integrated bandwidth management.",
    points: ["RFID door locks", "Captive portal Wi‑Fi", "Bandwidth control"],
    icon: KeyRound,
    accent: "from-neon-blue/25 to-transparent",
  },
  {
    title: "IT & Server Maintenance",
    summary:
      "End-to-end hardware support, local server management, and continuous 24/7 IT infrastructure care.",
    points: ["Hardware support", "Local servers", "24/7 monitoring"],
    icon: ServerCog,
    accent: "from-neon-cyan/25 to-transparent",
  },
];

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
          maintains the stack hotels and enterprises actually rely on.
        </p>
      </Reveal>

      <div className="mt-12 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
        {services.map((service, i) => {
          const Icon = service.icon;
          return (
            <motion.article
              key={service.title}
              initial={reduce ? false : { opacity: 0, y: 28 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true, margin: "-8%" }}
              transition={{ duration: 0.5, delay: i * 0.06 }}
              whileHover={reduce ? undefined : { y: -6, scale: 1.01 }}
              className="group relative overflow-hidden rounded-3xl border border-white/10 bg-white/[0.04] p-6 shadow-glass backdrop-blur-xl transition hover:border-neon-blue/40 hover:shadow-glow"
            >
              <div
                className={`pointer-events-none absolute inset-0 bg-gradient-to-br ${service.accent} opacity-0 transition group-hover:opacity-100`}
              />
              <div className="relative">
                <div className="mb-5 inline-flex h-12 w-12 items-center justify-center rounded-2xl border border-white/10 bg-ink-800 text-neon-cyan shadow-glow">
                  <Icon className="h-6 w-6" aria-hidden />
                </div>
                <h3 className="font-display text-xl font-semibold text-white">
                  {service.title}
                </h3>
                <p className="mt-3 text-sm leading-relaxed text-slate-300">
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
            </motion.article>
          );
        })}
      </div>
    </section>
  );
}
