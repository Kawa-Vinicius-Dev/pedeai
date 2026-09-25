import { screen, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { apiError, authResponse } from '../../test/fixtures';
import { loggedInAs, renderApp } from '../../test/render';
import { server } from '../../test/server';

describe('autenticação', () => {
  it('manda para o login quem não está logado', async () => {
    renderApp('/');

    expect(await screen.findByRole('heading', { name: 'Entrar' })).toBeInTheDocument();
  });

  it('faz login e abre o início com a loja', async () => {
    let body: unknown;
    server.use(
      http.post('/api/auth/login', async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(authResponse());
      }),
    );
    const { user } = renderApp('/login');

    await user.type(await screen.findByLabelText('E-mail'), 'ana@example.com');
    await user.type(screen.getByLabelText('Senha', { selector: 'input' }), 'senha-forte-1');
    await user.click(screen.getByRole('button', { name: 'Entrar' }));

    expect(await screen.findByRole('heading', { name: 'Olá, Ana!' })).toBeInTheDocument();
    expect(screen.getByText('Você está em Pizzaria Bella.')).toBeInTheDocument();
    expect(body).toEqual({ email: 'ana@example.com', password: 'senha-forte-1' });
  });

  it('mostra a mensagem da API quando a senha está errada', async () => {
    server.use(
      http.post('/api/auth/login', () =>
        HttpResponse.json(apiError(401, 'E-mail ou senha inválidos.'), { status: 401 }),
      ),
    );
    const { user } = renderApp('/login');

    await user.type(await screen.findByLabelText('E-mail'), 'ana@example.com');
    await user.type(screen.getByLabelText('Senha', { selector: 'input' }), 'errada-123');
    await user.click(screen.getByRole('button', { name: 'Entrar' }));

    expect(await screen.findByText('E-mail ou senha inválidos.')).toBeInTheDocument();
  });

  it('valida o cadastro antes de enviar', async () => {
    let called = false;
    server.use(
      http.post('/api/stores', () => {
        called = true;
        return HttpResponse.json(authResponse(), { status: 201 });
      }),
    );
    const { user } = renderApp('/cadastro');

    await user.type(await screen.findByLabelText('Nome da loja'), 'Pizzaria Bella');
    await user.type(screen.getByLabelText('Seu nome'), 'Ana');
    await user.type(screen.getByLabelText('E-mail'), 'ana@example.com');
    await user.type(screen.getByLabelText('Senha', { selector: 'input' }), 'curta');
    await user.type(screen.getByLabelText('Confirme a senha', { selector: 'input' }), 'outra');
    await user.click(screen.getByRole('button', { name: 'Criar conta' }));

    expect(await screen.findByText('A senha deve ter pelo menos 8 caracteres.')).toBeInTheDocument();
    expect(screen.getByText('As senhas não conferem.')).toBeInTheDocument();
    expect(called).toBe(false);
  });

  it('cadastra a loja e já entra', async () => {
    let body: unknown;
    server.use(
      http.post('/api/stores', async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(authResponse(), { status: 201 });
      }),
    );
    const { user } = renderApp('/cadastro');

    await user.type(await screen.findByLabelText('Nome da loja'), 'Pizzaria Bella');
    await user.type(screen.getByLabelText('Seu nome'), 'Ana Souza');
    await user.type(screen.getByLabelText('E-mail'), 'ana@example.com');
    await user.type(screen.getByLabelText('Senha', { selector: 'input' }), 'senha-forte-1');
    await user.type(screen.getByLabelText('Confirme a senha', { selector: 'input' }), 'senha-forte-1');
    await user.click(screen.getByRole('button', { name: 'Criar conta' }));

    expect(await screen.findByRole('heading', { name: 'Olá, Ana!' })).toBeInTheDocument();
    expect(body).toEqual({
      storeName: 'Pizzaria Bella',
      ownerName: 'Ana Souza',
      email: 'ana@example.com',
      password: 'senha-forte-1',
    });
  });

  it('não mostra configurações para quem não é dono', async () => {
    loggedInAs('CASHIER');
    renderApp('/configuracoes/equipe');

    expect(await screen.findByRole('heading', { name: 'Olá, Ana!' })).toBeInTheDocument();
    expect(screen.queryByText('Configurações')).not.toBeInTheDocument();
  });

  it('sai e volta para o login', async () => {
    loggedInAs('OWNER');
    server.use(http.post('/api/auth/logout', () => new HttpResponse(null, { status: 204 })));
    const { user } = renderApp('/');

    await user.click(await screen.findByRole('button', { name: 'Sair' }));

    await waitFor(() => expect(screen.getByRole('heading', { name: 'Entrar' })).toBeInTheDocument());
  });
});
