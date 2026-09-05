import { redirect } from "next/navigation";
import { PREVIEW_SECRET } from "@/lib/maintenance";

type Props = {
  params: Promise<{ secret: string }>;
};

/** Fallback page — middleware usually redirects unlocks before this renders. */
export default async function PreviewUnlockPage({ params }: Props) {
  const { secret } = await params;
  if (secret === PREVIEW_SECRET) {
    redirect("/");
  }
  redirect("/maintenance");
}
