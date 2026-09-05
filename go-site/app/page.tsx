import { Navbar } from "./components/Navbar";
import { Hero } from "./components/Hero";
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
        <Services />
        <OpexAdvantage />
        <TechStack />
        <Contact />
      </main>
      <Footer />
    </>
  );
}
