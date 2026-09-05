import { Navbar } from "./components/Navbar";
import { Hero } from "./components/Hero";
import { Marquee } from "./components/Marquee";
import { Showcase } from "./components/Showcase";
import { PromiseStrip } from "./components/PromiseStrip";
import { Properties } from "./components/Properties";
import { Services } from "./components/Services";
import { OpexAdvantage } from "./components/OpexAdvantage";
import { TechStack } from "./components/TechStack";
import { Contact } from "./components/Contact";
import { Footer } from "./components/Footer";
import { WhatsAppFloat } from "./components/WhatsAppFloat";

export default function HomePage() {
  return (
    <>
      <Navbar />
      <main>
        <Hero />
        <Marquee />
        <Showcase />
        <PromiseStrip />
        <Properties />
        <Services />
        <OpexAdvantage />
        <TechStack />
        <Contact />
      </main>
      <Footer />
      <WhatsAppFloat />
    </>
  );
}
