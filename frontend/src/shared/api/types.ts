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

export type Sector = Schemas['SectorResponse'];
export type SectorRequest = Schemas['SectorRequest'];
export type Category = Schemas['CategoryResponse'];
export type CategoryRequest = Schemas['CategoryRequest'];
export type OptionGroup = Schemas['OptionGroupResponse'];
export type OptionGroupRequest = Schemas['OptionGroupRequest'];
export type OptionItem = Schemas['OptionItemResponse'];
export type OptionItemRequest = Schemas['OptionItemRequest'];
export type PricingRule = OptionGroup['pricingRule'];
export type Product = Schemas['ProductResponse'];
export type ProductRequest = Schemas['ProductRequest'];
export type OptionChoice = Schemas['OptionChoice'];
export type PriceQuote = Schemas['PriceQuoteResponse'];
