export {
  extractPublicSlug,
  extractTenantSlug,
  resolvePublicSlugFromLocation,
  resolveTenantSlugFromLocation,
  normalizePublicSlug,
} from './lib/extractSubdomain.js';
export { fetchPublicHotelConfig } from './lib/fetchPublicHotelConfig.js';
export { useHotelTenant, fetchHotelBySlug } from './hooks/useHotelTenant.js';
export {
  useDevicePairing,
  getOrCreateDeviceId,
  readDeviceSession,
  clearDeviceSession,
} from './hooks/useDevicePairing.js';
export {
  HotelTenantGate,
  TenantLoadingScreen,
  TenantErrorScreen,
} from './components/HotelTenantGate.jsx';
export { DevicePairingScreen } from './components/DevicePairingScreen.jsx';
