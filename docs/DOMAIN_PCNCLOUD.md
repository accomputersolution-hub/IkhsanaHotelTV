# Domain map — pcncloud.in

Production hostnames for Hostity / PCN Cloud.

## Live map

| Role | Hostname | Vercel project root |
|------|----------|---------------------|
| **Marketing site** | **`www.pcncloud.in`** | `go-site` |
| Apex → marketing | **`pcncloud.in`** → 308 → `www.pcncloud.in` | `go-site` |
| **Master admin** | **`admin.pcncloud.in`** | `admin-panel` |
| Hotel / tenant public | **`{slug}.pcncloud.in`** | usually same as admin (wildcard) or kiosk |

Optional alias: `go.pcncloud.in` → marketing (only if you still want the old “go” host).

Reserved platform subdomains (never hotel slugs): `www`, `admin`, `go`, `app`, `api`, `mail`, …

## Vercel setup (matches your Domains screen)

You currently have **one** project holding `admin`, `www`, apex, and `*`. Split them:

### Project A — Admin (`admin-panel`)

**Keep / add**
- `admin.pcncloud.in` → Production
- `*.pcncloud.in` → Production (hotel tenants)

**Remove from this project**
- `www.pcncloud.in`
- `pcncloud.in` (apex redirect)

### Project B — Marketing (`go-site`)

**Add**
- `www.pcncloud.in` → Production
- `pcncloud.in` → Redirect **308** → `www.pcncloud.in`

Root Directory for Project B: **`go-site`**.

## DNS (registrar)

```
CNAME   www     → cname.vercel-dns.com     # marketing project
CNAME   admin   → cname.vercel-dns.com     # admin project
CNAME   *       → cname.vercel-dns.com     # admin (or kiosk) project
# apex: A/ALIAS per Vercel instructions for pcncloud.in → www redirect on marketing project
```

Use the exact CNAME target Vercel shows for each project.

## Firebase

Authorized domains:
- `pcncloud.in`
- `www.pcncloud.in`
- `admin.pcncloud.in`
- tenant hosts as needed

## Admin env (Vercel → admin project)

```
PASSWORD_RESET_CONTINUE_URL=https://admin.pcncloud.in/#/login
SMTP_FROM="Hostity Admin <noreply@pcncloud.in>"
RESET_LINK_DEST_DOMAINS=pcncloud.in,company.com
```

## Verify

```bash
curl -I https://www.pcncloud.in      # marketing (go-site)
curl -I https://admin.pcncloud.in    # admin panel
curl -I https://pcncloud.in          # 308 → www
curl -I https://{hotel-slug}.pcncloud.in
```
