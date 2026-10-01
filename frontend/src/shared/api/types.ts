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

export type Order = Schemas['OrderResponse'];
export type OrderSummary = Schemas['OrderSummaryResponse'];
export type OrderItem = Schemas['OrderItemResponse'];
export type OrderStatus = Order['status'];
export type OrderType = Order['type'];
export type OrderHistoryEntry = Schemas['OrderStatusHistoryResponse'];
export type OrdersPage = Schemas['PageResponseOrderSummaryResponse'];
export type CreateOrderRequest = Schemas['CreateOrderRequest'];
export type Customer = Schemas['CustomerResponse'];
export type CustomerAddress = Schemas['CustomerAddressResponse'];
export type AddressRequest = Schemas['AddressRequest'];
export type DeliveryZone = Schemas['DeliveryZoneResponse'];
export type DeliveryZoneRequest = Schemas['DeliveryZoneRequest'];
export type PaymentMethod = Schemas['PaymentMethodResponse'];
export type PaymentMethodRequest = Schemas['PaymentMethodRequest'];
export type PaymentMethodType = PaymentMethod['type'];
export type Payment = Schemas['PaymentResponse'];

export type TicketDocument = Schemas['TicketResponse'];
export type PrintAgent = Schemas['PrintAgentResponse'];
export type PairingCode = Schemas['PairingCodeResponse'];
export type Printer = Schemas['PrinterResponse'];
export type PrinterRequest = Schemas['PrinterRequest'];
export type SectorPrinter = Schemas['SectorPrinterResponse'];
export type SectorPrinterRequest = Schemas['SectorPrinterRequest'];
export type PrintJob = Schemas['PrintJobResponse'];
export type PrintAlert = Schemas['PrintAlertResponse'];
export type MarketplaceConnection = Schemas['ConnectionResponse'];
export type IfoodSetup = Schemas['IfoodSetupResponse'];
export type Merchant = Schemas['MerchantResponse'];
export type CancellationReason = Schemas['CancellationReasonResponse'];
export type MarketplaceAction = Schemas['OutboundActionResponse'];
export type Dispute = Schemas['DisputeResponse'];

export type CashSession = Schemas['CashSessionResponse'];
export type CashLine = Schemas['CashLineResponse'];
export type CashMovement = Schemas['CashMovementResponse'];
export type CashSessionSummary = Schemas['CashSessionSummaryResponse'];
export type Dashboard = Schemas['DashboardResponse'];
export type Revenue = Schemas['RevenueResponse'];
export type RevenueSummary = Schemas['RevenueSummaryResponse'];
export type RevenueGroup = Schemas['RevenueGroupResponse'];

export type Storefront = Schemas['StorefrontResponse'];
export type MenuProduct = Schemas['MenuProductResponse'];
export type MenuOrderRequest = Schemas['MenuOrderRequest'];
export type OrderTracking = Schemas['OrderTrackingResponse'];
export type OpeningHours = Schemas['OpeningHoursResponse'];
export type ApiKey = Schemas['ApiKeyResponse'];
export type CatalogImport = Schemas['CatalogImportResponse'];
export type Platform = Schemas['PlatformResponse'];
