import type { components } from './schema';

type Schemas = components['schemas'];

export type AuthResponse = Schemas['AuthResponse'];
export type User = Schemas['UserResponse'];
export type Role = User['role'];
export type StoreSummary = Schemas['StoreSummaryResponse'];
export type Store = Schemas['StoreResponse'];
export type UpdateStoreRequest = Schemas['UpdateStoreRequest'];
export type CreateUserRequest = Schemas['CreateUserRequest'];
export type UpdateUserRequest = Schemas['UpdateUserRequest'];
export type RegisterStoreRequest = Schemas['RegisterStoreRequest'];
