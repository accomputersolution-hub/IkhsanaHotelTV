# Ikhsana Hotel — Multi-Tenant Master Admin Panel

Vanilla HTML/JS reception + Super Admin dashboard for the **Ikhsana Hotel TV** Android app.  
Firebase Web SDK v11 (Auth + Firestore) · Tailwind CDN.

## Architecture

| Role | Route | Behavior |
|------|-------|----------|
| **Super Admin** | `#/super-admin` or `/super-admin.html` | Hotels table, add hotel, impersonate any tenant |
| **Hotel Admin** | `#/pms` | PMS modules scoped to their `hotelId` |
| **Login** | `#/login` | Email/password via Firebase Auth |

Tenant data lives under **`Hotels/{hotelId}/…`** (same shape as the Android TV app).  
User profiles: **`users/{uid}`** with `{ role, hotelId, email }`.

## Quick Start

```bash
cd admin-panel
npm start
```

Open **http://localhost:3000** → `#/login`.

### Super Admin setup (secure)

1. Firebase Console → Authentication → enable Email/Password → create the Super Admin user.
2. In Firestore, create `users/{uid}` manually with:
   `{ "role": "super_admin", "hotelId": "", "email": "<same email>" }`
3. Sign in on `#/login` with that email/password.
4. There is **no** client-side bootstrap / self-elevation to Super Admin.

Invalid credentials always show **Invalid email or password** and never grant access.

### Password management

1. **Change Password** (signed-in admin) — reauth with current password, then `updatePassword`.
2. **Staff → Reset password** — modal with new password only; calls `/api/staff/override-password` (Firebase Admin SDK, no email).
3. **Forgot password?** on login — account email + **Send link to**; calls `/api/auth/custom-reset` which uses `generatePasswordResetLink` + SMTP/Resend (not Firebase’s default mailer).

See [`API_ENV.md`](./API_ENV.md) for Vercel env vars (`FIREBASE_*`, `SMTP_*` / `RESEND_*`).

### Onboard a hotel

From Super Admin → **Add New Hotel** (name, slug, admin email/password, branding).  
This creates:

- `Hotels/{slug}` metadata + branding  
- Auth user for the hotel admin  
- `users/{uid}` with `role: hotel_admin` and `hotelId: slug`

### Impersonation

Use the **Manage hotel** dropdown (or **Open PMS** on a row) to load that hotel’s Room PMS / KDS / Menu / Messaging as Super Admin.

## Modules (per hotel)

| Module | Path |
|--------|------|
| Rooms & Guests | `Hotels/{hotelId}/Rooms` |
| Alerts / Broadcasts | `Hotels/{hotelId}/Alerts`, `…/Broadcasts` |
| Menu | `Hotels/{hotelId}/Menu` |
| Requests | `Hotels/{hotelId}/Requests` |
| Orders (KDS) | `Live_Orders` filtered by `hotelId` |

## Key files

```
js/auth.js              Firebase Auth + roles
js/tenant-context.js    Active hotelId + branding
js/router.js            Hash routes (#/login, #/super-admin, #/pms)
js/super-admin.js       Hotels CRUD + impersonation
js/paths.js             Dynamic Hotels/{hotelId}/… helpers
js/app.js               Shell switching by role/route
```

## Notes

- Keep `Hotels` capitalization in sync with Android `FirestorePaths.kt`.
- Canonical demo slug is **`ikhsana_001`** (underscore). Hyphenated ids like `ikhsana-001` are normalized to underscores on both Android and Web Admin.
- Enable Auth + update Firestore rules before production (see `firestore.rules.example`).

## Production domains (`pcncloud.in`)

| Host | App |
|------|-----|
| `www.pcncloud.in` | This admin panel (Vercel root `admin-panel`) |
| `go.pcncloud.in` | Marketing site (`go-site`) |
| `{slug}.pcncloud.in` | Hotel/tenant public host |

Full DNS + Vercel + Firebase checklist: [`docs/DOMAIN_PCNCLOUD.md`](../docs/DOMAIN_PCNCLOUD.md).
