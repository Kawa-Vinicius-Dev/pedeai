import { Navigate, Outlet, useLocation } from 'react-router';
import type { Role } from '../../shared/api/types';
import { FullPageLoader } from '../../shared/ui/FullPageLoader';
import { useAuth } from './auth-context';

/** Rotas internas: sem sessão, vai para o login e volta para cá depois. */
export function RequireAuth() {
  const { state } = useAuth();
  const location = useLocation();
  if (state.status === 'loading') {
    return <FullPageLoader />;
  }
  if (state.status === 'anonymous') {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  return <Outlet />;
}

/** Rotas de um papel específico. A API também barra (403); aqui só evita mostrar tela inútil. */
export function RequireRole({ roles }: { roles: Role[] }) {
  const { state } = useAuth();
  if (state.status !== 'authenticated' || !roles.includes(state.user.role)) {
    return <Navigate to="/" replace />;
  }
  return <Outlet />;
}

/** Login e cadastro: quem já está logado vai direto para o início. */
export function GuestOnly() {
  const { state } = useAuth();
  if (state.status === 'loading') {
    return <FullPageLoader />;
  }
  if (state.status === 'authenticated') {
    return <Navigate to="/" replace />;
  }
  return <Outlet />;
}
