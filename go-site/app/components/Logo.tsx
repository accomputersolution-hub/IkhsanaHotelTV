import Image from "next/image";
import Link from "next/link";

type LogoProps = {
  size?: "sm" | "md" | "lg";
  showWordmark?: boolean;
  className?: string;
  priority?: boolean;
};

const sizes = {
  sm: { px: 48, className: "h-12 w-12" },
  md: { px: 64, className: "h-16 w-16" },
  lg: { px: 160, className: "h-40 w-40" },
};

export function Logo({
  size = "sm",
  showWordmark = false,
  className = "",
  priority = false,
}: LogoProps) {
  const s = sizes[size];

  return (
    <Link
      href="/"
      className={`inline-flex items-center gap-3 ${className}`}
      aria-label="PCN Cloud home"
    >
      <span className={`relative shrink-0 overflow-hidden rounded-full ${s.className}`}>
        <Image
          src="/images/pcncloud-logo.jpg"
          alt="PCN Cloud"
          width={s.px}
          height={s.px}
          priority={priority}
          className="h-full w-full object-cover"
        />
      </span>
      {showWordmark && (
        <span className="font-display text-xl font-semibold tracking-tight text-bone sm:text-2xl">
          PCN Cloud
        </span>
      )}
    </Link>
  );
}
