import { WHATSAPP_URL } from "@/lib/contact";

/** Simple WhatsApp glyph — avoids pulling an extra icon dependency. */
function WhatsAppIcon({ className = "h-6 w-6" }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" className={className} aria-hidden>
      <path d="M20.52 3.48A11.78 11.78 0 0 0 12.04 0C5.5 0 .16 5.33.16 11.86c0 2.09.55 4.13 1.6 5.93L0 24l6.4-1.67a11.9 11.9 0 0 0 5.64 1.43h.01c6.53 0 11.86-5.33 11.86-11.86 0-3.17-1.23-6.15-3.39-8.42ZM12.05 21.15h-.01a9.27 9.27 0 0 1-4.72-1.29l-.34-.2-3.8.99 1.02-3.7-.22-.36a9.28 9.28 0 0 1-1.42-4.93c0-5.13 4.18-9.3 9.32-9.3a9.25 9.25 0 0 1 6.58 2.73 9.25 9.25 0 0 1 2.73 6.58c0 5.13-4.18 9.3-9.14 9.3Zm5.1-6.96c-.28-.14-1.65-.81-1.9-.9-.26-.1-.45-.14-.63.14-.19.27-.72.9-.88 1.08-.16.19-.33.21-.6.07-.28-.14-1.17-.43-2.23-1.37-.82-.73-1.38-1.64-1.54-1.91-.16-.28-.02-.43.12-.57.13-.12.28-.33.42-.5.14-.16.19-.28.28-.47.1-.19.05-.35-.02-.5-.07-.14-.63-1.52-.86-2.08-.23-.55-.46-.47-.63-.48h-.54c-.19 0-.5.07-.76.35-.26.28-1 1-1 2.43s1.02 2.82 1.17 3.01c.14.19 2 3.05 4.85 4.28.68.29 1.2.46 1.62.59.68.22 1.3.19 1.79.11.55-.08 1.65-.67 1.88-1.32.23-.65.23-1.2.16-1.32-.07-.11-.26-.18-.54-.32Z" />
    </svg>
  );
}

export function WhatsAppFloat() {
  return (
    <a
      href={WHATSAPP_URL}
      target="_blank"
      rel="noopener noreferrer"
      aria-label="Chat on WhatsApp"
      className="fixed z-[60] inline-flex h-12 w-12 items-center justify-center rounded-full bg-[#25D366] text-white shadow-[0_12px_40px_rgba(0,0,0,0.45)] transition hover:scale-105 hover:brightness-110 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#25D366] sm:h-14 sm:w-14"
      style={{
        bottom: "max(1.5rem, calc(env(safe-area-inset-bottom) + 0.5rem))",
        right: "max(1.25rem, env(safe-area-inset-right))",
      }}
    >
      <WhatsAppIcon />
    </a>
  );
}

export { WhatsAppIcon };
