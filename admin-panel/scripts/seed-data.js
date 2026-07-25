/**
 * Seed script — populates default menu items and a sample room profile.
 *
 * Usage:
 *   1. Open admin-panel/index.html via a local server (npm start)
 *   2. Open browser DevTools console
 *   3. Paste and run this script, OR import it as a module:
 *        import './scripts/seed-data.js'
 *
 * Safe to run multiple times — uses deterministic document IDs for menu items.
 */

import { db, DEFAULT_HOTEL_ID } from '../js/firebase-config.js';
import {
  doc,
  setDoc,
  collection,
  getDocs,
} from 'https://www.gstatic.com/firebasejs/11.6.0/firebase-firestore.js';

const DEFAULT_MENU = [
  { id: 'b1', name: 'Continental Breakfast', description: 'Eggs, toast, juice', price: 350, category: 'breakfast' },
  { id: 'b2', name: 'South Indian Breakfast', description: 'Idli, dosa, chutney', price: 280, category: 'breakfast' },
  { id: 'b3', name: 'Pancake Stack', description: 'Maple syrup & butter', price: 320, category: 'breakfast' },
  { id: 's1', name: 'Veg Sandwich', description: 'Fresh vegetables', price: 180, category: 'snacks' },
  { id: 's2', name: 'French Fries', description: 'Crispy golden fries', price: 150, category: 'snacks' },
  { id: 's3', name: 'Samosa Platter', description: '4 pcs with chutney', price: 120, category: 'snacks' },
  { id: 'v1', name: 'Fresh Orange Juice', description: '250 ml', price: 120, category: 'beverages' },
  { id: 'v2', name: 'Masala Chai', description: 'Traditional spiced tea', price: 80, category: 'beverages' },
  { id: 'v3', name: 'Cold Coffee', description: 'Blended with ice cream', price: 160, category: 'beverages' },
];

async function seedMenu() {
  for (const item of DEFAULT_MENU) {
    await setDoc(doc(db, 'Hotels', DEFAULT_HOTEL_ID, 'Menu', item.id), {
      name: item.name,
      description: item.description,
      price: item.price,
      category: item.category,
      available: true,
      imageUrl: '',
    });
    console.log(`✓ Menu item: ${item.name}`);
  }
}

async function seedRoom(roomNumber = '101', guestName = 'Guest') {
  await setDoc(doc(db, 'Hotels', DEFAULT_HOTEL_ID, 'Rooms', roomNumber), {
    guestName,
    hotelName: 'Ikhsana Hotel',
    hotelLogoUrl: '',
    hotelInfo:
      'Welcome to Ikhsana Hotel!\n\n' +
      '• 24/7 Room Service\n' +
      '• Free Wi-Fi\n' +
      '• Swimming Pool (6 AM – 10 PM)\n' +
      '• Spa & Wellness Center\n' +
      '• Concierge Desk',
    checkInDate: new Date().toLocaleDateString('en-IN'),
    checkOutDate: '',
  });
  console.log(`✓ Room profile: ${roomNumber} (${guestName})`);
}

async function seed() {
  const existing = await getDocs(collection(db, 'Hotels', DEFAULT_HOTEL_ID, 'Menu'));
  if (!existing.empty) {
    const proceed = confirm(`${existing.size} menu items already exist. Re-seed anyway?`);
    if (!proceed) return;
  }

  console.log('Seeding Firestore…');
  await seedMenu();
  await seedRoom('101', 'John Doe');
  console.log('Done! Refresh the admin panel.');
  alert('Seed complete! Menu items and Room 101 profile created.');
}

seed().catch(console.error);
