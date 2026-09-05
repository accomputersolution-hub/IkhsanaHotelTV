# Admin Panel API — environment variables (Vercel)

Password APIs under `/api/**` need Firebase Admin + mailer credentials on the Vercel project.

## Required (Firebase Admin)

Either:

```
FIREBASE_SERVICE_ACCOUNT_JSON={"type":"service_account",...}
```

Or:

```
FIREBASE_PROJECT_ID=ikhsana-hotel-tv
FIREBASE_CLIENT_EMAIL=firebase-adminsdk-...@ikhsana-hotel-tv.iam.gserviceaccount.com
FIREBASE_PRIVATE_KEY="-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----\n"
```

Optional:

```
FIREBASE_DATABASE_URL=https://ikhsana-hotel-tv-default-rtdb.asia-southeast1.firebasedatabase.app
PASSWORD_RESET_CONTINUE_URL=https://www.pcncloud.in/#/login
```

## Required (custom reset mailer)

One of:

**SMTP**

```
SMTP_HOST=smtp.example.com
SMTP_PORT=587
SMTP_USER=...
SMTP_PASS=...
SMTP_FROM="Hostity Admin <noreply@pcncloud.in>"
```

**Resend**

```
RESEND_API_KEY=re_...
RESEND_FROM="Hostity Admin <noreply@pcncloud.in>"
```

## Optional (restrict Forgot Password destination)

```
RESET_LINK_DEST_ALLOWLIST=it@company.com,security@company.com
RESET_LINK_DEST_DOMAINS=pcncloud.in,company.com
```

Production domain map: see [`docs/DOMAIN_PCNCLOUD.md`](../docs/DOMAIN_PCNCLOUD.md).

## Endpoints

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| POST | `/api/staff/override-password` | Bearer ID token (Super/Hotel Admin) | Instant staff password overwrite |
| POST | `/api/auth/custom-reset` | None (rate-limited) | Generate reset link + email via SMTP/Resend |
