import { MantineProvider } from '@mantine/core';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { type ReactNode, useState } from 'react';
import { theme } from '../app/theme';
import { ApiRequestError } from '../shared/api/errors';

/** Sem login e sem o código do painel: o cardápio abre rápido no celular do cliente. */
export function MenuProviders({
  children,
  queryClient,
  env = 'default',
}: {
  children: ReactNode;
  queryClient?: QueryClient;
  env?: 'default' | 'test';
}) {
  const [client] = useState(
    () =>
      queryClient ??
      new QueryClient({
        defaultOptions: {
          queries: {
            retry: (failureCount, error) =>
              failureCount < 1 && !(error instanceof ApiRequestError && error.status < 500),
            refetchOnWindowFocus: true,
          },
        },
      }),
  );
  return (
    <MantineProvider theme={theme} env={env}>
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    </MantineProvider>
  );
}
