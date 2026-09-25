import { Navigate, type RouteObject } from 'react-router';
import { GuestOnly, RequireAuth, RequireRole } from '../features/auth/guards';
import { LoginPage } from '../features/auth/LoginPage';
import { SignupPage } from '../features/auth/SignupPage';
import { CatalogPage } from '../features/catalog/CatalogPage';
import { HomePage } from '../features/home/HomePage';
import { StoreSettingsPage } from '../features/settings/StoreSettingsPage';
import { UsersPage } from '../features/settings/UsersPage';
import { AVAILABILITY_TOGGLERS } from '../shared/lib/roles';
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
