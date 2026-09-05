import type { Metadata } from "next";
import { Fraunces, Outfit } from "next/font/google";
import "./globals.css";

const sans = Outfit({
  subsets: ["latin"],
  variable: "--font-sans",
  display: "swap",
});

const display = Fraunces({
  subsets: ["latin"],
  variable: "--font-display",
  display: "swap",
});

export const metadata: Metadata = {
  title: "PCN Cloud — Hotel IT, Smart TV & Security Partner",
  description:
    "PCN Cloud is the single-point vendor for hotel TV kiosks, enterprise Wi-Fi rental, CCTV, EPABX, smart locks, and 24/7 IT maintenance.",
  metadataBase: new URL("https://www.pcncloud.in"),
  icons: {
    icon: [{ url: "/images/pcncloud-logo.jpg", type: "image/jpeg" }],
    apple: [{ url: "/images/pcncloud-logo.jpg", type: "image/jpeg" }],
  },
  openGraph: {
    title: "PCN Cloud — Elevate Your Hotel's Tech & Security",
    description:
      "Zero hassle. Zero downtime. Smart Hotel TV, networking rental, CCTV, communications, and full IT support.",
    url: "https://www.pcncloud.in",
    siteName: "PCN Cloud",
    type: "website",
    images: [{ url: "/images/pcncloud-logo.jpg", width: 1254, height: 1254, alt: "PCN Cloud" }],
  },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" className={`${sans.variable} ${display.variable}`}>
      <body className="min-h-screen bg-void font-sans text-bone antialiased">
        {children}
      </body>
    </html>
  );
}
