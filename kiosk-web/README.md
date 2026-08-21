# Secure multi-tenant kiosk (public slug + device pairing)

## Why not `Hotels/{subdomain}`?

Using the Firestore document id as the subdomain is an **IDOR** risk: anyone who
guesses `ikhsana_001.hostity.in` can probe your private hotel id space, and a
direct `get(Hotels/{id})` often returns sensitive fields.

## Secure flow

1. **Subdomain** `ikhsana.hostity.in` → public slug `ikhsana`
2. **Read** `public_hotels/ikhsana` (name, logo, theme, wallpaper, internal `hotelId`)
3. **Pairing** — kiosk shows a random **6-digit code** (no room number typed on device)
4. **Reception** claims the code in Admin → binds `roomNumber`
5. **Custom Claims** (Cloud Function, recommended) mint `{ hotelId, roomNumber, deviceId }` so Security Rules can allow room-scoped reads

```
public_hotels/{publicSlug}          ← anonymous read (branding only)
Hotels/{hotelId}/                   ← staff only
Hotels/{hotelId}/Rooms/{room}       ← staff OR paired device claims
Hotels/{hotelId}/pairing_codes/{6}  ← kiosk create; staff claim
```

## React usage

```jsx
import { HotelTenantGate } from './components/HotelTenantGate.jsx';

<HotelTenantGate db={db} rootDomain="hostity.in">
  {(hotel, session) => (
    <KioskApp hotel={hotel} roomNumber={session.roomNumber} />
  )}
</HotelTenantGate>
```

Local: `http://localhost:5173/?slug=ikhsana`

## Files

| File | Role |
|------|------|
| `src/lib/extractSubdomain.js` | Public slug from hostname |
| `src/lib/fetchPublicHotelConfig.js` | `public_hotels/{slug}` fetch |
| `src/hooks/useHotelTenant.js` | Loading / not_found / ready |
| `src/hooks/useDevicePairing.js` | 6-digit code + listen for claim |
| `src/components/DevicePairingScreen.jsx` | Pairing UI |
| `src/components/HotelTenantGate.jsx` | Public config + pairing gate |
| `admin-panel/firestore.rules.example` | Production-oriented rules |

## Custom Claims (after claim)

Use a callable Cloud Function when staff claims a code:

```js
await admin.auth().setCustomUserClaims(deviceAuthUid, {
  hotelId,
  roomNumber,
  deviceId,
});
```

Anonymous Auth + claims, or a short-lived custom token issued to the kiosk after claim, is the cleanest way to satisfy `isPairedRoom()` in the rules file.
