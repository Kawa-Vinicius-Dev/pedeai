import { createContext, useContext } from 'react';
import type { RegisterStoreRequest, StoreSummary, User } from '../../shared/api/types';

export type AuthState =
  | { status: 'loading' }
  | { status: 'anonymous' }
  | { status: 'authenticated'; user: User; store: StoreSummary };

export interface AuthContextValue {
  state: AuthState;
  login(email: string, password: string): Promise<void>;
  signup(request: RegisterStoreRequest): Promise<void>;
  logout(): Promise<void>;
  updateStoreName(name: string): void;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth precisa estar dentro de <AuthProvider>.');
  }
  return context;
}

/** Atalho para telas que só existem com alguém logado. */
export function useSession(): { user: User; store: StoreSummary } {
  const { state } = useAuth();
  if (state.status !== 'authenticated') {
    throw new Error('useSession usado fora de uma rota autenticada.');
  }
  return { user: state.user, store: state.store };
}
