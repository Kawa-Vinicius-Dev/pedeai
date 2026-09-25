import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { apiError } from '../../test/fixtures';
import { server } from '../../test/server';
import { api } from './client';
import { unwrap } from './errors';

const login = () => unwrap(api.POST('/api/auth/login', { body: { email: 'ana@example.com', password: 'senha-forte-1' } }));

describe('mensagens de erro da API', () => {
  it('usa a mensagem que a API mandou', async () => {
    server.use(
      http.post('/api/auth/login', () =>
        HttpResponse.json(apiError(401, 'E-mail ou senha incorretos.'), { status: 401 }),
      ),
    );

    await expect(login()).rejects.toThrow('E-mail ou senha incorretos.');
  });

  it('avisa que o servidor não está no ar quando a hospedagem responde 404 sem a API', async () => {
    // Assim responde a Vercel enquanto /api/* não aponta para uma API.
    server.use(
      http.post(
        '/api/auth/login',
        () => new HttpResponse('<!doctype html><h1>404 NOT_FOUND</h1>', { status: 404, headers: { 'Content-Type': 'text/html' } }),
      ),
    );

    await expect(login()).rejects.toThrow('Não foi possível falar com o servidor do PedeAí. Tente novamente mais tarde.');
  });

  it('diz que o servidor teve um problema quando responde 5xx', async () => {
    server.use(http.post('/api/auth/login', () => new HttpResponse('Bad Gateway', { status: 502 })));

    await expect(login()).rejects.toThrow('O servidor teve um problema. Tente novamente em instantes.');
  });
});
