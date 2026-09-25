import { notifications } from '@mantine/notifications';
import { type QueryClient, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../shared/api/client';
import { errorMessage, unwrap } from '../../shared/api/errors';
import type { Category, OptionChoice, OptionGroup, Product, Sector } from '../../shared/api/types';

/** Tudo do cardápio fica sob ['catalog'], para uma alteração invalidar o conjunto de uma vez. */
export const catalogKeys = {
  all: ['catalog'] as const,
  sectors: ['catalog', 'sectors'] as const,
  categories: ['catalog', 'categories'] as const,
  optionGroups: ['catalog', 'option-groups'] as const,
  products: ['catalog', 'products'] as const,
  priceQuote: (productId: string, quantity: number, options: OptionChoice[]) =>
    ['catalog', 'price-quote', productId, quantity, options] as const,
};

export function useSectors() {
  return useQuery({ queryKey: catalogKeys.sectors, queryFn: () => unwrap(api.GET('/api/sectors')) });
}

export function useCategories() {
  return useQuery({ queryKey: catalogKeys.categories, queryFn: () => unwrap(api.GET('/api/categories')) });
}

export function useOptionGroups() {
  return useQuery({ queryKey: catalogKeys.optionGroups, queryFn: () => unwrap(api.GET('/api/option-groups')) });
}

export function useProducts() {
  return useQuery({
    queryKey: catalogKeys.products,
    queryFn: () => unwrap(api.GET('/api/products')),
  });
}

export interface CatalogData {
  sectors: Sector[];
  categories: Category[];
  optionGroups: OptionGroup[];
  products: Product[];
}

/** As quatro listas juntas: as telas cruzam nomes de setor, categoria e grupo de adicionais. */
export function useCatalog(): { data: CatalogData | undefined; error: Error | null } {
  const sectors = useSectors();
  const categories = useCategories();
  const optionGroups = useOptionGroups();
  const products = useProducts();
  const error = sectors.error ?? categories.error ?? optionGroups.error ?? products.error;
  const data =
    sectors.data && categories.data && optionGroups.data && products.data
      ? { sectors: sectors.data, categories: categories.data, optionGroups: optionGroups.data, products: products.data }
      : undefined;
  return { data, error };
}

/** Depois de salvar qualquer cadastro: setor padrão e setor da categoria mudam o setor efetivo dos produtos. */
export function invalidateCatalog(queryClient: QueryClient) {
  return queryClient.invalidateQueries({ queryKey: catalogKeys.all });
}

function replaceById<T extends { id: string }>(queryClient: QueryClient, queryKey: readonly unknown[], item: T) {
  queryClient.setQueryData<T[]>(queryKey, (list) =>
    list?.map((current) => (current.id === item.id ? item : current)),
  );
}

/** Pausar e liberar produto durante o serviço. */
export function useProductAvailability() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, available }: { id: string; available: boolean }) =>
      unwrap(api.PUT('/api/products/{id}/availability', { params: { path: { id } }, body: { available } })),
    onSuccess: (product) => {
      replaceById(queryClient, catalogKeys.products, product);
      notifications.show({
        color: product.available ? 'green' : 'yellow',
        message: product.available ? `${product.name} voltou para a venda.` : `${product.name} está pausado.`,
      });
    },
    onError: (error) => notifications.show({ color: 'red', message: errorMessage(error) }),
  });
}

/** Pausar e liberar uma opção (um sabor que acabou, por exemplo). */
export function useOptionAvailability() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ groupId, optionId, available }: { groupId: string; optionId: string; available: boolean }) =>
      unwrap(
        api.PUT('/api/option-groups/{groupId}/options/{optionId}/availability', {
          params: { path: { groupId, optionId } },
          body: { available },
        }),
      ),
    onSuccess: (group, { optionId }) => {
      replaceById(queryClient, catalogKeys.optionGroups, group);
      const option = group.options.find((current) => current.id === optionId);
      if (option) {
        notifications.show({
          color: option.available ? 'green' : 'yellow',
          message: option.available ? `${option.name} voltou para a venda.` : `${option.name} está pausado.`,
        });
      }
    },
    onError: (error) => notifications.show({ color: 'red', message: errorMessage(error) }),
  });
}
