/**
 * Flip to `false` (or set MAINTENANCE_MODE=false) to restore the full site publicly.
 */
export const MAINTENANCE_ENABLED =
  process.env.MAINTENANCE_MODE !== "false" &&
  process.env.NEXT_PUBLIC_MAINTENANCE_MODE !== "false";

export const MAINTENANCE_EMAIL = "hello@pcncloud.in";

/** Secret path segment for private preview while maintenance stays on. */
export const PREVIEW_SECRET =
  process.env.PREVIEW_SECRET?.trim() || "pcn-verify-2026";

export const PREVIEW_COOKIE = "pcn_site_preview";
