import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { apiError } from './fixtures';

/** API simulada. Por padrão ninguém está logado: a renovação de sessão responde 401. */
export const server = setupServer(
  http.post('/api/auth/refresh', () =>
    HttpResponse.json(apiError(401, 'Sua sessão expirou. Faça login novamente.'), { status: 401 }),
  ),
);
