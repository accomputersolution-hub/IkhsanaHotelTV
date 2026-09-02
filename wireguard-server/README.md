# WireGuard peer API (auto IP assignment)

Node service for `POST /api/add-peer` on the VPN host.

## Behavior

1. Read `/etc/wireguard/wg0.conf`
2. Prefer **deviceId** lookup (`peers-by-device.json`) so reinstalls keep the same IP
3. Else find highest `10.0.0.x` and assign **next** IP (`max + 1`)
4. Append / update `[Peer]` + `wg set` live
5. Return JSON:

```json
{
  "success": true,
  "clientIp": "10.0.0.4",
  "address": "10.0.0.4/32",
  "dns": "8.8.8.8, 8.8.4.4",
  "serverPublicKey": "eGIDnt4o…",
  "message": "Assigned 10.0.0.4/32"
}
```

Android body:

```json
{ "publicKey": "<base64>", "deviceId": "<ANDROID_ID or UUID>" }
```

`dns` is required by Android TV full-tunnel clients so websites resolve while the tunnel is UP.

Client-supplied `clientIp` in the request body is **ignored** so devices cannot collide.

Re-registering the same `publicKey` or the same `deviceId` returns the existing AllowedIP.

## Important: redeploy on the VPN host

If Android still gets:
```json
{"error":"PublicKey and clientIp are required"}
```
the **old** `server.js` is still running on `103.29.99.58:3001`. Copy this
folder’s `server.js` onto the VPS and restart Node (systemd / pm2 / screen).

Quick check after deploy:
```bash
curl -sS -X POST http://127.0.0.1:3001/api/add-peer \
  -H 'Content-Type: application/json' \
  -d '{"publicKey":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=","deviceId":"test-device-1"}'
```
Must return `200` with a `clientIp` — never 400 asking for `clientIp`.

Optional env:

| Variable | Default | Meaning |
|----------|---------|---------|
| `WG_CONF` | `/etc/wireguard/wg0.conf` | Config path |
| `WG_INTERFACE` | `wg0` | Interface for `wg set` |
| `WG_DEVICES_DB` | `<WG_CONF dir>/peers-by-device.json` | deviceId → IP map |
| `WG_SERVER_PUBLIC_KEY` | from `wg show` | Override returned public key |
| `WG_CLIENT_DNS` | `8.8.8.8, 8.8.4.4` | DNS returned to Android clients |
| `WG_MIN_CLIENT_HOST` | `2` | First client octet (`.1` = server) |
| `PORT` | `3001` | Listen port |

## Tests

```bash
npm test
```
