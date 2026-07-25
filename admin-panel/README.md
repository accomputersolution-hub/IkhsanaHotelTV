# Ikhsana Hotel — Web Admin Panel

Standalone reception dashboard for the **Ikhsana Hotel TV** Android app.  
Built with HTML, Tailwind CSS (CDN), and Firebase Web SDK v11 (modular).

## Features

| Section | Firestore Path | Description |
|---------|---------------|-------------|
| **Live Room Orders** | `Live_Orders` | Real-time order feed with status updates (Pending → Preparing → Delivered) and audio alert on new orders |
| **Guest Management** | `Hotels/ikhsana/Rooms` | Assign guest names per room — TV welcome header updates live |
| **Send Room Alerts** | `Hotels/ikhsana/Alerts` | Push messages to guest TV screens by room number |
| **Menu Editor** | `Hotels/ikhsana/Menu` | Full CRUD: add, edit price/description, toggle availability, delete |

## Quick Start

### 1. Firebase setup

1. Open [Firebase Console](https://console.firebase.google.com) → project **ikhsana-hotel-tv**
2. Enable **Cloud Firestore**
3. Copy rules from `firestore.rules.example` into Firestore → Rules (dev only)
4. Deploy indexes from `firestore.indexes.json` if prompted by Firebase

### 2. Run locally

ES modules require a local HTTP server — do **not** open `index.html` directly via `file://`.

**Option A — npm:**
```bash
cd admin-panel
npm start
```

**Option B — Windows script:**
```powershell
.\start.ps1
```

**Option C — Python:**
```bash
python -m http.server 3000
```

Open **http://localhost:3000** in your browser.

> Click anywhere on the page once to unlock audio notifications (browser autoplay policy).

### 3. Seed sample data (optional)

After starting the server, open DevTools console and run:

```js
import('./scripts/seed-data.js');
```

This creates 9 default menu items and a Room 101 guest profile.

## File Structure

```
admin-panel/
├── index.html              # Main dashboard
├── css/
│   └── custom.css          # Scrollbar, animations, connection status
├── js/
│   ├── firebase-config.js  # Firebase init + hotel ID
│   ├── utils.js            # Bell audio, toast, modals
│   ├── orders.js           # Live orders + bell on new order
│   ├── guests.js           # Guest name management per room
│   ├── alerts.js           # Send alerts + recent alerts list
│   ├── menu.js             # Full menu CRUD + edit modal
│   └── app.js              # Entry point
├── scripts/
│   └── seed-data.js        # Firestore seed script
├── firestore.rules.example # Dev security rules
├── firestore.indexes.json  # Required composite indexes
├── package.json
├── start.ps1 / start.bat
└── README.md
```

## Firestore Schema

```
Live_Orders/{orderId}
  hotelId, roomNumber, guestName, items[], totalAmount, status, timestamp

Hotels/ikhsana/
  Menu/{itemId}       → name, description, price, category, available, imageUrl
  Alerts/{alertId}    → roomNumber, title, message, priority, read, timestamp
  Rooms/{roomNumber}  → guestName, hotelName, hotelLogoUrl, hotelInfo, checkInDate
```

## Android TV App Alignment

The Android TV app reads from the same Firestore paths with `hotelId = "ikhsana"`.  
Ensure each TV device is provisioned with the correct room number (default: `101`).

## Production Notes

- Replace open Firestore rules with authenticated admin access
- Add Firebase Authentication for reception staff login
- Deploy to Firebase Hosting: `firebase init hosting && firebase deploy`
