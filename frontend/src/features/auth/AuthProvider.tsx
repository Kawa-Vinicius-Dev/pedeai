import { notifications } from '@mantine/notifications';
import { useQueryClient } from '@tanstack/react-query';
import { type ReactNode, useCallback, useEffect, useMemo, useState } from 'react';
import { api, onSessionExpired, refreshSession, setAccessToken } from '../../shared/api/client';
import { unwrap } from '../../shared/api/errors';
import type { AuthResponse, RegisterStoreRequest } from '../../shared/api/types';
import { AuthContext, type AuthContextValue, type AuthState } from './auth-context';

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [state, setState] = useState<AuthState>({ status: 'loading' });

  const startSession = useCallback((session: AuthResponse) => {
    setAccessToken(session.accessToken);
    setState({ status: 'authenticated', user: session.user, store: session.store });
  }, []);

  const endSession = useCallback(() => {
    setAccessToken(null);
    queryClient.clear();
    setState({ status: 'anonymous' });
  }, [queryClient]);

  // Ao abrir o app, tenta retomar a sessão pelo cookie de renovação.
  useEffect(() => {
    let cancelled = false;
    void refreshSession().then((session) => {
      if (cancelled) {
        return;
      }
      if (session) {
        startSession(session);
      } else {
        setState({ status: 'anonymous' });
      }
    });
    return () => {
      cancelled = true;
    };
  }, [startSession]);

  useEffect(
    () =>
      onSessionExpired(() => {
        endSession();
        notifications.show({
          color: 'orange',
          title: 'Sessão encerrada',
          message: 'Faça login novamente para continuar.',
        });
      }),
    [endSession],
  );

  const value = useMemo<AuthContextValue>(
    () => ({
      state,
      async login(email, password) {
        startSession(await unwrap(api.POST('/api/auth/login', { body: { email, password } })));
      },
      async signup(request: RegisterStoreRequest) {
        startSession(await unwrap(api.POST('/api/stores', { body: request })));
      },
      async logout() {
        try {
          await api.POST('/api/auth/logout');
        } finally {
          endSession();
        }
      },
      updateStoreName(name) {
        setState((current) =>
          current.status === 'authenticated' ? { ...current, store: { ...current.store, name } } : current,
        );
      },
    }),
    [state, startSession, endSession],
  );

  return <AuthContext value={value}>{children}</AuthContext>;
}
