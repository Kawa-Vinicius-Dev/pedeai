import { QueryClient } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { Providers } from '../app/Providers';
import { routes } from '../app/routes';
import type { Role } from '../shared/api/types';
import { authResponse } from './fixtures';
import { server } from './server';

/** Renderiza o app inteiro (rotas, sessão e providers) começando no caminho informado. */
export function renderApp(path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const user = userEvent.setup();
  render(
    <Providers queryClient={queryClient} env="test">
      <RouterProvider router={router} />
    </Providers>,
  );
  return { user, router };
}

/** Faz a renovação de sessão responder como se a pessoa já estivesse logada. */
export function loggedInAs(role: Role) {
  server.use(http.post('/api/auth/refresh', () => HttpResponse.json(authResponse(role))));
}
