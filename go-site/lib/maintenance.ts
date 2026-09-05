/**
 * Flip to `false` (or set MAINTENANCE_MODE=false) to restore the full site.
 */
export const MAINTENANCE_ENABLED =
  process.env.MAINTENANCE_MODE !== "false" &&
  process.env.NEXT_PUBLIC_MAINTENANCE_MODE !== "false";

export const MAINTENANCE_EMAIL = "hello@pcncloud.in";
