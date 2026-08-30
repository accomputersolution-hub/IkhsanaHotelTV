# WireGuard peer API (auto IP assignment)

Node service for `POST /api/add-peer` on the VPN host.

## Behavior

1. Read `/etc/wireguard/wg0.conf`
2. Find the highest `10.0.0.x` already present (Interface + Peer AllowedIPs)
3. Assign **next** IP (`max + 1`), e.g. after `.3` → `10.0.0.4`
4. Append `[Peer]` to conf + `wg set` live
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

`dns` is required by Android TV full-tunnel clients so websites resolve while the tunnel is UP.

Client-supplied `clientIp` in the request body is **ignored** so devices cannot collide.

Re-registering the same `publicKey` returns the existing AllowedIP (idempotent).

## Important: redeploy on the VPN host

If Android still gets:
```json
{"error":"PublicKey and clientIp are required"}
```
the **old** `server.js` is still running on `103.29.99.61:3001`. Copy this
folder’s `server.js` onto the VPS and restart Node (systemd / pm2 / screen).

Quick check after deploy:
```bash
curl -sS -X POST http://127.0.0.1:3001/api/add-peer \
  -H 'Content-Type: application/json' \
  -d '{"publicKey":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="}'
```
Must return `200` with a `clientIp` — never 400 asking for `clientIp`.

Optional env:

| Variable | Default | Meaning |
|----------|---------|---------|
| `WG_CONF` | `/etc/wireguard/wg0.conf` | Config path |
| `WG_INTERFACE` | `wg0` | Interface for `wg set` |
| `WG_SERVER_PUBLIC_KEY` | from `wg show` | Override returned public key |
| `WG_CLIENT_DNS` | `8.8.8.8, 8.8.4.4` | DNS returned to Android clients |
| `WG_MIN_CLIENT_HOST` | `2` | First client octet (`.1` = server) |
| `PORT` | `3001` | Listen port |

## Tests

```bash
npm test
```
