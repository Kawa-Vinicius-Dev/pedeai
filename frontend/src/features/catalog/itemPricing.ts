import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { api } from '../../shared/api/client';
import { unwrap } from '../../shared/api/errors';
import type { OptionChoice, OptionGroup, Product } from '../../shared/api/types';
import { catalogKeys } from './api';

/** Quantidade escolhida de cada opção, pelo id da opção. */
export type ChosenOptions = Record<string, number>;

/** Grupos ativos do produto, na ordem em que aparecem para o cliente. */
export function productGroups(product: Product, optionGroups: OptionGroup[]): OptionGroup[] {
  return product.optionGroupIds
    .map((id) => optionGroups.find((group) => group.id === id))
    .filter((group): group is OptionGroup => group !== undefined && group.active);
}

export function toChoices(chosen: ChosenOptions): OptionChoice[] {
  return Object.entries(chosen)
    .filter(([, quantity]) => quantity > 0)
    .map(([optionId, quantity]) => ({ optionId, quantity }));
}

/** Preço do item montado, calculado pela API: é a mesma conta que o pedido usa. */
export function usePriceQuote(productId: string, quantity: number, options: OptionChoice[]) {
  return useQuery({
    queryKey: catalogKeys.priceQuote(productId, quantity, options),
    queryFn: () =>
      unwrap(
        api.POST('/api/products/{id}/price-quotes', {
          params: { path: { id: productId } },
          body: { quantity, options },
        }),
      ),
    placeholderData: keepPreviousData,
  });
}
