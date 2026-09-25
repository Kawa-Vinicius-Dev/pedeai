import type { ApiErrorBody } from '../shared/api/errors';
import type { AuthResponse, Role, Store, User } from '../shared/api/types';

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
