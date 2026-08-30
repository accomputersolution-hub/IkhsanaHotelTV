/**
 * WireGuard peer registration API.
 *
 * POST /api/add-peer
 *   body: { "publicKey": "<base64>", "deviceId": "<ANDROID_ID or UUID>" }
 *   clientIp from the client is IGNORED
 *   → reads /etc/wireguard/wg0.conf, assigns or reuses 10.0.0.x by deviceId,
 *     adds/updates the peer, returns { clientIp, address, dns, serverPublicKey }.
 *
 * deviceId lets the server remember a TV after app reinstall (new keypair,
 * same ANDROID_ID). Mapping is stored in DEVICES_DB (JSON).
 */
const express = require("express");
const fs = require("fs");
const path = require("path");
const { execFileSync } = require("child_process");

const PORT = Number(process.env.PORT || 3001);
const WG_CONF = process.env.WG_CONF || "/etc/wireguard/wg0.conf";
const WG_INTERFACE = process.env.WG_INTERFACE || "wg0";
const DEVICES_DB =
  process.env.WG_DEVICES_DB ||
  path.join(path.dirname(WG_CONF), "peers-by-device.json");
const IP_PREFIX = "10.0.0.";
const MIN_CLIENT_HOST = Number(process.env.WG_MIN_CLIENT_HOST || 2);
const MAX_CLIENT_HOST = Number(process.env.WG_MAX_CLIENT_HOST || 254);
const CLIENT_DNS = process.env.WG_CLIENT_DNS || "8.8.8.8, 8.8.4.4";
const CLIENT_PERSISTENT_KEEPALIVE = Number(
  process.env.WG_CLIENT_PERSISTENT_KEEPALIVE || 25,
);

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

function loadDevicesDb() {
  try {
    if (!fs.existsSync(DEVICES_DB)) return {};
    const raw = fs.readFileSync(DEVICES_DB, "utf8");
    const parsed = JSON.parse(raw);
    return parsed && typeof parsed === "object" ? parsed : {};
  } catch (err) {
    console.warn("devices db read failed:", err.message);
    return {};
  }
}

function saveDevicesDb(db) {
  const dir = path.dirname(DEVICES_DB);
  fs.mkdirSync(dir, { recursive: true });
  fs.writeFileSync(DEVICES_DB, JSON.stringify(db, null, 2) + "\n", "utf8");
}

function rememberDevice(deviceId, publicKey, clientIp) {
  if (!deviceId) return;
  const db = loadDevicesDb();
  db[deviceId] = {
    publicKey,
    clientIp,
    updatedAt: new Date().toISOString(),
  };
  saveDevicesDb(db);
}

