import type { ComponentType } from 'react';
import { Navigate, type RouteObject } from 'react-router';
import { GuestOnly, RequireAuth, RequireRole } from '../features/auth/guards';
import { LoginPage } from '../features/auth/LoginPage';
import { SignupPage } from '../features/auth/SignupPage';
import { HomePage } from '../features/home/HomePage';
import { NewOrderPage } from '../features/orders/NewOrderPage';
import { OrdersBoardPage } from '../features/orders/OrdersBoardPage';
import { AVAILABILITY_TOGGLERS, ORDER_TAKERS, ORDER_VIEWERS, SETTINGS_MANAGERS } from '../shared/lib/roles';
import { AppLayout } from './AppLayout';

/**
 * Telas fora do fluxo de pico (cardápio, histórico, configurações, impressão, integrações) baixam só quando alguém
 * abre: o login, o quadro e o PDV carregam mais rápido no computador do caixa.
 */
function page<M extends Record<string, unknown>>(load: () => Promise<M>, name: keyof M & string) {
  return async () => ({ Component: (await load())[name] as ComponentType });
}

export const routes: RouteObject[] = [
  {
    element: <GuestOnly />,
    children: [
      { path: '/login', element: <LoginPage /> },
      { path: '/cadastro', element: <SignupPage /> },
    ],
  },
  {
    element: <RequireAuth />,
    children: [
      {
        element: <AppLayout />,
        children: [
          { index: true, element: <HomePage /> },
          {
            element: <RequireRole roles={AVAILABILITY_TOGGLERS} />,
            children: [
              { path: '/cardapio', element: <Navigate to="/cardapio/produtos" replace /> },
              { path: '/cardapio/:tab', lazy: page(() => import('../features/catalog/CatalogPage'), 'CatalogPage') },
            ],
          },
          {
            element: <RequireRole roles={ORDER_VIEWERS} />,
            children: [
              { path: '/pedidos', element: <OrdersBoardPage /> },
              { path: '/pedidos/historico', lazy: page(() => import('../features/orders/OrdersHistoryPage'), 'OrdersHistoryPage') },
              { path: '/cozinha', lazy: page(() => import('../features/kitchen/KitchenPage'), 'KitchenPage') },
              { path: '/impressoes', lazy: page(() => import('../features/printing/PrintJobsPage'), 'PrintJobsPage') },
            ],
          },
          {
            element: <RequireRole roles={ORDER_TAKERS} />,
            children: [{ path: '/pedidos/novo', element: <NewOrderPage /> }],
          },
          {
            element: <RequireRole roles={SETTINGS_MANAGERS} />,
            children: [
              { path: '/configuracoes/pagamentos', lazy: page(() => import('../features/settings/PaymentMethodsPage'), 'PaymentMethodsPage') },
              { path: '/configuracoes/taxas', lazy: page(() => import('../features/settings/DeliveryZonesPage'), 'DeliveryZonesPage') },
              { path: '/configuracoes/impressao', lazy: page(() => import('../features/printing/PrintingSettingsPage'), 'PrintingSettingsPage') },
              { path: '/configuracoes/integracoes', lazy: page(() => import('../features/integrations/IntegrationsPage'), 'IntegrationsPage') },
            ],
          },
          {
            element: <RequireRole roles={['OWNER']} />,
            children: [
              { path: '/configuracoes/loja', lazy: page(() => import('../features/settings/StoreSettingsPage'), 'StoreSettingsPage') },
              { path: '/configuracoes/equipe', lazy: page(() => import('../features/settings/UsersPage'), 'UsersPage') },
            ],
          },
        ],
      },
    ],
  },
  { path: '*', element: <Navigate to="/" replace /> },
];
