import { initializeApp } from 'https://www.gstatic.com/firebasejs/11.6.0/firebase-app.js';
import { getFirestore } from 'https://www.gstatic.com/firebasejs/11.6.0/firebase-firestore.js';

export const firebaseConfig = {
  apiKey: 'AIzaSyBEXhPG6aNiJ1S7pN4EDoBo6EYMtbNe-pQ',
  authDomain: 'ikhsana-hotel-tv.firebaseapp.com',
  projectId: 'ikhsana-hotel-tv',
  storageBucket: 'ikhsana-hotel-tv.firebasestorage.app',
};

export const HOTEL_ID = 'ikhsana';

export const app = initializeApp(firebaseConfig);
export const db = getFirestore(app);
