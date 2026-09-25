import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert,
  Button,
  Drawer,
  Group,
  MultiSelect,
  Select,
  SimpleGrid,
  Stack,
  Switch,
  Textarea,
  TextInput,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { CircleAlert } from 'lucide-react';
import { Controller, useForm, useWatch } from 'react-hook-form';
import { z } from 'zod';
import { api } from '../../shared/api/client';
import { unwrap } from '../../shared/api/errors';
import type { Product, ProductRequest } from '../../shared/api/types';
import { applyApiError } from '../../shared/lib/forms';
import { centsToReais, moneyField } from '../../shared/lib/numbers';
import { MoneyInput } from '../../shared/ui/MoneyInput';
import { type CatalogData, invalidateCatalog } from './api';

const schema = z.object({
  name: z.string().trim().min(1, 'Informe o nome do produto.').max(120, 'Use até 120 caracteres.'),
  categoryId: z.string().min(1, 'Escolha a categoria.'),
  price: moneyField('Informe o preço (use 0 se o preço vem dos sabores).'),
  code: z.string().trim().max(40, 'Use até 40 caracteres.'),
  description: z.string().trim().max(500, 'Use até 500 caracteres.'),
  sectorId: z.string().nullable(),
  optionGroupIds: z.array(z.string()).max(20, 'Use até 20 grupos de adicionais.'),
  available: z.boolean(),
  active: z.boolean(),
});

type ProductFormInput = z.input<typeof schema>;
type ProductForm = z.output<typeof schema>;

/** O que o drawer está editando: um produto existente ou um novo, já numa categoria. */
export type ProductTarget = { product: Product } | { categoryId: string | null };

function toForm(target: ProductTarget): ProductFormInput {
  if ('product' in target) {
    const { product } = target;
    return {
      name: product.name,
      categoryId: product.categoryId,
      price: centsToReais(product.priceCents),
      code: product.code ?? '',
      description: product.description ?? '',
      sectorId: product.sectorId,
      optionGroupIds: product.optionGroupIds,
      available: product.available,
      active: product.active,
    };
  }
  return {
    name: '',
    categoryId: target.categoryId ?? '',
    price: '',
    code: '',
    description: '',
    sectorId: null,
    optionGroupIds: [],
    available: true,
    active: true,
  };
}

function toRequest(form: ProductForm): ProductRequest {
  return {
    categoryId: form.categoryId,
    name: form.name,
    priceCents: form.price,
    code: form.code || undefined,
    description: form.description || undefined,
    sectorId: form.sectorId ?? undefined,
    optionGroupIds: form.optionGroupIds,
    available: form.available,
    active: form.active,
  };
}

export function ProductDrawer({
  target,
  catalog,
  onClose,
}: {
  target: ProductTarget | null;
  catalog: CatalogData;
  onClose: () => void;
}) {
  const editing = target !== null && 'product' in target ? target.product : null;
  return (
    <Drawer
      opened={target !== null}
      onClose={onClose}
      position="right"
      size="lg"
      title={editing ? `Editar ${editing.name}` : 'Novo produto'}
    >
      {target && <ProductFormBody key={editing?.id ?? 'new'} target={target} catalog={catalog} onClose={onClose} />}
    </Drawer>
  );
}

