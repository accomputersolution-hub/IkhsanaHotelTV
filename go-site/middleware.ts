import { NextRequest, NextResponse } from "next/server";
import { MAINTENANCE_ENABLED } from "@/lib/maintenance";

export function middleware(request: NextRequest) {
  if (!MAINTENANCE_ENABLED) {
    return NextResponse.next();
  }

  const { pathname } = request.nextUrl;

  // Allow Next internals, static files, and the maintenance route itself.
  if (
    pathname.startsWith("/_next") ||
    pathname.startsWith("/maintenance") ||
    pathname === "/favicon.ico" ||
    pathname === "/robots.txt" ||
    /\.[a-zA-Z0-9]+$/.test(pathname)
  ) {
    return NextResponse.next();
  }

  const url = request.nextUrl.clone();
  url.pathname = "/maintenance";
  return NextResponse.rewrite(url);
}

export const config = {
  matcher: ["/((?!_next/static|_next/image).*)"],
};
