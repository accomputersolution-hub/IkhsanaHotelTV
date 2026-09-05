import { initializeApp } from 'firebase/app';
import { getFirestore } from 'firebase/firestore';
import { HotelTenantGate } from './components/HotelTenantGate.jsx';

const app = initializeApp({
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
});
const db = getFirestore(app);

export default function App() {
  return (
    <HotelTenantGate db={db} rootDomain="pcncloud.in">
      {(hotel, session) => (
        <main
          style={{
            minHeight: '100vh',
            padding: 32,
            color: '#F1F5F9',
            background: hotel.bgWallpaper
              ? `linear-gradient(rgba(11,19,37,.6), rgba(11,19,37,.82)), url(${hotel.bgWallpaper}) center/cover`
              : '#0B1325',
          }}
        >
          {hotel.logoUrl ? (
            <img src={hotel.logoUrl} alt="" style={{ height: 56, marginBottom: 16 }} />
          ) : null}
          <h1 style={{ color: hotel.themeColor || '#E8D5A3' }}>{hotel.name}</h1>
          <p>Public slug: {hotel.publicSlug}</p>
          <p>Paired room: {session?.roomNumber}</p>
          {/* Room-scoped kiosk routes — use session.hotelId + session.roomNumber */}
        </main>
      )}
    </HotelTenantGate>
  );
}
