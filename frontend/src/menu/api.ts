import { keepPreviousData, useMutation, useQuery } from '@tanstack/react-query';
import { api } from '../shared/api/client';
import { unwrap } from '../shared/api/errors';
import type { MenuOrderRequest, OptionChoice } from '../shared/api/types';

/** O cardápio da loja. Atualiza a cada minuto: item pausado e loja fechando aparecem sem recarregar. */
export function useStorefront(slug: string) {
  return useQuery({
    queryKey: ['storefront', slug],
    queryFn: () => unwrap(api.GET('/api/public/stores/{slug}', { params: { path: { slug } } })),
    refetchInterval: 60_000,
  });
}

/** Preço do item montado, pela mesma conta que o pedido vai usar. */
export function usePublicQuote(
  slug: string,
  productId: string,
  quantity: number,
  options: OptionChoice[],
  enabled: boolean,
) {
  return useQuery({
    queryKey: ['storefront', slug, 'quote', productId, quantity, options],
    queryFn: () =>
      unwrap(
        api.POST('/api/public/stores/{slug}/products/{productId}/price-quotes', {
          params: { path: { slug, productId } },
          body: { quantity, options },
        }),
      ),
    placeholderData: keepPreviousData,
    enabled,
  });
}

export function usePlaceOrder(slug: string) {
  return useMutation({
    mutationFn: (body: MenuOrderRequest) =>
      unwrap(api.POST('/api/public/stores/{slug}/orders', { params: { path: { slug } }, body })),
  });
}

/** Acompanhamento: confere a cada 15 s até o pedido terminar. */
export function useTracking(code: string) {
  return useQuery({
    queryKey: ['tracking', code],
    queryFn: () => unwrap(api.GET('/api/public/orders/{trackingCode}', { params: { path: { trackingCode: code } } })),
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      return status === 'COMPLETED' || status === 'CANCELLED' ? false : 15_000;
    },
  });
}
