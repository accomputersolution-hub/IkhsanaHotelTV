/**
 * WireGuard peer registration API.
 *
 * POST /api/add-peer
 *   body: { "publicKey": "<base64>" }
 *   → reads /etc/wireguard/wg0.conf, assigns next free 10.0.0.x,
 *     adds the peer, returns { clientIp, address, serverPublicKey }.
 *
 * Deploy on the VPN host (e.g. 103.29.99.61) as root / with sudo for
 * `wg` + write access to wg0.conf.
 */
const express = require("express");
const fs = require("fs");
const path = require("path");
const { execFileSync } = require("child_process");

const PORT = Number(process.env.PORT || 3000);
const WG_CONF = process.env.WG_CONF || "/etc/wireguard/wg0.conf";
const WG_INTERFACE = process.env.WG_INTERFACE || "wg0";
const IP_PREFIX = "10.0.0.";
/** First usable client host octet (.1 is typically the server Address). */
const MIN_CLIENT_HOST = Number(process.env.WG_MIN_CLIENT_HOST || 2);
const MAX_CLIENT_HOST = Number(process.env.WG_MAX_CLIENT_HOST || 254);
/** DNS pushed to Android clients for full-tunnel name resolution. */
const CLIENT_DNS = process.env.WG_CLIENT_DNS || "8.8.8.8, 8.8.4.4";

const app = express();
app.use(express.json({ limit: "32kb" }));

function readConf() {
  return fs.readFileSync(WG_CONF, "utf8");
}

function extractHostOctets(confText) {
  const octets = new Set();
  const re = /\b10\.0\.0\.(\d{1,3})\b/g;
  let match;
  while ((match = re.exec(confText)) !== null) {
    const n = Number(match[1]);
    if (Number.isInteger(n) && n >= 0 && n <= 255) {
      octets.add(n);
    }
  }
  return octets;
}

/**
 * Highest assigned 10.0.0.x in the conf, then +1 (clamped to client range).
 * Skips holes only by taking max+1 — matches the requested behavior.
 */
function nextClientIp(confText) {
  const used = extractHostOctets(confText);
  let highest = MIN_CLIENT_HOST - 1;
  for (const n of used) {
    if (n > highest) highest = n;
  }
  const next = highest + 1;
  if (next < MIN_CLIENT_HOST) {
    return `${IP_PREFIX}${MIN_CLIENT_HOST}`;
  }
  if (next > MAX_CLIENT_HOST) {
    throw new Error(
      `No free IPs left in ${IP_PREFIX}${MIN_CLIENT_HOST}-${MAX_CLIENT_HOST}`,
    );
  }
  return `${IP_PREFIX}${next}`;
}

function findPeerAllowedIp(confText, publicKey) {
  const blocks = confText.split(/\[Peer\]/i).slice(1);
  for (const block of blocks) {
    const keyMatch = block.match(/PublicKey\s*=\s*([A-Za-z0-9+/=]+)/);
    if (!keyMatch || keyMatch[1].trim() !== publicKey) continue;
    const ipMatch = block.match(/AllowedIPs\s*=\s*([0-9./,\s]+)/i);
    if (!ipMatch) return null;
    const first = ipMatch[1].split(",")[0].trim();
    const bare = first.replace(/\/\d+$/, "");
    if (/^10\.0\.0\.\d{1,3}$/.test(bare)) return bare;
    return null;
  }
  return null;
}

function readServerPublicKey() {
  if (process.env.WG_SERVER_PUBLIC_KEY) {
    return process.env.WG_SERVER_PUBLIC_KEY.trim();
  }
  try {
    return execFileSync("wg", ["show", WG_INTERFACE, "public-key"], {
      encoding: "utf8",
    }).trim();
  } catch {
    try {
      const conf = readConf();
      const m = conf.match(/\[Interface\][\s\S]*?PrivateKey\s*=\s*([A-Za-z0-9+/=]+)/);
      if (!m) return null;
      return execFileSync("wg", ["pubkey"], {
        input: m[1].trim() + "\n",
        encoding: "utf8",
      }).trim();
    } catch {
      return null;
    }
  }
}

function appendPeerToConf(publicKey, clientIp) {
  const stanza =
    `\n# added by api/add-peer ${new Date().toISOString()}\n` +
    `[Peer]\n` +
    `PublicKey = ${publicKey}\n` +
    `AllowedIPs = ${clientIp}/32\n`;
  fs.appendFileSync(WG_CONF, stanza, "utf8");
}

function applyPeerLive(publicKey, clientIp) {
  try {
    execFileSync(
      "wg",
      ["set", WG_INTERFACE, "peer", publicKey, "allowed-ips", `${clientIp}/32`],
      { stdio: ["ignore", "pipe", "pipe"] },
    );
  } catch (err) {
    // Conf was written; live apply may fail if interface is down — still OK.
    console.warn("wg set failed (peer still in conf):", err.message);
  }
}

app.get("/health", (_req, res) => {
  res.json({ ok: true, conf: WG_CONF, interface: WG_INTERFACE });
});

app.post("/api/add-peer", (req, res) => {
  try {
    const publicKey =
      typeof req.body?.publicKey === "string" ? req.body.publicKey.trim() : "";
    if (!publicKey || publicKey.length < 40) {
      return res.status(400).json({
        success: false,
        message: "publicKey is required (WireGuard base64 public key)",
      });
    }

    if (!fs.existsSync(WG_CONF)) {
      return res.status(500).json({
        success: false,
        message: `WireGuard conf not found: ${WG_CONF}`,
      });
    }

    // Serialize against concurrent registrations (simple process-local lock).
    if (app.locals.addingPeer) {
      return res.status(409).json({
        success: false,
        message: "Another peer registration is in progress — retry",
      });
    }
    app.locals.addingPeer = true;

    try {
      const conf = readConf();

      // Idempotent: same public key → reuse existing AllowedIPs.
      const existingIp = findPeerAllowedIp(conf, publicKey);
      if (existingIp) {
        const serverPublicKey = readServerPublicKey();
        return res.status(200).json({
          success: true,
          clientIp: existingIp,
          address: `${existingIp}/32`,
          dns: CLIENT_DNS,
          serverPublicKey,
          message: "Peer already present — reusing assigned IP",
        });
      }

      // Ignore client-supplied clientIp to prevent conflicts; always allocate.
      const clientIp = nextClientIp(conf);
      appendPeerToConf(publicKey, clientIp);
      applyPeerLive(publicKey, clientIp);

      const serverPublicKey = readServerPublicKey();
      console.log(
        `add-peer ok publicKey=${publicKey.slice(0, 8)}… clientIp=${clientIp}`,
      );

      return res.status(200).json({
        success: true,
        clientIp,
        address: `${clientIp}/32`,
        dns: CLIENT_DNS,
        serverPublicKey,
        message: `Assigned ${clientIp}/32`,
      });
    } finally {
      app.locals.addingPeer = false;
    }
  } catch (err) {
    console.error("add-peer error:", err);
    return res.status(500).json({
      success: false,
      message: err.message || "Internal error",
    });
  }
});

if (require.main === module) {
  app.listen(PORT, "0.0.0.0", () => {
    console.log(
      `WireGuard peer API listening on :${PORT} conf=${WG_CONF} if=${WG_INTERFACE}`,
    );
  });
}

module.exports = {
  app,
  nextClientIp,
  extractHostOctets,
  findPeerAllowedIp,
};
