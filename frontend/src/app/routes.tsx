import { Navigate, type RouteObject } from 'react-router';
import { GuestOnly, RequireAuth, RequireRole } from '../features/auth/guards';
import { LoginPage } from '../features/auth/LoginPage';
import { SignupPage } from '../features/auth/SignupPage';
import { CatalogPage } from '../features/catalog/CatalogPage';
import { HomePage } from '../features/home/HomePage';
import { KitchenPage } from '../features/kitchen/KitchenPage';
import { NewOrderPage } from '../features/orders/NewOrderPage';
import { OrdersBoardPage } from '../features/orders/OrdersBoardPage';
import { OrdersHistoryPage } from '../features/orders/OrdersHistoryPage';
import { DeliveryZonesPage } from '../features/settings/DeliveryZonesPage';
import { PaymentMethodsPage } from '../features/settings/PaymentMethodsPage';
import { StoreSettingsPage } from '../features/settings/StoreSettingsPage';
import { UsersPage } from '../features/settings/UsersPage';
import { AVAILABILITY_TOGGLERS, ORDER_TAKERS, ORDER_VIEWERS, SETTINGS_MANAGERS } from '../shared/lib/roles';
import { AppLayout } from './AppLayout';

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
              { path: '/cardapio/:tab', element: <CatalogPage /> },
            ],
          },
          {
            element: <RequireRole roles={ORDER_VIEWERS} />,
            children: [
              { path: '/pedidos', element: <OrdersBoardPage /> },
              { path: '/pedidos/historico', element: <OrdersHistoryPage /> },
              { path: '/cozinha', element: <KitchenPage /> },
            ],
          },
          {
            element: <RequireRole roles={ORDER_TAKERS} />,
            children: [{ path: '/pedidos/novo', element: <NewOrderPage /> }],
          },
          {
            element: <RequireRole roles={SETTINGS_MANAGERS} />,
            children: [
              { path: '/configuracoes/pagamentos', element: <PaymentMethodsPage /> },
              { path: '/configuracoes/taxas', element: <DeliveryZonesPage /> },
            ],
          },
          {
            element: <RequireRole roles={['OWNER']} />,
            children: [
              { path: '/configuracoes/loja', element: <StoreSettingsPage /> },
              { path: '/configuracoes/equipe', element: <UsersPage /> },
            ],
          },
        ],
      },
    ],
  },
  { path: '*', element: <Navigate to="/" replace /> },
];
