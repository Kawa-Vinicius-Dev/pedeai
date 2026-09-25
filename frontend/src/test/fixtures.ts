import type { ApiErrorBody } from '../shared/api/errors';
import type {
  AuthResponse,
  Category,
  OptionGroup,
  OptionItem,
  Product,
  Role,
  Sector,
  Store,
  User,
} from '../shared/api/types';

export const STORE_ID = '01a0d567-0000-7000-8000-000000000001';

export function user(overrides: Partial<User> = {}): User {
  return {
    id: '01a0d567-0000-7000-8000-000000000002',
    name: 'Ana Souza',
    email: 'ana@example.com',
    role: 'OWNER',
    active: true,
    createdAt: '2026-09-24T12:00:00Z',
    ...overrides,
  };
}

export function store(overrides: Partial<Store> = {}): Store {
  return {
    id: STORE_ID,
    name: 'Pizzaria Bella',
    document: null,
    phone: null,
    timezone: 'America/Sao_Paulo',
    businessDayCutoff: '05:00:00',
    serviceFeeBp: 1000,
    autoConfirmOwnOrders: true,
    startPreparationOnConfirm: false,
    ...overrides,
  };
}

export function authResponse(role: Role = 'OWNER', accessToken = 'token-de-acesso'): AuthResponse {
  return {
    accessToken,
    tokenType: 'Bearer',
    expiresIn: 900,
    user: user({ role }),
    store: { id: STORE_ID, name: 'Pizzaria Bella' },
  };
}

export function apiError(status: number, message: string, fields?: Record<string, string>): ApiErrorBody {
  return { timestamp: '2026-09-24T12:00:00Z', status, message, path: '/api', ...(fields ? { fields } : {}) };
}

/** Ids fixos do cardápio de exemplo (uma pizzaria). */
export const MENU_IDS = {
  kitchen: '01a0d567-0000-7000-8000-0000000000a1',
  bar: '01a0d567-0000-7000-8000-0000000000a2',
  pizzas: '01a0d567-0000-7000-8000-0000000000b1',
  drinks: '01a0d567-0000-7000-8000-0000000000b2',
  desserts: '01a0d567-0000-7000-8000-0000000000b3',
  flavors: '01a0d567-0000-7000-8000-0000000000c1',
  calabresa: '01a0d567-0000-7000-8000-0000000000c2',
  fourCheese: '01a0d567-0000-7000-8000-0000000000c3',
  pizza: '01a0d567-0000-7000-8000-0000000000d1',
  soda: '01a0d567-0000-7000-8000-0000000000d2',
};

export function sector(overrides: Partial<Sector> = {}): Sector {
  return { id: MENU_IDS.kitchen, name: 'Cozinha', defaultSector: true, active: true, ...overrides };
}

export function category(overrides: Partial<Category> = {}): Category {
  return { id: MENU_IDS.pizzas, name: 'Pizzas', defaultSectorId: null, sortOrder: 0, active: true, ...overrides };
}

export function optionItem(overrides: Partial<OptionItem> = {}): OptionItem {
  return {
    id: MENU_IDS.calabresa,
    code: '101',
    name: 'Calabresa',
    priceCents: 4590,
    available: true,
    active: true,
    ...overrides,
  };
}

export function optionGroup(overrides: Partial<OptionGroup> = {}): OptionGroup {
  return {
    id: MENU_IDS.flavors,
    name: 'Sabores',
    minChoices: 1,
    maxChoices: 2,
    pricingRule: 'MAX',
    active: true,
    options: [
      optionItem(),
      optionItem({ id: MENU_IDS.fourCheese, code: '102', name: 'Quatro queijos', priceCents: 5290 }),
    ],
    ...overrides,
  };
}

export function product(overrides: Partial<Product> = {}): Product {
  return {
    id: MENU_IDS.pizza,
    categoryId: MENU_IDS.pizzas,
    code: '500',
    name: 'Pizza Grande',
    description: '8 fatias',
    priceCents: 0,
    sectorId: null,
    effectiveSectorId: MENU_IDS.kitchen,
    optionGroupIds: [MENU_IDS.flavors],
    available: true,
    active: true,
    ...overrides,
  };
}

export interface Menu {
  sectors: Sector[];
  categories: Category[];
  optionGroups: OptionGroup[];
  products: Product[];
}

/** Pizza meio a meio na Cozinha (padrão da loja) e refrigerante no Bar (setor da categoria Bebidas). */
export function pizzeriaMenu(): Menu {
  return {
    sectors: [sector(), sector({ id: MENU_IDS.bar, name: 'Bar', defaultSector: false })],
    categories: [
      category(),
      category({ id: MENU_IDS.drinks, name: 'Bebidas', defaultSectorId: MENU_IDS.bar, sortOrder: 1 }),
    ],
    optionGroups: [optionGroup()],
    products: [
      product(),
      product({
        id: MENU_IDS.soda,
        categoryId: MENU_IDS.drinks,
        code: '900',
        name: 'Refrigerante lata',
        description: null,
        priceCents: 700,
        effectiveSectorId: MENU_IDS.bar,
        optionGroupIds: [],
      }),
    ],
  };
}

export function emptyMenu(): Menu {
  return { sectors: [], categories: [], optionGroups: [], products: [] };
}
