import { useHotelTenant } from '../hooks/useHotelTenant.js';

/**
 * Full-screen loading while Hotels/{slug} is fetched from Firestore.
 */
export function TenantLoadingScreen({ slug }) {
  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'grid',
        placeItems: 'center',
        background: 'radial-gradient(ellipse at top, #1a2744 0%, #0B1325 70%)',
        color: '#F1F5F9',
        fontFamily: 'system-ui, sans-serif',
        padding: 24,
        textAlign: 'center',
      }}
    >
      <div>
        <div
          aria-hidden
          style={{
            width: 42,
            height: 42,
            margin: '0 auto 18px',
            borderRadius: '50%',
            border: '3px solid rgba(201,169,98,0.25)',
            borderTopColor: '#C9A962',
            animation: 'tenant-spin 0.8s linear infinite',
          }}
        />
        <h1 style={{ margin: 0, fontSize: 22, fontWeight: 700, color: '#E8D5A3' }}>
          Loading property…
        </h1>
        <p style={{ marginTop: 8, opacity: 0.7, fontSize: 14 }}>
          {slug ? (
            <>
              Resolving <code style={{ color: '#E8D5A3' }}>{slug}</code>
            </>
          ) : (
            'Detecting hotel from subdomain'
          )}
        </p>
        <style>{`@keyframes tenant-spin { to { transform: rotate(360deg); } }`}</style>
      </div>
    </div>
  );
}

/**
 * Fallback when slug is missing or Hotels/{slug} does not exist.
 */
export function TenantErrorScreen({ status, slug, error, onRetry }) {
  const title =
    status === 'not_found'
      ? 'Hotel not found'
      : status === 'missing_slug'
        ? 'No hotel subdomain'
        : 'Unable to load hotel';

  const detail =
    error ||
    (status === 'not_found'
      ? `No document at Hotels/${slug || '…'} in Firestore.`
      : 'Open a tenant URL like https://ikhsana_001.hostity.in');

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'grid',
        placeItems: 'center',
        background: 'radial-gradient(ellipse at top, #2a1520 0%, #0B1325 70%)',
        color: '#F1F5F9',
        fontFamily: 'system-ui, sans-serif',
        padding: 24,
        textAlign: 'center',
      }}
    >
      <div style={{ maxWidth: 420 }}>
        <p
          style={{
            margin: '0 0 8px',
            fontSize: 12,
            letterSpacing: '0.12em',
            textTransform: 'uppercase',
            color: '#FCA5A5',
            fontWeight: 700,
          }}
        >
          Tenant error
        </p>
        <h1 style={{ margin: 0, fontSize: 26, fontWeight: 800 }}>{title}</h1>
        <p style={{ marginTop: 12, lineHeight: 1.5, color: '#94A3B8' }}>{detail}</p>
        {slug ? (
          <p style={{ marginTop: 8, fontSize: 13, color: '#64748B' }}>
            Slug: <code style={{ color: '#E8D5A3' }}>{slug}</code>
          </p>
        ) : null}
        {typeof onRetry === 'function' ? (
          <button
            type="button"
            onClick={onRetry}
            style={{
              marginTop: 20,
              padding: '10px 18px',
              borderRadius: 10,
              border: '1px solid rgba(201,169,98,0.45)',
              background: 'rgba(201,169,98,0.18)',
              color: '#E8D5A3',
              fontWeight: 700,
              cursor: 'pointer',
            }}
          >
            Try again
          </button>
        ) : null}
      </div>
    </div>
  );
}

/**
 * Gate children until the hotel tenant is ready.
 *
 * @example
 *   <HotelTenantGate db={db}>
 *     {(hotel) => <KioskApp hotel={hotel} />}
 *   </HotelTenantGate>
 */
export function HotelTenantGate({
  db,
  rootDomain = 'hostity.in',
  fallback = null,
  useDefaultOnLocal = true,
  children,
  loadingScreen,
  errorScreen,
}) {
  const tenant = useHotelTenant({
    db,
    rootDomain,
    fallback,
    useDefaultOnLocal,
  });

  if (tenant.status === 'loading') {
    return loadingScreen || <TenantLoadingScreen slug={tenant.slug} />;
  }

  if (tenant.status !== 'ready' || !tenant.hotel) {
    return (
      errorScreen || (
        <TenantErrorScreen
          status={tenant.status}
          slug={tenant.slug}
          error={tenant.error}
          onRetry={tenant.reload}
        />
      )
    );
  }

  if (typeof children === 'function') {
    return children(tenant.hotel, tenant);
  }

  return children;
}
