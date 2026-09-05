"use client";

import { Reveal } from "./Reveal";

export function About() {
  return (
    <section id="about" className="relative bg-bone text-void">
      <div className="section-pad">
        <div className="grid gap-10 lg:grid-cols-[0.85fr_1.15fr] lg:gap-16">
          <Reveal>
            <p className="eyebrow-dark">About us</p>
            <h2 className="mt-4 font-display text-4xl font-semibold tracking-tight sm:text-5xl md:text-6xl">
              One accountable tech partner for hospitality.
            </h2>
          </Reveal>

          <Reveal delay={0.08}>
            <div className="border-t border-void/15 pt-8 text-base leading-relaxed text-void/70 sm:text-lg lg:border-l lg:border-t-0 lg:pl-10 lg:pt-1">
              <p>
                PCN Cloud was founded with a unified vision: to eliminate the IT and
                networking headaches of the hospitality industry. By combining strong
                strategic backing with deep, hands-on expertise in network architecture
                and IT infrastructure, we built a company that acts as your single,
                accountable tech partner. From the fiber connection coming into your
                property to the smart TV kiosks and RFID locks, our leadership ensures
                seamless deployment and 24/7 maintenance — so you can focus entirely on
                your guests.
              </p>
            </div>
          </Reveal>
        </div>
      </div>
    </section>
  );
}
