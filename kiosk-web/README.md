# Kiosk web — subdomain tenant bootstrap (React + Firebase)

Users open **`https://{hotel_slug}.hostity.in`**. The app:

1. Extracts `{hotel_slug}` from `window.location.hostname`
2. Loads Firestore **`Hotels/{hotel_slug}`**
3. Shows a loading screen, then branding — or an error if the hotel is missing

Localdev fallback: `http://localhost:5173/?hotel=ikhsana_001`

## Files

| File | Purpose |
|------|---------|
| `src/lib/extractSubdomain.js` | Pure hostname / query → slug |
| `src/hooks/useHotelTenant.js` | React hook + Firestore fetch |
| `src/components/HotelTenantGate.jsx` | Loading + error + ready gate |
| `src/App.example.jsx` | Drop-in usage example |

## Usage

```jsx
import { HotelTenantGate } from './components/HotelTenantGate.jsx';

<HotelTenantGate db={db} rootDomain="hostity.in" useDefaultOnLocal>
  {(hotel) => <KioskApp hotel={hotel} />}
</HotelTenantGate>
```

Or the hook alone:

```jsx
const { status, hotel, slug, error, reload } = useHotelTenant({ db });
```

## Hook states

| `status` | Meaning |
|----------|---------|
| `loading` | Fetching `Hotels/{slug}` |
| `ready` | Hotel found — use `hotel.logoUrl`, `themeColor`, `bgWallpaper` |
| `not_found` | Slug resolved but no Firestore doc |
| `missing_slug` | Apex / localhost without `?hotel=` |
| `error` | Network / Firebase failure |

## DNS note

Point `*.hostity.in` (wildcard) at your Vercel project so every hotel slug resolves without adding each subdomain manually. See Vercel multi-tenant wildcard docs.
