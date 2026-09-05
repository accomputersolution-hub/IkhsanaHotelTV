import {
  Tv,
  Wifi,
  Cctv,
  Phone,
  KeyRound,
  ServerCog,
  type LucideIcon,
} from "lucide-react";
import type { ServiceIconKey } from "@/lib/services";

export const serviceIcons: Record<ServiceIconKey, LucideIcon> = {
  tv: Tv,
  wifi: Wifi,
  cctv: Cctv,
  phone: Phone,
  key: KeyRound,
  server: ServerCog,
};
