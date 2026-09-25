import { zodResolver } from '@hookform/resolvers/zod';
import {
  ActionIcon,
  Alert,
  Badge,
  Button,
  Card,
  Group,
  Loader,
  Modal,
  Select,
  Stack,
  Switch,
  Table,
  Text,
  TextInput,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { ArrowDown, ArrowUp, CircleAlert, Pencil, Plus } from 'lucide-react';
import { useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { z } from 'zod';
import { api } from '../../shared/api/client';
import { errorMessage, unwrap } from '../../shared/api/errors';
import type { Category, CategoryRequest, Sector } from '../../shared/api/types';
import { applyApiError } from '../../shared/lib/forms';
import { invalidateCatalog, useCatalog } from './api';

export function CategoriesTab() {
  const queryClient = useQueryClient();
  const { data: catalog, error } = useCatalog();
  const [editing, setEditing] = useState<Category | 'new' | null>(null);

  // Troca a posição com a vizinha. Renumera o que estiver fora de ordem, então funciona mesmo com
  // posições repetidas ou com buracos.
  const move = useMutation({
    mutationFn: async ({ categories, from, to }: { categories: Category[]; from: number; to: number }) => {
      const reordered = [...categories];
      const [moved] = reordered.splice(from, 1);
      reordered.splice(to, 0, moved);
      for (const [position, category] of reordered.entries()) {
        if (category.sortOrder !== position) {
          await unwrap(
            api.PUT('/api/categories/{id}', {
              params: { path: { id: category.id } },
              body: toRequest(category, position),
            }),
          );
        }
      }
    },
    onError: (moveError) => notifications.show({ color: 'red', message: errorMessage(moveError) }),
    onSettled: () => invalidateCatalog(queryClient),
  });

  if (!catalog) {
    return error ? (
      <Alert color="red" icon={<CircleAlert size={18} />}>
        {errorMessage(error)}
      </Alert>
    ) : (
      <Loader aria-label="Carregando categorias" />
    );
  }

  const { categories, sectors, products } = catalog;
  const storeDefault = sectors.find((sector) => sector.defaultSector);

  return (
    <Stack>
      <Group justify="space-between" align="flex-end">
        <Text c="dimmed" size="sm" maw={560}>
          A ordem aqui é a ordem do cardápio. O setor da categoria vale para os produtos dela que não têm setor
          próprio.
        </Text>
        <Button leftSection={<Plus size={18} />} onClick={() => setEditing('new')}>
          Nova categoria
        </Button>
      </Group>

      {categories.length === 0 ? (
        <Text c="dimmed">Nenhuma categoria ainda. Comece por Pizzas, Lanches, Bebidas...</Text>
      ) : (
        <Card withBorder radius="lg" padding={0}>
          <Table.ScrollContainer minWidth={560}>
            <Table verticalSpacing="sm" highlightOnHover>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th w={90}>Ordem</Table.Th>
                  <Table.Th>Categoria</Table.Th>
                  <Table.Th>Setor</Table.Th>
                  <Table.Th>Produtos</Table.Th>
                  <Table.Th />
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {categories.map((category, index) => {
                  const sector = sectors.find((current) => current.id === category.defaultSectorId);
                  const count = products.filter((product) => product.categoryId === category.id && product.active).length;
                  return (
                    <Table.Tr key={category.id} opacity={category.active ? 1 : 0.6}>
                      <Table.Td>
                        <Group gap={2} wrap="nowrap">
                          <ActionIcon
                            variant="subtle"
                            color="gray"
                            aria-label={`Subir ${category.name}`}
                            disabled={index === 0 || move.isPending}
                            onClick={() => move.mutate({ categories, from: index, to: index - 1 })}
                          >
                            <ArrowUp size={16} />
                          </ActionIcon>
                          <ActionIcon
                            variant="subtle"
                            color="gray"
                            aria-label={`Descer ${category.name}`}
                            disabled={index === categories.length - 1 || move.isPending}
                            onClick={() => move.mutate({ categories, from: index, to: index + 1 })}
                          >
                            <ArrowDown size={16} />
                          </ActionIcon>
                        </Group>
                      </Table.Td>
                      <Table.Td>
                        <Group gap="xs">
                          <Text fw={500}>{category.name}</Text>
                          {!category.active && (
                            <Badge size="sm" color="gray" variant="light">
                              Inativa
                            </Badge>
                          )}
                        </Group>
                      </Table.Td>
                      <Table.Td>
                        {sector ? (
                          <Text size="sm">{sector.name}</Text>
                        ) : (
                          <Text size="sm" c="dimmed">
                            {storeDefault ? `Padrão da loja (${storeDefault.name})` : 'Sem setor'}
                          </Text>
                        )}
                      </Table.Td>
                      <Table.Td>{count}</Table.Td>
                      <Table.Td ta="right">
                        <Button
                          variant="subtle"
                          size="xs"
                          leftSection={<Pencil size={14} />}
                          onClick={() => setEditing(category)}
                          aria-label={`Editar ${category.name}`}
                        >
                          Editar
                        </Button>
                      </Table.Td>
                    </Table.Tr>
                  );
                })}
              </Table.Tbody>
            </Table>
          </Table.ScrollContainer>
        </Card>
      )}

      <Modal
        opened={editing !== null}
        onClose={() => setEditing(null)}
        title={editing === 'new' ? 'Nova categoria' : editing ? `Editar ${editing.name}` : ''}
      >
        {editing && (
          <CategoryForm
            key={editing === 'new' ? 'new' : editing.id}
            category={editing === 'new' ? null : editing}
            sectors={sectors}
            onClose={() => setEditing(null)}
          />
        )}
      </Modal>
    </Stack>
  );
}

function toRequest(category: Category, sortOrder: number): CategoryRequest {
  return {
    name: category.name,
    defaultSectorId: category.defaultSectorId ?? undefined,
    sortOrder,
    active: category.active,
  };
}

const schema = z.object({
  name: z.string().trim().min(1, 'Informe o nome da categoria.').max(80, 'Use até 80 caracteres.'),
  defaultSectorId: z.string().nullable(),
  active: z.boolean(),
});

type CategoryFormValues = z.infer<typeof schema>;

function CategoryForm({
  category,
  sectors,
  onClose,
}: {
  category: Category | null;
  sectors: Sector[];
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const {
    register,
    control,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<CategoryFormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: category?.name ?? '',
      defaultSectorId: category?.defaultSectorId ?? null,
      active: category?.active ?? true,
    },
  });

  const save = useMutation({
    mutationFn: (form: CategoryFormValues) => {
      // Sem posição: a nova vai para o fim, e a editada mantém a sua.
      const body: CategoryRequest = {
        name: form.name,
        defaultSectorId: form.defaultSectorId ?? undefined,
        active: form.active,
      };
      return category
        ? unwrap(api.PUT('/api/categories/{id}', { params: { path: { id: category.id } }, body }))
        : unwrap(api.POST('/api/categories', { body }));
    },
    onSuccess: async (saved) => {
      await invalidateCatalog(queryClient);
      notifications.show({ color: 'green', message: `Categoria ${saved.name} salva.` });
      onClose();
    },
    onError: (saveError) => applyApiError(saveError, setError),
  });

  const storeDefault = sectors.find((sector) => sector.defaultSector);
  const sectorOptions = sectors
    .filter((sector) => sector.active || sector.id === category?.defaultSectorId)
    .map((sector) => ({ value: sector.id, label: sector.name }));

  return (
    <form onSubmit={handleSubmit((form) => save.mutate(form))} noValidate>
      <Stack>
        {errors.root && (
          <Alert color="red" icon={<CircleAlert size={18} />}>
            {errors.root.message}
          </Alert>
        )}
        <TextInput label="Nome" data-autofocus {...register('name')} error={errors.name?.message} />
        <Controller
          control={control}
          name="defaultSectorId"
          render={({ field }) => (
            <Select
              label="Setor de produção"
              description="Ex.: Bebidas vão para o Bar."
              placeholder={storeDefault ? `Usar o padrão da loja: ${storeDefault.name}` : 'Nenhum setor cadastrado'}
              data={sectorOptions}
              value={field.value}
              onChange={field.onChange}
              clearable
            />
          )}
        />
        {category && (
          <Controller
            control={control}
            name="active"
            render={({ field }) => (
              <Switch
                label="Categoria ativa"
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
