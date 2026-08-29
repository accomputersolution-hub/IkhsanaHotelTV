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
  "serverPublicKey": "eGIDnt4o…",
  "message": "Assigned 10.0.0.4/32"
}
```

Client-supplied `clientIp` in the request body is **ignored** so devices cannot collide.

Re-registering the same `publicKey` returns the existing AllowedIP (idempotent).

## Run on the VPN server

```bash
cd wireguard-server
npm install
sudo WG_CONF=/etc/wireguard/wg0.conf PORT=3000 node server.js
```

Optional env:

| Variable | Default | Meaning |
|----------|---------|---------|
| `WG_CONF` | `/etc/wireguard/wg0.conf` | Config path |
| `WG_INTERFACE` | `wg0` | Interface for `wg set` |
| `WG_SERVER_PUBLIC_KEY` | from `wg show` | Override returned public key |
| `WG_MIN_CLIENT_HOST` | `2` | First client octet (`.1` = server) |
| `PORT` | `3000` | Listen port |

## Tests

```bash
npm test
```
