import type { ApiErrorBody } from '../shared/api/errors';
import type {
  AuthResponse,
  Category,
  Customer,
  DeliveryZone,
  OptionGroup,
  OptionItem,
  Order,
  OrderHistoryEntry,
  OrderSummary,
  Payment,
  PaymentMethod,
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

/** Ids fixos de pedidos, clientes e pagamentos. */
export const ORDER_IDS = {
  cash: '01a0d567-0000-7000-8000-0000000000e1',
  pix: '01a0d567-0000-7000-8000-0000000000e2',
  downtown: '01a0d567-0000-7000-8000-0000000000e3',
  maria: '01a0d567-0000-7000-8000-0000000000e4',
  mariaHome: '01a0d567-0000-7000-8000-0000000000e5',
  order: '01a0d567-0000-7000-8000-0000000000f1',
  payment: '01a0d567-0000-7000-8000-0000000000f2',
};

export function paymentMethod(overrides: Partial<PaymentMethod> = {}): PaymentMethod {
  return { id: ORDER_IDS.cash, name: 'Dinheiro', type: 'CASH', active: true, ...overrides };
}

export function deliveryZone(overrides: Partial<DeliveryZone> = {}): DeliveryZone {
  return { id: ORDER_IDS.downtown, neighborhood: 'Centro', feeCents: 800, active: true, ...overrides };
}

export function customer(overrides: Partial<Customer> = {}): Customer {
  return {
    id: ORDER_IDS.maria,
    name: 'Maria Oliveira',
    phone: '+5511999990000',
    email: null,
    notes: null,
    addresses: [
      {
        id: ORDER_IDS.mariaHome,
        label: null,
        street: 'Rua das Flores',
        number: '120',
        complement: 'apto 3',
        neighborhood: 'Centro',
        city: null,
        state: null,
        postalCode: null,
        reference: 'Portão azul',
      },
    ],
    ...overrides,
  };
}

/** Pedido de delivery da Maria: pizza meio a meio (R$ 52,90), 2 refrigerantes (R$ 14,00) e taxa de R$ 8,00. */
export function order(overrides: Partial<Order> = {}): Order {
  return {
    id: ORDER_IDS.order,
    number: 12,
    businessDate: '2026-09-24',
    type: 'DELIVERY',
    source: 'PEDEAI',
    status: 'RECEIVED',
    customerId: ORDER_IDS.maria,
    customerName: 'Maria Oliveira',
    customerPhone: '+5511999990000',
    deliveryAddress: {
      street: 'Rua das Flores',
      number: '120',
      complement: 'apto 3',
      neighborhood: 'Centro',
      city: null,
      state: null,
      postalCode: null,
      reference: 'Portão azul',
    },
    notes: null,
    items: [
      {
        id: '01a0d567-0000-7000-8000-0000000000f3',
        productId: MENU_IDS.pizza,
        code: '500',
        name: 'Pizza Grande',
        sectorId: MENU_IDS.kitchen,
        quantity: 1,
        unitPriceCents: 0,
        optionsPriceCents: 5290,
        totalCents: 5290,
        notes: 'Sem cebola',
        status: 'ACTIVE',
        options: [
          { optionId: MENU_IDS.calabresa, groupName: 'Sabores', name: 'Calabresa', code: '101', quantity: 1, unitPriceCents: 4590 },
          { optionId: MENU_IDS.fourCheese, groupName: 'Sabores', name: 'Quatro queijos', code: '102', quantity: 1, unitPriceCents: 5290 },
        ],
      },
      {
        id: '01a0d567-0000-7000-8000-0000000000f4',
        productId: MENU_IDS.soda,
        code: '900',
        name: 'Refrigerante lata',
        sectorId: MENU_IDS.bar,
        quantity: 2,
        unitPriceCents: 700,
        optionsPriceCents: 0,
        totalCents: 1400,
        notes: null,
        status: 'ACTIVE',
        options: [],
      },
    ],
    subtotalCents: 6690,
    discountCents: 0,
    deliveryFeeCents: 800,
    additionalFeeCents: 0,
    platformSubsidyCents: 0,
    totalCents: 7490,
    createdAt: '2026-09-24T22:10:00Z',
    confirmedAt: null,
    preparationStartedAt: null,
    readyAt: null,
    dispatchedAt: null,
    completedAt: null,
    cancelledAt: null,
    cancelReason: null,
    version: 0,
    ...overrides,
  };
}

export function orderSummary(overrides: Partial<OrderSummary> = {}): OrderSummary {
  const full = order();
  return {
    id: full.id,
    number: full.number,
    businessDate: full.businessDate,
    type: full.type,
    source: full.source,
    status: full.status,
    customerName: full.customerName,
    deliveryNeighborhood: 'Centro',
    totalCents: full.totalCents,
    itemCount: 3,
    itemsSummary: '1x Pizza Grande, 2x Refrigerante lata',
    createdAt: full.createdAt,
    updatedAt: full.createdAt,
    version: full.version,
    ...overrides,
  };
}

/** "Troco para R$ 100" do pedido da Maria, a receber na entrega. */
export function payment(overrides: Partial<Payment> = {}): Payment {
  return {
    id: ORDER_IDS.payment,
    paymentMethodId: ORDER_IDS.cash,
    methodName: 'Dinheiro',
    methodType: 'CASH',
    amountCents: 7490,
    changeForCents: 10000,
    changeCents: 2510,
    status: 'PENDING',
    origin: 'LOCAL',
    paidAt: null,
    createdAt: '2026-09-24T22:10:00Z',
    ...overrides,
  };
}

export function historyEntry(overrides: Partial<OrderHistoryEntry> = {}): OrderHistoryEntry {
  return {
    fromStatus: null,
    toStatus: 'RECEIVED',
    actorType: 'USER',
    actorName: 'Ana Souza',
    reason: null,
    createdAt: '2026-09-24T22:10:00Z',
    ...overrides,
  };
}
