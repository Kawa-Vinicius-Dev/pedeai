import '@mantine/core/styles.css';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { createBrowserRouter, RouterProvider } from 'react-router';
import { MenuProviders } from './MenuApp';
import { menuRoutes } from './routes';

const sentryDsn = import.meta.env.VITE_SENTRY_DSN;
if (sentryDsn) {
  void import('@sentry/react').then((Sentry) =>
    Sentry.init({ dsn: sentryDsn, environment: import.meta.env.MODE }),
  );
}

const root = document.getElementById('root');
if (!root) {
  throw new Error('Elemento #root não encontrado.');
}

createRoot(root).render(
  <StrictMode>
    <MenuProviders>
      <RouterProvider router={createBrowserRouter(menuRoutes)} />
    </MenuProviders>
  </StrictMode>,
);
