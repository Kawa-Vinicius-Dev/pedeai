import { MantineProvider } from '@mantine/core';
import { Notifications } from '@mantine/notifications';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { type ReactNode, useState } from 'react';
import { AuthProvider } from '../features/auth/AuthProvider';
import { ApiRequestError } from '../shared/api/errors';
import { theme } from './theme';

function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        // Erro 4xx não melhora tentando de novo; falha de rede e 5xx tentam mais uma vez.
        retry: (failureCount, error) =>
          failureCount < 1 && !(error instanceof ApiRequestError && error.status < 500),
        refetchOnWindowFocus: false,
      },
    },
  });
}

interface ProvidersProps {
  children: ReactNode;
  queryClient?: QueryClient;
  /** Nos testes: sem animações e sem portais, para os componentes aparecerem na hora. */
  env?: 'default' | 'test';
}

export function Providers({ children, queryClient, env = 'default' }: ProvidersProps) {
  const [client] = useState(() => queryClient ?? createQueryClient());
  return (
    <MantineProvider theme={theme} env={env}>
      <Notifications position="top-right" />
      <QueryClientProvider client={client}>
        <AuthProvider>{children}</AuthProvider>
      </QueryClientProvider>
    </MantineProvider>
  );
}
