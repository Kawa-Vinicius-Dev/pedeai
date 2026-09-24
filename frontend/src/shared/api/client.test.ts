import { delay, http, HttpResponse } from 'msw';
import { describe, expect, it, vi } from 'vitest';
import { apiError, authResponse, store, user } from '../../test/fixtures';
import { server } from '../../test/server';
import { api, onSessionExpired, setAccessToken } from './client';

const unauthorized = () => HttpResponse.json(apiError(401, 'Faça login para continuar.'), { status: 401 });

describe('cliente da API', () => {
  it('envia o token de acesso nas chamadas da API', async () => {
    setAccessToken('token-atual');
    let authorization: string | null = null;
    server.use(
      http.get('/api/me', ({ request }) => {
        authorization = request.headers.get('Authorization');
        return HttpResponse.json({ user: user(), store: { id: 'x', name: 'Loja' } });
      }),
    );

    await api.GET('/api/me');

    expect(authorization).toBe('Bearer token-atual');
  });

  it('não envia token para as rotas de autenticação', async () => {
    setAccessToken('token-atual');
    let authorization: string | null = 'não chamado';
    server.use(
      http.post('/api/auth/logout', ({ request }) => {
        authorization = request.headers.get('Authorization');
        return new HttpResponse(null, { status: 204 });
      }),
    );

    await api.POST('/api/auth/logout');

    expect(authorization).toBeNull();
  });

  it('renova a sessão uma vez só e repete as chamadas quando o token vence', async () => {
    setAccessToken('vencido');
    let refreshCalls = 0;
    const withFreshToken = (body: object) =>
      ({ request }: { request: Request }) =>
        request.headers.get('Authorization') === 'Bearer novo' ? HttpResponse.json(body) : unauthorized();
    server.use(
      http.post('/api/auth/refresh', async () => {
        refreshCalls += 1;
        await delay(30);
        return HttpResponse.json(authResponse('OWNER', 'novo'));
      }),
      http.get('/api/me', withFreshToken({ user: user(), store: { id: 'x', name: 'Loja' } })),
      http.get('/api/store', withFreshToken(store())),
    );

    const [me, currentStore] = await Promise.all([api.GET('/api/me'), api.GET('/api/store')]);

    expect(me.response.status).toBe(200);
    expect(currentStore.data?.name).toBe('Pizzaria Bella');
    expect(refreshCalls).toBe(1);
  });

  it('avisa que a sessão acabou quando a renovação falha', async () => {
    setAccessToken('vencido');
    const expired = vi.fn();
    const unsubscribe = onSessionExpired(expired);
    server.use(http.get('/api/me', unauthorized));

    const { response } = await api.GET('/api/me');

    expect(response.status).toBe(401);
    expect(expired).toHaveBeenCalledOnce();
    unsubscribe();
  });
});
