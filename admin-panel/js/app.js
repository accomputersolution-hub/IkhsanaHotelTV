import { initAudio, setConnectionStatus } from './utils.js';
import { initOrders } from './orders.js';
import { initAlerts } from './alerts.js';
import { initMenu } from './menu.js';
import { initGuests } from './guests.js';
import { initHousekeeping, initConcierge } from './requests.js';
import { initAnalytics } from './analytics.js';
import { initNavigation } from './navigation.js';
import './paths.js';

document.addEventListener('DOMContentLoaded', () => {
  setConnectionStatus('connecting');
  initNavigation();
  initAudio();
  initOrders();
  initGuests();
  initAlerts();
  initMenu();
  initHousekeeping();
  initConcierge();
  initAnalytics();
});