function ProductFormBody({
  target,
  catalog,
  onClose,
}: {
  target: ProductTarget;
  catalog: CatalogData;
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const product = 'product' in target ? target.product : null;
  const {
    register,
    control,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<ProductFormInput, unknown, ProductForm>({
    resolver: zodResolver(schema),
    defaultValues: toForm(target),
  });
  const categoryId = useWatch({ control, name: 'categoryId' });

  const save = useMutation({
    mutationFn: (body: ProductRequest) =>
      product
        ? unwrap(api.PUT('/api/products/{id}', { params: { path: { id: product.id } }, body }))
        : unwrap(api.POST('/api/products', { body })),
    onSuccess: async (saved) => {
      await invalidateCatalog(queryClient);
      notifications.show({ color: 'green', message: `${saved.name} salvo no cardápio.` });
      onClose();
    },
    onError: (error) => applyApiError(error, setError, { priceCents: 'price' }),
  });

  // Inativos só aparecem se já estão escolhidos, para não sumirem da tela ao editar.
  const categoryOptions = catalog.categories
    .filter((category) => category.active || category.id === product?.categoryId)
    .map((category) => ({ value: category.id, label: category.name }));
  const sectorOptions = catalog.sectors
    .filter((sector) => sector.active || sector.id === product?.sectorId)
    .map((sector) => ({ value: sector.id, label: sector.name }));
  const groupOptions = catalog.optionGroups
    .filter((group) => group.active || product?.optionGroupIds.includes(group.id))
    .map((group) => ({ value: group.id, label: group.name }));

  return (
    <form onSubmit={handleSubmit((form) => save.mutate(toRequest(form)))} noValidate>
      <Stack>
        {errors.root && (
          <Alert color="red" icon={<CircleAlert size={18} />}>
            {errors.root.message}
          </Alert>
        )}
        <TextInput label="Nome" data-autofocus {...register('name')} error={errors.name?.message} />
        <SimpleGrid cols={{ base: 1, sm: 2 }}>
          <Controller
            control={control}
            name="categoryId"
            render={({ field }) => (
              <Select
                label="Categoria"
                data={categoryOptions}
                value={field.value || null}
                onChange={(value) => field.onChange(value ?? '')}
                error={errors.categoryId?.message}
                allowDeselect={false}
                searchable
              />
            )}
          />
          <Controller
            control={control}
            name="price"
            render={({ field }) => (
              <MoneyInput
                label="Preço"
                description="Pizza cobrada pelo sabor: use 0."
                value={field.value}
                onChange={field.onChange}
                onBlur={field.onBlur}
                error={errors.price?.message}
              />
            )}
          />
        </SimpleGrid>
        <Textarea
          label="Descrição"
          description="Opcional. Aparece para quem monta o pedido."
          autosize
          minRows={2}
          maxRows={5}
          {...register('description')}
          error={errors.description?.message}
        />
        <SimpleGrid cols={{ base: 1, sm: 2 }}>
          <TextInput
            label="Código PDV"
            description="Liga o item do iFood e da 99Food a este produto."
            {...register('code')}
            error={errors.code?.message}
          />
          <Controller
            control={control}
            name="sectorId"
            render={({ field }) => (
              <Select
                label="Setor de produção"
                description="Para onde o item vai na cozinha e na impressão."
                placeholder={inheritedSectorLabel(catalog, categoryId)}
                data={sectorOptions}
                value={field.value}
                onChange={field.onChange}
                clearable
              />
            )}
          />
        </SimpleGrid>
        <Controller
          control={control}
          name="optionGroupIds"
          render={({ field }) => (
            <MultiSelect
              label="Adicionais"
              description="Grupos que o cliente escolhe, na ordem em que aparecem."
              placeholder={field.value.length === 0 ? 'Nenhum' : undefined}
              data={groupOptions}
              value={field.value}
              onChange={field.onChange}
              error={errors.optionGroupIds?.message}
              searchable
              clearable
            />
          )}
        />
        <Controller
          control={control}
          name="available"
          render={({ field }) => (
            <Switch
              label="À venda agora"
              description="Desligue quando acabar. Volta com um clique na lista."
              checked={field.value}
              onChange={(event) => field.onChange(event.currentTarget.checked)}
            />
          )}
        />
        {product && (
          <Controller
            control={control}
            name="active"
            render={({ field }) => (
              <Switch
                label="Ativo no cardápio"
                description="Desative em vez de apagar: pedidos antigos continuam apontando para o produto."
                checked={field.value}
                onChange={(event) => field.onChange(event.currentTarget.checked)}
              />
            )}
          />
        )}
        <Group justify="flex-end">
          <Button variant="default" onClick={onClose}>
            Cancelar
          </Button>
          <Button type="submit" loading={save.isPending}>
            Salvar
          </Button>
        </Group>
      </Stack>
    </form>
  );
}

/** "Usar o da categoria: Bar" ou "Usar o padrão da loja: Cozinha". */
function inheritedSectorLabel(catalog: CatalogData, categoryId: string): string {
  const category = catalog.categories.find((current) => current.id === categoryId);
  const categorySector = catalog.sectors.find((sector) => sector.id === category?.defaultSectorId);
  if (categorySector) {
    return `Usar o da categoria: ${categorySector.name}`;
  }
  const storeDefault = catalog.sectors.find((sector) => sector.defaultSector);
  return storeDefault ? `Usar o padrão da loja: ${storeDefault.name}` : 'Nenhum setor cadastrado';
}
