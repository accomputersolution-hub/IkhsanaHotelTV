export type ServiceIconKey =
  | "tv"
  | "wifi"
  | "cctv"
  | "phone"
  | "key"
  | "server";

export type ServiceFeature = {
  title: string;
  description: string;
  icon: ServiceIconKey;
};

export type ServiceProcessStep = {
  title: string;
  description: string;
};

export type Service = {
  slug: string;
  title: string;
  shortTitle: string;
  summary: string;
  heroDescription: string;
  points: string[];
  accent: string;
  icon: ServiceIconKey;
  idealFor: string[];
  included: string[];
  useCases: string[];
  process: ServiceProcessStep[];
  features: ServiceFeature[];
  opexNote: string;
};

export const services: Service[] = [
  {
    slug: "smart-tv-kiosk",
    title: "Smart Hotel TV Kiosk System",
    shortTitle: "Smart Hotel TV Kiosk",
    summary:
      "Custom Android TV launcher with centralized remote management, zero-touch provisioning, and full hotel branding.",
    heroDescription:
      "Turn every guest-room and boardroom display into a locked, branded experience. PCN Cloud’s Android TV kiosk delivers Live TV, apps, announcements, and a cloud-managed home screen — with zero-touch provisioning and fleet control from a single admin panel.",
    points: [
      "Branded guest home screen",
      "Remote fleet control",
      "Zero-touch provisioning",
      "Live TV + approved apps",
      "Announcements & tickers",
    ],
    accent: "from-neon-blue/30 to-transparent",
    icon: "tv",
    idealFor: [
      "Hotels & resorts (guest rooms)",
      "Boutique properties needing brand consistency",
      "Corporate lobbies & boardrooms",
      "Multi-property groups with central ops",
    ],
    included: [
      "Android TV / STB kiosk app with locked launcher",
      "Hotel branding (logo, colors, welcome copy)",
      "Cloud admin panel access for fleet management",
      "Zero-touch / scripted device enrollment",
      "Live TV path setup (WireGuard-secured where required)",
      "Ticker / announcement channel configuration",
      "Approved guest app allowlist",
      "Onboarding training for front-office / IT staff",
      "Ongoing content & config push support",
    ],
    useCases: [
      "Replace messy Android home screens with a locked hotel UI",
      "Push festival offers or event tickers to all rooms in minutes",
      "Standardize corporate welcome screens across campuses",
      "Reduce truck rolls when opening a new wing or floor",
    ],
    process: [
      {
        title: "Discovery & branding",
        description:
          "We map room counts, TV models, Live TV needs, and brand assets — then define your launcher layout.",
      },
      {
        title: "Pilot rooms",
        description:
          "A small fleet is provisioned first so GM and ops can approve UX before full rollout.",
      },
      {
        title: "Fleet rollout",
        description:
          "Remaining rooms enroll via zero-touch / scripted setup and lock into kiosk mode under your tenant.",
      },
      {
        title: "Operate & update",
        description:
          "You manage tickers, branding, and policies from the cloud; we support upgrades and troubleshooting.",
      },
    ],
    features: [
      {
        icon: "tv",
        title: "Custom branded launcher",
        description:
          "Hotel logo, welcome messaging, and curated apps that feel like your property — not a generic Android home.",
      },
      {
        icon: "server",
        title: "Centralized remote management",
        description:
          "Push configs, tickers, and content updates across rooms from admin.pcncloud.in without visiting each TV.",
      },
      {
        icon: "key",
        title: "Zero-touch provisioning",
        description:
          "New units enroll and lock into kiosk mode with your tenant profile — faster rollouts, fewer truck rolls.",
      },
      {
        icon: "wifi",
        title: "Live TV & guest apps",
        description:
          "WireGuard-secured Live TV paths and approved app experiences that stay inside your kiosk policy.",
      },
      {
        icon: "cctv",
        title: "Announcements & tickers",
        description:
          "Property-wide or room-group messaging for events, offers, and operational notices.",
      },
      {
        icon: "phone",
        title: "Enterprise & hotel modes",
        description:
          "Hospitality guest UX or corporate lobby/boardroom layouts — same platform, tuned per property type.",
      },
    ],
    opexNote:
      "TV kiosk hardware and ongoing management can sit on a predictable monthly program — avoid a large CapEx spike when refreshing guest-room fleets.",
  },
  {
    slug: "enterprise-wifi",
    title: "Enterprise Wi‑Fi & Networking",
    shortTitle: "Enterprise Wi‑Fi & Networking",
    summary:
      "AP and gateway install, configuration, and proactive maintenance on an affordable month-to-month rental model.",
    heroDescription:
      "Reliable guest and staff Wi‑Fi without buying and depreciating every AP yourself. PCN Cloud installs, configures, and maintains access points and gateways on a month-to-month rental model — so coverage and uptime stay our problem, not yours.",
    points: [
      "Month-to-month AP rental",
      "Gateway + SSID design",
      "Guest / staff segmentation",
      "Proactive monitoring",
      "Free hardware replacements",
    ],
    accent: "from-neon-cyan/30 to-transparent",
    icon: "wifi",
    idealFor: [
      "Hotels opening or renovating wings",
      "Resorts with outdoor / villa coverage needs",
      "Properties tired of CapEx Wi‑Fi refresh cycles",
      "Groups that want one network SLA across sites",
    ],
    included: [
      "Site survey / heat-map informed AP placement",
      "Enterprise APs and gateway on monthly rental",
      "Guest, staff, and IoT SSID / VLAN design",
      "Captive-portal ready network handoff",
      "Firewall / routing basics for hotel traffic",
      "Proactive health monitoring",
      "Firmware & configuration hygiene",
      "Failed AP / gateway replacement under rental",
      "Priority support for outage escalations",
    ],
    useCases: [
      "Launch a new property without a huge networking CapEx bill",
      "Fix dead zones in banquet halls and pool decks",
      "Separate guest streaming from PMS / POS staff traffic",
      "Keep Wi‑Fi current without buying hardware every 3–5 years",
    ],
    process: [
      {
        title: "Survey & design",
        description:
          "We assess coverage gaps, room density, and backhaul — then propose AP count and SSID layout.",
      },
      {
        title: "Install & configure",
        description:
          "APs and gateway go live with guest/staff segmentation, QoS, and captive-portal readiness.",
      },
      {
        title: "Validate & train",
        description:
          "Walkthrough with ops; confirm roaming, speed, and staff network access before handover.",
      },
      {
        title: "Monitor & replace",
        description:
          "Ongoing monitoring plus rental replacements when hardware fails — no spare-stock headache.",
      },
    ],
    features: [
      {
        icon: "wifi",
        title: "AP & gateway deployment",
        description:
          "Site survey–informed placement, SSID design for guest/staff, and gateway setup tuned for hospitality traffic.",
      },
      {
        icon: "server",
        title: "Month-to-month rental",
        description:
          "Shift networking from CapEx to OpEx with clear monthly pricing and included maintenance.",
      },
      {
        icon: "cctv",
        title: "Proactive monitoring",
        description:
          "Health checks and configuration hygiene so dead APs and misconfigs are caught before guests complain.",
      },
      {
        icon: "key",
        title: "Hardware replacements",
        description:
          "Covered rental endpoints that fail get replaced — you are not stuck buying spare stock.",
      },
      {
        icon: "phone",
        title: "Guest / staff segmentation",
        description:
          "Clean separation of guest, staff, and IoT traffic with policies that match how hotels actually operate.",
      },
      {
        icon: "tv",
        title: "Ready for TV & IoT",
        description:
          "Designed to coexist with Smart TV kiosks, CCTV, and lock systems on the same property network.",
      },
    ],
    opexNote:
      "This is our flagship OpEx offering: rent enterprise Wi‑Fi hardware monthly with free replacements and continuous maintenance — zero heavy upfront cost.",
  },
  {
    slug: "security-surveillance",
    title: "Advanced Security & Surveillance",
    shortTitle: "Security & Surveillance",
    summary:
      "HD IP CCTV installation, NVR cloud management, and secure remote monitoring for lobbies, floors, and perimeters.",
    heroDescription:
      "See lobbies, corridors, parking, and back-of-house with confidence. PCN Cloud designs and installs HD IP CCTV with NVR and secure remote viewing — so owners and GMs can monitor properties without juggling another siloed security vendor.",
    points: [
      "HD IP camera coverage",
      "NVR retention design",
      "Secure remote viewing",
      "Role-based access",
      "Multi-property ready",
    ],
    accent: "from-neon-violet/30 to-transparent",
    icon: "cctv",
    idealFor: [
      "Hotels needing lobby / corridor coverage",
      "Resorts with parking & perimeter risks",
      "Owners managing multiple properties remotely",
      "Properties upgrading from old analog DVRs",
    ],
    included: [
      "Camera placement plan (lobby, floors, perimeter, critical areas)",
      "HD IP cameras suited for day/night hospitality use",
      "NVR sizing for your retention policy",
      "Secure remote viewing for authorized users",
      "Role-based live-view vs export permissions",
      "Network / PoE planning with your IT stack",
      "UPS guidance for recording continuity",
      "Handover training for security & GM teams",
      "Optional rental / maintenance packaging",
    ],
    useCases: [
      "Investigate lobby incidents with clear HD footage",
      "Monitor parking and perimeter after dark",
      "Let ownership review sites remotely without VPN chaos",
      "Replace aging DVRs with IP + retention that auditors expect",
    ],
    process: [
      {
        title: "Risk & coverage walkthrough",
        description:
          "We tour the property to mark blind spots, critical assets, and retention needs.",
      },
      {
        title: "Design & BOM",
        description:
          "Camera types, NVR capacity, storage days, and network drops are locked before install.",
      },
      {
        title: "Install & harden",
        description:
          "Mounting, PoE, recording, and encrypted remote access — cameras are not left open to the internet.",
      },
      {
        title: "Handover & monitor",
        description:
          "Ops learns live-view and export workflows; we stay on for health checks and expansions.",
      },
    ],
    features: [
      {
        icon: "cctv",
        title: "HD IP camera systems",
        description:
          "Lobby, floor, perimeter, and critical-area coverage with cameras chosen for night and high-traffic use.",
      },
      {
        icon: "server",
        title: "NVR & retention design",
        description:
          "On-prem NVR sizing and retention policies aligned with how long you need footage for incidents and audits.",
      },
      {
        icon: "wifi",
        title: "Secure remote monitoring",
        description:
          "Encrypted remote access for authorized staff — view sites without exposing cameras to the open internet.",
      },
      {
        icon: "key",
        title: "Access-controlled viewing",
        description:
          "Role-aware who can live-view vs export — protect guest privacy and operational integrity.",
      },
      {
        icon: "phone",
        title: "Multi-property oversight",
        description:
          "Owners with multiple hotels get a consistent monitoring pattern instead of one-off DVR setups per site.",
      },
      {
        icon: "tv",
        title: "Integrated with IT stack",
        description:
          "Cameras and NVRs planned alongside Wi‑Fi, VLAN, and UPS so surveillance does not fight the rest of the network.",
      },
    ],
    opexNote:
      "Cameras, NVRs, and monitoring support can be structured on rental/maintenance terms — spread cost monthly instead of one large security CapEx.",
  },
  {
    slug: "hotel-communications",
    title: "Complete Hotel Communications",
    shortTitle: "Hotel Communications",
    summary:
      "Intercom and EPABX / IP PBX systems for seamless room-to-reception and internal staff connectivity.",
    heroDescription:
      "Room-to-reception and staff-to-staff calling that just works. PCN Cloud deploys intercom and EPABX / IP PBX systems so guests reach the front desk instantly and teams coordinate without consumer WhatsApp chaos.",
    points: [
      "IP PBX / EPABX core",
      "Room-to-reception intercom",
      "Department hunt groups",
      "Night mode routing",
      "QoS for clear voice",
    ],
    accent: "from-neon-purple/30 to-transparent",
    icon: "phone",
    idealFor: [
      "Hotels replacing aging EPABX boards",
      "Properties wanting room phones that actually reach reception",
      "Teams drowning in WhatsApp for internal ops",
      "Resorts needing wing / villa extension dialing",
    ],
    included: [
      "IP PBX / EPABX system design & dial plan",
      "Reception, F&B, housekeeping, and management extensions",
      "Room intercom / guest-to-desk calling",
      "Hunt groups and night/day routing",
      "Handsets / soft endpoints as scoped",
      "QoS & VLAN planning for voice clarity",
      "Integration guidance with existing trunks / SIP",
      "Staff training for console & transfer workflows",
      "Ongoing maintenance options",
    ],
    useCases: [
      "Guest dials 0 and always reaches a live desk or hunt group",
      "Housekeeping and engineering coordinate without personal phones",
      "Night audit routes calls correctly after front-desk shift change",
      "VIP arrivals trigger coordinated staff extensions",
    ],
    process: [
      {
        title: "Dial-plan workshop",
        description:
          "We map departments, room numbering, night modes, and who should answer what.",
      },
      {
        title: "Core & endpoint install",
        description:
          "PBX, handsets, and room endpoints go live with QoS so voice does not fight guest Wi‑Fi.",
      },
      {
        title: "Call-flow testing",
        description:
          "Hunt groups, transfers, and room-to-reception paths are tested with front-office staff.",
      },
      {
        title: "Stabilize & support",
        description:
          "Fine-tune routing after go-live; maintenance covers moves, adds, and changes.",
      },
    ],
    features: [
      {
        icon: "phone",
        title: "IP PBX / EPABX",
        description:
          "Modern IP telephony for reception, F&B, housekeeping, and management extensions in one dial plan.",
      },
      {
        icon: "tv",
        title: "Room intercom",
        description:
          "Guest-to-reception connectivity that feels native to the room experience — fast help, fewer front-desk walks.",
      },
      {
        icon: "wifi",
        title: "Network-aware design",
        description:
          "QoS and VLAN planning so voice stays clear even when guest Wi‑Fi is busy.",
      },
      {
        icon: "server",
        title: "Central numbering & hunt groups",
        description:
          "Hunt groups, night modes, and department routing that match how your property actually staffs shifts.",
      },
      {
        icon: "key",
        title: "Secure staff calling",
        description:
          "Internal extensions for ops without exposing guest rooms to arbitrary outside dialing policies.",
      },
      {
        icon: "cctv",
        title: "Ops + security handoff",
        description:
          "Communications that pair with surveillance and reception workflows during incidents or VIP arrivals.",
      },
    ],
    opexNote:
      "PBX hardware, handsets, and ongoing support can run on monthly maintenance/rental — keep CapEx free for guest-facing renovations.",
  },
  {
    slug: "smart-access",
    title: "Smart Access & Guest Automation",
    shortTitle: "Smart Access & Guest Automation",
    summary:
      "RFID smart door locks and guest Wi‑Fi captive portals with integrated bandwidth management.",
    heroDescription:
      "From the door lock to the first Wi‑Fi login, automate the guest journey. RFID smart locks plus captive portals with bandwidth control reduce front-desk friction and keep network abuse in check.",
    points: [
      "RFID smart door locks",
      "Branded captive portal",
      "Per-room bandwidth limits",
      "Staff access windows",
      "Audit-ready entry logs",
    ],
    accent: "from-neon-blue/25 to-transparent",
    icon: "key",
    idealFor: [
      "Hotels modernizing metal-key workflows",
      "Properties with Wi‑Fi abuse / fair-use issues",
      "Resorts needing amenity-area access control",
      "Groups wanting branded guest Wi‑Fi login",
    ],
    included: [
      "RFID lock recommendation & install scope",
      "Encoder / card or fob workflow for front desk",
      "Staff access profiles (housekeeping, engineering)",
      "Branded Wi‑Fi captive portal",
      "Room-number or voucher login flows",
      "Bandwidth / fair-use policy configuration",
      "Basic audit trail for door events",
      "Integration-minded design with PMS habits",
      "Training for front desk and engineering",
    ],
    useCases: [
      "Issue room cards at check-in without master-key chaos",
      "Let guests join Wi‑Fi via room number on a branded splash page",
      "Throttle abusive streaming so banquet Wi‑Fi stays usable",
      "Time-box housekeeping access without sharing metal masters",
    ],
    process: [
      {
        title: "Journey mapping",
        description:
          "We map check-in, amenity access, and Wi‑Fi login so locks and portal feel like one guest flow.",
      },
      {
        title: "Install & encode",
        description:
          "Locks, encoders, and portal go live; front desk practices card issue and void workflows.",
      },
      {
        title: "Policy tuning",
        description:
          "Bandwidth limits and staff access windows are adjusted after the first busy weekend.",
      },
      {
        title: "Operate",
        description:
          "You run day-to-day encoding and portal messaging; we support expansions and incidents.",
      },
    ],
    features: [
      {
        icon: "key",
        title: "RFID smart door locks",
        description:
          "Card/fob access for rooms and amenity areas with audit trails and faster check-in workflows.",
      },
      {
        icon: "wifi",
        title: "Guest captive portals",
        description:
          "Branded splash pages for Wi‑Fi login — room-number or voucher flows that match your PMS habits.",
      },
      {
        icon: "server",
        title: "Bandwidth management",
        description:
          "Fair-use and per-room limits so one heavy streamer does not kill the whole floor.",
      },
      {
        icon: "phone",
        title: "Staff access profiles",
        description:
          "Housekeeping and engineering access windows without handing out master metal keys for every shift.",
      },
      {
        icon: "cctv",
        title: "Entry + camera correlation",
        description:
          "Access events that complement lobby and corridor cameras for incident review.",
      },
      {
        icon: "tv",
        title: "Part of the guest stack",
        description:
          "Locks and portals designed to sit alongside TV kiosk and networking — one vendor accountable for the journey.",
      },
    ],
    opexNote:
      "Locks, controllers, and captive-portal infrastructure can be delivered with monthly support — upgrade access tech without a one-time cash crunch.",
  },
  {
    slug: "it-maintenance",
    title: "Complete IT & Server Maintenance",
    shortTitle: "IT & Server Maintenance",
    summary:
      "End-to-end hardware support, local server management, and continuous 24/7 IT infrastructure care.",
    heroDescription:
      "Hotels run on POS, PMS, printers, local servers, and a pile of “someone else’s problem” devices. PCN Cloud provides end-to-end hardware support and 24/7 infrastructure care so your GM is not the de facto IT desk.",
    points: [
      "24/7 escalation path",
      "PMS / local server care",
      "POS & printer support",
      "Patching & backups",
      "Single accountable partner",
    ],
    accent: "from-neon-cyan/25 to-transparent",
    icon: "server",
    idealFor: [
      "Hotels without a full-time IT team",
      "Properties with aging on-prem PMS servers",
      "Groups tired of multi-vendor finger-pointing",
      "Ops teams hit by checkout-hour printer failures",
    ],
    included: [
      "Named escalation path for critical outages",
      "Local server health, patching, and backup checks",
      "POS / printer / desktop break-fix support",
      "Switch / firewall hygiene alongside Wi‑Fi estate",
      "Credential & change discipline",
      "Asset awareness for critical hotel endpoints",
      "Preventive maintenance visits as scoped",
      "Coordination with PMS / POS vendors when needed",
      "Monthly care instead of surprise CapEx spikes",
    ],
    useCases: [
      "PMS server alerted and restored before breakfast rush",
      "Checkout printers fixed without GM hunting USB cables",
      "One partner accountable when TV, Wi‑Fi, and POS overlap",
      "Predictable IT spend instead of emergency hardware buys",
    ],
    process: [
      {
        title: "Infrastructure audit",
        description:
          "We inventory servers, critical PCs, printers, and network gear — and mark single points of failure.",
      },
      {
        title: "Stabilize baselines",
        description:
          "Backups, patch windows, and monitoring are set so “unknown” boxes become known-good.",
      },
      {
        title: "Operate on SLA",
        description:
          "Day-to-day tickets and critical calls route to PCN Cloud with clear severity response.",
      },
      {
        title: "Improve continuously",
        description:
          "Monthly reviews catch aging hardware before it fails on a sold-out weekend.",
      },
    ],
    features: [
      {
        icon: "server",
        title: "Local server management",
        description:
          "On-prem servers for PMS, file shares, and property apps — patching, backups, and health watched continuously.",
      },
      {
        icon: "wifi",
        title: "Network & endpoint hygiene",
        description:
          "Switches, firewalls, and critical endpoints kept in a known-good state alongside your rental Wi‑Fi estate.",
      },
      {
        icon: "phone",
        title: "24/7 escalation path",
        description:
          "When printers die at checkout or the PMS server hiccups at midnight, you have a single partner to call.",
      },
      {
        icon: "cctv",
        title: "Hardware lifecycle support",
        description:
          "Desktops, thin clients, and peripherals supported end-to-end — diagnose, replace, restore.",
      },
      {
        icon: "key",
        title: "Access & change discipline",
        description:
          "Controlled changes and credentials so vendor sprawl does not leave mystery admin accounts behind.",
      },
      {
        icon: "tv",
        title: "Unified with media & security",
        description:
          "IT that understands TV kiosks, CCTV, and locks — not a generic MSP who has never set foot in a hotel.",
      },
    ],
    opexNote:
      "Maintenance contracts convert unpredictable break/fix CapEx into a steady monthly OpEx line — with continuous care included.",
  },
];

export function getServiceBySlug(slug: string): Service | undefined {
  return services.find((s) => s.slug === slug);
}

export function getAllServiceSlugs(): string[] {
  return services.map((s) => s.slug);
}
