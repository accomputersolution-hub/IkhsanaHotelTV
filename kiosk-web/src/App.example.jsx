/**
 * Example root usage for a React + Firebase kiosk on *.hostity.in
 *
 * Copy these patterns into your Vite/CRA app after installing:
 *   npm i react react-dom firebase
 */

import { initializeApp } from 'firebase/app';
import { getFirestore } from 'firebase/firestore';
import { HotelTenantGate } from './components/HotelTenantGate.jsx';

const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
  // …same project as admin-panel / Android TV
};

const app = initializeApp(firebaseConfig);
const db = getFirestore(app);

export default function App() {
  return (
    <HotelTenantGate db={db} rootDomain="hostity.in" useDefaultOnLocal>
      {(hotel) => (
        <main
          style={{
            minHeight: '100vh',
            backgroundImage: hotel.bgWallpaper
              ? `linear-gradient(rgba(11,19,37,.55), rgba(11,19,37,.78)), url(${hotel.bgWallpaper})`
              : 'radial-gradient(ellipse at top, #1a2744, #0B1325)',
            backgroundSize: 'cover',
            backgroundPosition: 'center',
            color: '#F1F5F9',
            padding: 32,
          }}
        >
          {hotel.logoUrl ? (
            <img
              src={hotel.logoUrl}
              alt={hotel.name}
              style={{ height: 64, objectFit: 'contain', marginBottom: 16 }}
            />
          ) : null}
          <h1 style={{ margin: 0, color: hotel.themeColor || '#E8D5A3' }}>{hotel.name}</h1>
          <p style={{ opacity: 0.75 }}>Tenant id: {hotel.id}</p>
          {/* Mount room kiosk / dining routes here */}
        </main>
      )}
    </HotelTenantGate>
  );
}
