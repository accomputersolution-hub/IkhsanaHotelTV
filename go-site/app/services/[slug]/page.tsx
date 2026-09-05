import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { Navbar } from "@/app/components/Navbar";
import { Footer } from "@/app/components/Footer";
import { ServiceDetail } from "@/app/components/ServiceDetail";
import { WhatsAppFloat } from "@/app/components/WhatsAppFloat";
import { getAllServiceSlugs, getServiceBySlug } from "@/lib/services";

type PageProps = {
  params: Promise<{ slug: string }>;
};

export function generateStaticParams() {
  return getAllServiceSlugs().map((slug) => ({ slug }));
}

export async function generateMetadata({ params }: PageProps): Promise<Metadata> {
  const { slug } = await params;
  const service = getServiceBySlug(slug);
  if (!service) {
    return { title: "Service not found — PCN Cloud" };
  }
  return {
    title: `${service.title} — PCN Cloud`,
    description: service.summary,
    openGraph: {
      title: `${service.title} — PCN Cloud`,
      description: service.summary,
      url: `https://www.pcncloud.in/services/${service.slug}`,
    },
  };
}

export default async function ServicePage({ params }: PageProps) {
  const { slug } = await params;
  const service = getServiceBySlug(slug);
  if (!service) notFound();

  return (
    <>
      <Navbar />
      <ServiceDetail service={service} />
      <Footer />
      <WhatsAppFloat />
    </>
  );
}
