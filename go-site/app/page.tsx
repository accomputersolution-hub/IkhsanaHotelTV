import { Navbar } from "./components/Navbar";
import { Hero } from "./components/Hero";
import { Showcase } from "./components/Showcase";
import { Services } from "./components/Services";
import { OpexAdvantage } from "./components/OpexAdvantage";
import { TechStack } from "./components/TechStack";
import { Contact } from "./components/Contact";
import { Footer } from "./components/Footer";

export default function HomePage() {
  return (
    <>
      <Navbar />
      <main>
        <Hero />
        <Showcase />
        <Services />
        <OpexAdvantage />
        <TechStack />
        <Contact />
      </main>
      <Footer />
    </>
  );
}
