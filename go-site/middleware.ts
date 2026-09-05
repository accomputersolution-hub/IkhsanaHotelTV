import { NextRequest, NextResponse } from "next/server";
import {
  MAINTENANCE_ENABLED,
  PREVIEW_COOKIE,
  PREVIEW_SECRET,
} from "@/lib/maintenance";

function isStaticOrInternal(pathname: string) {
  return (
    pathname.startsWith("/_next") ||
    pathname.startsWith("/maintenance") ||
    pathname === "/favicon.ico" ||
    pathname === "/robots.txt" ||
    /\.[a-zA-Z0-9]+$/.test(pathname)
  );
}

function withPreviewCookie(response: NextResponse) {
  response.cookies.set(PREVIEW_COOKIE, "1", {
    path: "/",
    httpOnly: true,
    sameSite: "lax",
    secure: true,
    maxAge: 60 * 60 * 24 * 14, // 14 days
  });
  return response;
}

export function middleware(request: NextRequest) {
  if (!MAINTENANCE_ENABLED) {
    return NextResponse.next();
  }

  const { pathname, searchParams } = request.nextUrl;
  const hasPreviewCookie = request.cookies.get(PREVIEW_COOKIE)?.value === "1";

  // Unlock link: /preview/pcn-verify-2026  (or ?preview=pcn-verify-2026)
  const previewPath = `/preview/${PREVIEW_SECRET}`;
  const previewQuery = searchParams.get("preview");
  const isUnlock =
    pathname === previewPath ||
    pathname === `/preview/${PREVIEW_SECRET}/` ||
    previewQuery === PREVIEW_SECRET;

  if (isUnlock) {
    const url = request.nextUrl.clone();
    url.pathname = "/";
    url.search = "";
    return withPreviewCookie(NextResponse.redirect(url));
  }

  if (hasPreviewCookie || isStaticOrInternal(pathname)) {
    return NextResponse.next();
  }

  const url = request.nextUrl.clone();
  url.pathname = "/maintenance";
  url.search = "";
  return NextResponse.rewrite(url);
}

export const config = {
  matcher: ["/((?!_next/static|_next/image).*)"],
};
