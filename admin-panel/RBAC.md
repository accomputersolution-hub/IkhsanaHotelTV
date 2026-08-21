# Staff RBAC (Firebase Auth + Realtime Database)

## Data model

```
staff_users/{uid}/
  role: "admin" | "kitchen" | "reception" | "housekeeping"
  hotelId: "your_hotel_id"
  email: "chef@hotel.com"
  displayName: "Chef"
```

Platform login still uses Firestore `users/{uid}` (`super_admin` | `hotel_admin`).
After Auth succeeds, the app also reads `staff_users/{uid}/role` and filters the PMS sidebar.

## Module access

| Role | Modules |
|------|---------|
| **admin** | Everything (Room Status, Food Orders, Menu, Billing, Housekeeping, Staff, …) |
| **kitchen** | Food Orders (`kds`), Menu Management (`menu`) |
| **reception** | Room Status (`pms`), Billing, Messaging, Concierge |
| **housekeeping** | Housekeeping, Room Status |

## Helper

```js
import { hasAccess, canAccessModule, getOperationalRole } from './rbac.js';

hasAccess('kitchen', 'kds');     // true
hasAccess('kitchen', 'billing'); // false
canAccessModule('staff');        // true only for admin
```

## Seed example (Firebase Console → Realtime Database)

```json
{
  "staff_users": {
    "FIREBASE_AUTH_UID": {
      "role": "kitchen",
      "hotelId": "ikhsana_001",
      "email": "kitchen@hotel.com",
      "displayName": "Kitchen"
    }
  }
}
```

Or use **Staff Management** in the admin panel (admin role only) to create accounts.
