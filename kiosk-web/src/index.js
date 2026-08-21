export { extractTenantSlug, resolveTenantSlugFromLocation, normalizeHotelId } from './lib/extractSubdomain.js';
export { useHotelTenant, fetchHotelBySlug } from './hooks/useHotelTenant.js';
export {
  HotelTenantGate,
  TenantLoadingScreen,
  TenantErrorScreen,
} from './components/HotelTenantGate.jsx';
