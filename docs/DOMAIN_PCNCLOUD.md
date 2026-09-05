# Domain cutover — hostity.in → pcncloud.in

All app code now defaults to **`pcncloud.in`**. Point DNS + Vercel + Firebase
the same way `hostity.in` was wired before.

## Hostname map (was → now)

| Role | Old | New | Vercel root |
|------|-----|-----|-------------|
| Master admin | `www.hostity.in` | **`www.pcncloud.in`** | `admin-panel` |
| Apex redirect (optional) | `hostity.in` → www | **`pcncloud.in` → www** | same admin project |
| Marketing / “go” site | `go.hostity.in` | **`go.pcncloud.in`** | `go-site` |
| Hotel / tenant public | `{slug}.hostity.in` | **`{slug}.pcncloud.in`** | kiosk or admin tenant host |
| Mail from | `noreply@hostity.in` | **`noreply@pcncloud.in`** | Vercel env |

Reserved platform subdomains (not hotels): `www`, `go`, `admin`, `app`, `api`, `mail`, …

## DNS records (registrar for pcncloud.in)

```
# Admin
A / ALIAS   @       → Vercel (or CNAME to cname.vercel-dns.com if supported)
CNAME       www     → cname.vercel-dns.com

# Marketing
CNAME       go      → cname.vercel-dns.com

# Wildcard tenants (hotel public / kiosk)
CNAME       *       → cname.vercel-dns.com
```

Exact Vercel CNAME target is shown in each project → Domains.

## Vercel projects

### 1) Admin (was www.hostity.in)

1. Project root: **`admin-panel`**
2. Domains: `www.pcncloud.in`, optionally `pcncloud.in`
3. Env (Production) — update any old hostity values:

```
PASSWORD_RESET_CONTINUE_URL=https://www.pcncloud.in/#/login
SMTP_FROM="Hostity Admin <noreply@pcncloud.in>"
# or RESEND_FROM=...
RESET_LINK_DEST_DOMAINS=pcncloud.in,company.com
```

Redeploy after env changes.

### 2) Marketing go-site

1. New or existing project root: **`go-site`**
2. Domain: **`go.pcncloud.in`** only (do not attach www here)

### 3) Tenant / kiosk host (if separate)

1. Attach **`*.pcncloud.in`** (and remove old `*.hostity.in`)
2. Ensure `rootDomain` is `pcncloud.in` (code default already)

## Firebase Console

Authentication → Settings → **Authorized domains** — add:

- `pcncloud.in`
- `www.pcncloud.in`
- `go.pcncloud.in`
- any `{slug}.pcncloud.in` you use in browsers (wildcard not always listed; add as needed)

Remove `hostity.in` / `www.hostity.in` when traffic has moved.

## Mail / DNS extras

- SPF / DKIM for `pcncloud.in` on the SMTP/Resend provider
- Update `SMTP_FROM` / `RESEND_FROM` to `@pcncloud.in`
- Google Workspace / mailbox: `hello@pcncloud.in`, `noreply@pcncloud.in`

## Android TV

No Hostity web domain is baked into the APK for admin. WireGuard still uses the
VPN host IP (`103.29.99.58`), unrelated to this cutover.

## Verify

```bash
rg 'hostity\.in'   # must be empty in repo
curl -I https://www.pcncloud.in
curl -I https://go.pcncloud.in
curl -I https://{your-hotel-slug}.pcncloud.in
```

Admin login, create/open a hotel slug URL, send a password-reset mail, open go-site.