function findDeviceRecord(deviceId) {
  if (!deviceId) return null;
  const db = loadDevicesDb();
  const row = db[deviceId];
  if (!row || typeof row !== "object") return null;
  const clientIp = String(row.clientIp || "").trim().replace(/\/\d+$/, "");
  const publicKey = String(row.publicKey || "").trim();
  if (!/^10\.0\.0\.\d{1,3}$/.test(clientIp)) return null;
  return { publicKey, clientIp };
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
      const m = conf.match(
        /\[Interface\][\s\S]*?PrivateKey\s*=\s*([A-Za-z0-9+/=]+)/,
      );
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

function appendPeerToConf(publicKey, clientIp, deviceId) {
  const deviceNote = deviceId ? ` deviceId=${deviceId}` : "";
  const stanza =
    `\n# added by api/add-peer ${new Date().toISOString()}${deviceNote}\n` +
    `[Peer]\n` +
    `PublicKey = ${publicKey}\n` +
    `AllowedIPs = ${clientIp}/32\n`;
  fs.appendFileSync(WG_CONF, stanza, "utf8");
}

/**
 * Replace an existing peer's PublicKey in conf (same AllowedIPs), used when
 * the same deviceId reinstalls with a new keypair.
 */
function replacePeerPublicKeyInConf(oldPublicKey, newPublicKey, clientIp) {
  let conf = readConf();
  const blocks = conf.split(/(\[Peer\])/i);
  let changed = false;
  for (let i = 1; i < blocks.length; i += 2) {
    const header = blocks[i];
    const body = blocks[i + 1] || "";
    const keyMatch = body.match(/PublicKey\s*=\s*([A-Za-z0-9+/=]+)/);
    if (!keyMatch || keyMatch[1].trim() !== oldPublicKey) continue;
    let nextBody = body.replace(
      /PublicKey\s*=\s*[A-Za-z0-9+/=]+/,
      `PublicKey = ${newPublicKey}`,
    );
    if (!/AllowedIPs\s*=/i.test(nextBody)) {
      nextBody = nextBody.replace(/\s*$/, `\nAllowedIPs = ${clientIp}/32\n`);
    }
    blocks[i + 1] = nextBody;
    changed = true;
    break;
  }
  if (changed) {
    fs.writeFileSync(WG_CONF, blocks.join(""), "utf8");
    return true;
  }
  return false;
}

function applyPeerLive(publicKey, clientIp) {
  try {
    execFileSync(
      "wg",
      ["set", WG_INTERFACE, "peer", publicKey, "allowed-ips", `${clientIp}/32`],
      { stdio: ["ignore", "pipe", "pipe"] },
    );
  } catch (err) {
    console.warn("wg set failed (peer still in conf):", err.message);
  }
}

function removePeerLive(publicKey) {
  if (!publicKey) return;
  try {
    execFileSync("wg", ["set", WG_INTERFACE, "peer", publicKey, "remove"], {
      stdio: ["ignore", "pipe", "pipe"],
    });
  } catch (err) {
    console.warn("wg remove peer failed:", err.message);
  }
}

function successPayload(clientIp, message) {
  return {
    success: true,
    clientIp,
    address: `${clientIp}/32`,
    dns: CLIENT_DNS,
    persistentKeepalive: CLIENT_PERSISTENT_KEEPALIVE,
    serverPublicKey: readServerPublicKey(),
    message,
  };
}

app.get("/health", (_req, res) => {
  res.json({
    ok: true,
    conf: WG_CONF,
    interface: WG_INTERFACE,
    devicesDb: DEVICES_DB,
  });
});

app.post("/api/add-peer", (req, res) => {
  try {
    const body = req.body && typeof req.body === "object" ? req.body : {};

    const publicKey = String(
      body.publicKey ?? body.PublicKey ?? body.public_key ?? "",
    ).trim();
    const deviceId = String(
      body.deviceId ?? body.DeviceId ?? body.device_id ?? "",
    ).trim();

    void body.clientIp;
    void body.ClientIp;

    if (!publicKey || publicKey.length < 40) {
      return res.status(400).json({
        success: false,
        error: "publicKey is required",
        message: "publicKey is required (WireGuard base64 public key)",
      });
    }

    if (!fs.existsSync(WG_CONF)) {
      return res.status(500).json({
        success: false,
        error: `WireGuard conf not found: ${WG_CONF}`,
        message: `WireGuard conf not found: ${WG_CONF}`,
      });
    }

    if (app.locals.addingPeer) {
      return res.status(409).json({
        success: false,
        error: "Another peer registration is in progress — retry",
        message: "Another peer registration is in progress — retry",
      });
    }
    app.locals.addingPeer = true;

    try {
      const conf = readConf();
      const knownDevice = findDeviceRecord(deviceId);

      // Same device after reinstall: keep IP, rotate public key if needed.
      if (knownDevice) {
        const clientIp = knownDevice.clientIp;
        if (knownDevice.publicKey && knownDevice.publicKey !== publicKey) {
          const replaced = replacePeerPublicKeyInConf(
            knownDevice.publicKey,
            publicKey,
            clientIp,
          );
          removePeerLive(knownDevice.publicKey);
          if (!replaced) {
            appendPeerToConf(publicKey, clientIp, deviceId);
          }
          applyPeerLive(publicKey, clientIp);
          rememberDevice(deviceId, publicKey, clientIp);
          console.log(
            `add-peer rotate deviceId=${deviceId.slice(0, 8)}… ` +
              `publicKey=${publicKey.slice(0, 8)}… clientIp=${clientIp}`,
          );
          return res
            .status(200)
            .json(
              successPayload(
                clientIp,
                `Device remembered — rotated key, kept ${clientIp}/32`,
              ),
            );
        }

        // Same key (or empty stored key): ensure peer exists + remember.
        const existingIp = findPeerAllowedIp(conf, publicKey) || clientIp;
        if (!findPeerAllowedIp(conf, publicKey)) {
          appendPeerToConf(publicKey, existingIp, deviceId);
          applyPeerLive(publicKey, existingIp);
        }
        rememberDevice(deviceId, publicKey, existingIp);
        return res
          .status(200)
          .json(
            successPayload(
              existingIp,
              "Device remembered — reusing assigned IP",
            ),
          );
      }

      // Idempotent by public key (no deviceId, or first time with this key).
      const existingIp = findPeerAllowedIp(conf, publicKey);
      if (existingIp) {
        rememberDevice(deviceId, publicKey, existingIp);
        return res
          .status(200)
          .json(
            successPayload(existingIp, "Peer already present — reusing assigned IP"),
          );
      }

      const clientIp = nextClientIp(conf);
      appendPeerToConf(publicKey, clientIp, deviceId);
      applyPeerLive(publicKey, clientIp);
      rememberDevice(deviceId, publicKey, clientIp);

      console.log(
        `add-peer ok deviceId=${(deviceId || "-").slice(0, 8)}… ` +
          `publicKey=${publicKey.slice(0, 8)}… clientIp=${clientIp}`,
      );

      return res
        .status(200)
        .json(successPayload(clientIp, `Assigned ${clientIp}/32`));
    } finally {
      app.locals.addingPeer = false;
    }
  } catch (err) {
    console.error("add-peer error:", err);
    return res.status(500).json({
      success: false,
      error: err.message || "Internal error",
      message: err.message || "Internal error",
    });
  }
});

if (require.main === module) {
  app.listen(PORT, "0.0.0.0", () => {
    console.log(
      `WireGuard peer API listening on :${PORT} conf=${WG_CONF} ` +
        `if=${WG_INTERFACE} devicesDb=${DEVICES_DB}`,
    );
  });
}

module.exports = {
  app,
  nextClientIp,
  extractHostOctets,
  findPeerAllowedIp,
  findDeviceRecord,
  replacePeerPublicKeyInConf,
};
