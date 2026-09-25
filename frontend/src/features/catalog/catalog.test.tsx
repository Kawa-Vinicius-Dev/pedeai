import { screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { beforeEach, describe, expect, it } from 'vitest';
import type { Category, OptionChoice, PriceQuote } from '../../shared/api/types';
import {
  apiError,
  category,
  emptyMenu,
  MENU_IDS,
  type Menu,
  optionGroup,
  pizzeriaMenu,
  product,
  sector,
} from '../../test/fixtures';
import { loggedInAs, renderApp } from '../../test/render';
import { server, serveMenu } from '../../test/server';

describe('cardápio: produtos', () => {
  beforeEach(() => loggedInAs('OWNER'));

  it('lista os produtos por categoria, com preço e o setor de onde vieram', async () => {
    const menu = pizzeriaMenu();
    menu.categories.push(category({ id: MENU_IDS.desserts, name: 'Sobremesas', sortOrder: 2 }));
    serveMenu(menu);
    renderApp('/cardapio');

    const pizzaRow = (await screen.findByText('Pizza Grande')).closest('tr') as HTMLElement;
    expect(within(pizzaRow).getByText('Cozinha')).toBeInTheDocument();
    expect(within(pizzaRow).getByText('padrão da loja')).toBeInTheDocument();
    expect(within(pizzaRow).getByText('+ adicionais')).toBeInTheDocument();

    const sodaRow = screen.getByText('Refrigerante lata').closest('tr') as HTMLElement;
    expect(within(sodaRow).getByText('R$ 7,00')).toBeInTheDocument();
    expect(within(sodaRow).getByText('Bar')).toBeInTheDocument();
    expect(within(sodaRow).getByText('da categoria')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Bebidas' })).toBeInTheDocument();
    // Categoria ainda sem produto aparece para quem cadastra, com o atalho para pôr produto nela.
    expect(screen.getByRole('heading', { name: 'Sobremesas' })).toBeInTheDocument();
    expect(screen.getByText('Nenhum produto nesta categoria.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Novo produto em Sobremesas' })).toBeInTheDocument();
  });

  it('busca sem diferenciar acento e também pelo código PDV', async () => {
    serveMenu(pizzeriaMenu());
    const { user } = renderApp('/cardapio/produtos');

    const search = await screen.findByRole('textbox', { name: 'Buscar produto' });
    await user.type(search, 'refrigerãnte');
    expect(screen.queryByText('Pizza Grande')).not.toBeInTheDocument();
    expect(screen.getByText('Refrigerante lata')).toBeInTheDocument();

    await user.clear(search);
    await user.type(search, '500');
    expect(screen.getByText('Pizza Grande')).toBeInTheDocument();
    expect(screen.queryByText('Refrigerante lata')).not.toBeInTheDocument();
  });

  it('cadastra um produto com o preço digitado em reais e envia centavos', async () => {
    const menu = pizzeriaMenu();
    serveMenu(menu);
    let created: unknown;
    server.use(
      http.post('/api/products', async ({ request }) => {
        created = await request.json();
        const saved = product({
          id: '01a0d567-0000-7000-8000-0000000000d3',
          name: 'Pizza Broto',
          code: '501',
          priceCents: 3290,
          optionGroupIds: [],
        });
        menu.products.push(saved);
        return HttpResponse.json(saved, { status: 201 });
      }),
    );
    const { user } = renderApp('/cardapio/produtos');

    await user.click(await screen.findByRole('button', { name: 'Novo produto' }));
    const drawer = await screen.findByRole('dialog');
    await user.type(within(drawer).getByLabelText('Nome'), 'Pizza Broto');
    await user.click(within(drawer).getByRole('combobox', { name: 'Categoria' }));
    await user.click(await screen.findByRole('option', { name: 'Pizzas' }));
    // Digita como a pessoa faria: "32,90" passa por "32," e por "32,9" antes de ficar completo.
    await user.type(within(drawer).getByLabelText('Preço'), '32,90');
    expect(within(drawer).getByLabelText('Preço')).toHaveValue('R$ 32,90');
    await user.type(within(drawer).getByLabelText('Código PDV'), '501');
    await user.click(within(drawer).getByRole('button', { name: 'Salvar' }));

    await waitFor(() =>
      expect(created).toEqual({
        categoryId: MENU_IDS.pizzas,
        name: 'Pizza Broto',
        priceCents: 3290,
        code: '501',
        optionGroupIds: [],
        available: true,
        active: true,
      }),
    );
    expect(await screen.findByText('Pizza Broto')).toBeInTheDocument();
  });

  it('mostra no formulário o código PDV repetido devolvido pela API', async () => {
    serveMenu(pizzeriaMenu());
    server.use(
      http.post('/api/products', () =>
        HttpResponse.json(apiError(409, 'Já existe um produto com o código PDV 500.'), { status: 409 }),
      ),
    );
    const { user } = renderApp('/cardapio/produtos');

    await user.click(await screen.findByRole('button', { name: 'Novo produto em Pizzas' }));
    const drawer = await screen.findByRole('dialog');
    await user.type(within(drawer).getByLabelText('Nome'), 'Pizza Família');
    await user.type(within(drawer).getByLabelText('Preço'), '0');
    await user.type(within(drawer).getByLabelText('Código PDV'), '500');
    await user.click(within(drawer).getByRole('button', { name: 'Salvar' }));

    expect(await within(drawer).findByText('Já existe um produto com o código PDV 500.')).toBeInTheDocument();
  });

  it('exige nome, categoria e preço antes de enviar', async () => {
    serveMenu(pizzeriaMenu());
    const { user } = renderApp('/cardapio/produtos');

    await user.click(await screen.findByRole('button', { name: 'Novo produto' }));
    const drawer = await screen.findByRole('dialog');
    await user.click(within(drawer).getByRole('button', { name: 'Salvar' }));

    expect(await within(drawer).findByText('Informe o nome do produto.')).toBeInTheDocument();
    expect(within(drawer).getByText('Escolha a categoria.')).toBeInTheDocument();
    expect(within(drawer).getByText('Informe o preço (use 0 se o preço vem dos sabores).')).toBeInTheDocument();
  });

  it('pausa um produto que acabou', async () => {
    const menu = pizzeriaMenu();
    serveMenu(menu);
    let body: unknown;
    server.use(
      http.put('/api/products/:id/availability', async ({ request }) => {
        body = await request.json();
        return HttpResponse.json({ ...menu.products[0], available: false });
      }),
    );
    const { user } = renderApp('/cardapio/produtos');

    const toggle = await screen.findByRole('switch', { name: 'Pizza Grande à venda' });
    expect(toggle).toBeChecked();
    await user.click(toggle);

    await waitFor(() => expect(body).toEqual({ available: false }));
    expect(await screen.findByText('Pausado')).toBeInTheDocument();
    expect(screen.getByRole('switch', { name: 'Pizza Grande à venda' })).not.toBeChecked();
  });

  it('simula o preço da pizza meio a meio pelo sabor mais caro', async () => {
    serveMenu(pizzeriaMenu());
    const prices: Record<string, number> = { [MENU_IDS.calabresa]: 4590, [MENU_IDS.fourCheese]: 5290 };
    let lastBody: { quantity: number; options: OptionChoice[] } | undefined;
    server.use(
      http.post('/api/products/:id/price-quotes', async ({ request }) => {
        lastBody = (await request.json()) as { quantity: number; options: OptionChoice[] };
        if (lastBody.options.length === 0) {
          return HttpResponse.json(apiError(422, 'Escolha pelo menos 1 opção em Sabores.'), { status: 422 });
        }
        const unit = Math.max(...lastBody.options.map((option) => prices[option.optionId] ?? 0));
        const quote: PriceQuote = {
          productId: MENU_IDS.pizza,
          name: 'Pizza Grande',
          code: '500',
          sectorId: MENU_IDS.kitchen,
          quantity: lastBody.quantity,
          basePriceCents: 0,
          optionsPriceCents: unit,
          unitPriceCents: unit,
          totalCents: unit * lastBody.quantity,
          options: [],
        };
        return HttpResponse.json(quote);
      }),
    );
    const { user } = renderApp('/cardapio/produtos');

    await user.click(await screen.findByRole('button', { name: 'Simular preço de Pizza Grande' }));
    const dialog = await screen.findByRole('dialog');
    expect(await within(dialog).findByText('Escolha pelo menos 1 opção em Sabores.')).toBeInTheDocument();

    await user.click(within(dialog).getByRole('checkbox', { name: 'Calabresa' }));
    await user.click(within(dialog).getByRole('checkbox', { name: 'Quatro queijos' }));

    await waitFor(() =>
      expect(within(dialog).getByRole('row', { name: /Total \(1 un\.\)/ })).toHaveTextContent('R$ 52,90'),
    );
    expect(lastBody).toEqual({
      quantity: 1,
      options: [
        { optionId: MENU_IDS.calabresa, quantity: 1 },
        { optionId: MENU_IDS.fourCheese, quantity: 1 },
      ],
    });
  });

  it('com o cardápio vazio, mostra os passos na ordem', async () => {
    serveMenu(emptyMenu());
    renderApp('/cardapio');

    expect(await screen.findByText('Monte o cardápio em 4 passos')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ir para Setores' })).toHaveAttribute('href', '/cardapio/setores');
    expect(screen.getByRole('button', { name: 'Novo produto' })).toBeDisabled();
  });
});

describe('cardápio: adicionais', () => {
  beforeEach(() => loggedInAs('OWNER'));

  it('cadastra os sabores da pizza com a regra do maior valor', async () => {
    const menu: Menu = { ...pizzeriaMenu(), optionGroups: [] };
    serveMenu(menu);
    let created: unknown;
    server.use(
      http.post('/api/option-groups', async ({ request }) => {
        created = await request.json();
        const saved = optionGroup();
        menu.optionGroups.push(saved);
        return HttpResponse.json(saved, { status: 201 });
      }),
    );
    const { user } = renderApp('/cardapio/adicionais');

    await user.click(await screen.findByRole('button', { name: 'Novo grupo' }));
    const drawer = await screen.findByRole('dialog');
    await user.type(within(drawer).getByLabelText('Nome do grupo'), 'Sabores');
    await user.click(within(drawer).getByRole('radio', { name: 'Maior valor' }));
    const min = within(drawer).getByLabelText('Mínimo de escolhas');
    await user.clear(min);
    await user.type(min, '1');
    const max = within(drawer).getByLabelText('Máximo de escolhas');
    await user.clear(max);
    await user.type(max, '2');
    await user.type(within(drawer).getByLabelText('Nome da opção 1'), 'Calabresa');
    await user.type(within(drawer).getByLabelText('Preço da opção 1'), '45,90');
    await user.type(within(drawer).getByLabelText('Código PDV da opção 1'), '101');
    await user.click(within(drawer).getByRole('button', { name: 'Adicionar opção' }));
    await user.type(within(drawer).getByLabelText('Nome da opção 2'), 'Quatro queijos');
    await user.type(within(drawer).getByLabelText('Preço da opção 2'), '52,90');
    await user.click(within(drawer).getByRole('button', { name: 'Salvar' }));

    await waitFor(() =>
      expect(created).toEqual({
        name: 'Sabores',
        pricingRule: 'MAX',
        minChoices: 1,
        maxChoices: 2,
        active: true,
        options: [
          { name: 'Calabresa', priceCents: 4590, code: '101', available: true, active: true },
          { name: 'Quatro queijos', priceCents: 5290, available: true, active: true },
        ],
      }),
    );
    expect(await screen.findByRole('switch', { name: 'Quatro queijos à venda' })).toBeInTheDocument();
  });

  it('ao editar, mantém o id das opções e manda a nova ordem', async () => {
    const menu = pizzeriaMenu();
    serveMenu(menu);
    let updated: unknown;
    server.use(
      http.put('/api/option-groups/:id', async ({ request }) => {
        updated = await request.json();
        return HttpResponse.json(menu.optionGroups[0]);
      }),
    );
    const { user } = renderApp('/cardapio/adicionais');

    await user.click(await screen.findByRole('button', { name: 'Editar Sabores' }));
    const drawer = await screen.findByRole('dialog');
    await user.click(within(drawer).getByRole('button', { name: 'Descer opção 1' }));
    await user.click(within(drawer).getByRole('button', { name: 'Salvar' }));

    await waitFor(() =>
      expect(updated).toMatchObject({
        options: [
          { id: MENU_IDS.fourCheese, name: 'Quatro queijos', priceCents: 5290, code: '102' },
          { id: MENU_IDS.calabresa, name: 'Calabresa', priceCents: 4590, code: '101' },
        ],
      }),
    );
  });

  it('não deixa o mínimo passar do máximo', async () => {
    serveMenu(pizzeriaMenu());
    const { user } = renderApp('/cardapio/adicionais');

    await user.click(await screen.findByRole('button', { name: 'Novo grupo' }));
    const drawer = await screen.findByRole('dialog');
    await user.type(within(drawer).getByLabelText('Nome do grupo'), 'Borda');
    const min = within(drawer).getByLabelText('Mínimo de escolhas');
    await user.clear(min);
    await user.type(min, '3');
    await user.type(within(drawer).getByLabelText('Nome da opção 1'), 'Catupiry');
    await user.type(within(drawer).getByLabelText('Preço da opção 1'), '8');
    await user.click(within(drawer).getByRole('button', { name: 'Salvar' }));

    expect(await within(drawer).findByText('O mínimo não pode ser maior que o máximo.')).toBeInTheDocument();
  });
});

describe('cardápio: categorias e setores', () => {
  beforeEach(() => loggedInAs('OWNER'));

  it('muda a ordem das categorias', async () => {
    const menu = pizzeriaMenu();
    menu.categories.push(category({ id: MENU_IDS.desserts, name: 'Sobremesas', sortOrder: 2 }));
    serveMenu(menu);
    const puts: { id: string; body: unknown }[] = [];
    server.use(
      http.put('/api/categories/:id', async ({ request, params }) => {
        const body = (await request.json()) as Category;
        puts.push({ id: String(params.id), body });
        return HttpResponse.json({ ...menu.categories.find((current) => current.id === params.id), ...body });
      }),
    );
    const { user } = renderApp('/cardapio/categorias');

    await user.click(await screen.findByRole('button', { name: 'Descer Pizzas' }));

    await waitFor(() =>
      expect(puts).toEqual([
        { id: MENU_IDS.drinks, body: { name: 'Bebidas', defaultSectorId: MENU_IDS.bar, sortOrder: 0, active: true } },
        { id: MENU_IDS.pizzas, body: { name: 'Pizzas', sortOrder: 1, active: true } },
      ]),
    );
  });

  it('o primeiro setor já nasce como padrão da loja', async () => {
    const menu = emptyMenu();
    serveMenu(menu);
    let created: unknown;
    server.use(
      http.post('/api/sectors', async ({ request }) => {
        created = await request.json();
        const saved = sector();
        menu.sectors.push(saved);
        return HttpResponse.json(saved, { status: 201 });
      }),
    );
    const { user } = renderApp('/cardapio/setores');

    await user.click(await screen.findByRole('button', { name: 'Novo setor' }));
    const dialog = await screen.findByRole('dialog');
    const defaultSwitch = within(dialog).getByRole('switch', { name: /Setor padrão da loja/ });
    expect(defaultSwitch).toBeChecked();
    expect(defaultSwitch).toBeDisabled();
    await user.type(within(dialog).getByLabelText('Nome'), 'Cozinha');
    await user.click(within(dialog).getByRole('button', { name: 'Salvar' }));

    await waitFor(() => expect(created).toEqual({ name: 'Cozinha', defaultSector: true, active: true }));
    expect(await screen.findByText('Padrão')).toBeInTheDocument();
  });
});

describe('cardápio: permissões', () => {
  it('caixa pausa itens, mas não vê cadastro', async () => {
    loggedInAs('CASHIER');
    serveMenu(pizzeriaMenu());
    renderApp('/cardapio');

    expect(await screen.findByRole('switch', { name: 'Pizza Grande à venda' })).toBeEnabled();
    expect(screen.queryByRole('button', { name: 'Novo produto' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Editar Pizza Grande' })).not.toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Adicionais' })).toBeInTheDocument();
    expect(screen.queryByRole('tab', { name: 'Setores' })).not.toBeInTheDocument();
  });

  it('caixa que abre a aba de setores volta para os produtos', async () => {
    loggedInAs('CASHIER');
    serveMenu(pizzeriaMenu());
    const { router } = renderApp('/cardapio/setores');

    await screen.findByRole('switch', { name: 'Pizza Grande à venda' });
    expect(router.state.location.pathname).toBe('/cardapio/produtos');
  });

  it('garçom não tem o cardápio no menu', async () => {
    loggedInAs('WAITER');
    const { router } = renderApp('/cardapio');

    expect(await screen.findByRole('heading', { name: /Olá/ })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/');
    expect(screen.queryByRole('link', { name: 'Cardápio' })).not.toBeInTheDocument();
  });
});
