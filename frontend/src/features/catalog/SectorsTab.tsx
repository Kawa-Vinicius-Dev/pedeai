import { zodResolver } from '@hookform/resolvers/zod';
import { Alert, Badge, Button, Card, Group, Loader, Modal, Stack, Switch, Table, Text, TextInput } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { CircleAlert, Pencil, Plus } from 'lucide-react';
import { useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { z } from 'zod';
import { api } from '../../shared/api/client';
import { errorMessage, unwrap } from '../../shared/api/errors';
import type { Sector, SectorRequest } from '../../shared/api/types';
import { applyApiError } from '../../shared/lib/forms';
import { invalidateCatalog, useCatalog } from './api';

export function SectorsTab() {
  const { data: catalog, error } = useCatalog();
  const [editing, setEditing] = useState<Sector | 'new' | null>(null);

  if (!catalog) {
    return error ? (
      <Alert color="red" icon={<CircleAlert size={18} />}>
        {errorMessage(error)}
      </Alert>
    ) : (
      <Loader aria-label="Carregando setores" />
    );
  }

  const { sectors, products } = catalog;

  return (
    <Stack>
      <Group justify="space-between" align="flex-end">
        <Text c="dimmed" size="sm" maw={560}>
          O setor diz onde cada item é preparado. Com ele, a bebida vai para o Bar e a pizza para a Cozinha, cada uma
          na sua tela e na sua impressora. O setor padrão recebe o que não tiver outro definido.
        </Text>
        <Button leftSection={<Plus size={18} />} onClick={() => setEditing('new')}>
          Novo setor
        </Button>
      </Group>

      {sectors.length === 0 ? (
        <Text c="dimmed">Nenhum setor ainda. A maioria das lojas começa com Cozinha e Bar.</Text>
      ) : (
        <Card withBorder radius="lg" padding={0}>
          <Table.ScrollContainer minWidth={480}>
            <Table verticalSpacing="sm" highlightOnHover>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>Setor</Table.Th>
                  <Table.Th>Produtos</Table.Th>
                  <Table.Th />
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {sectors.map((sector) => (
                  <Table.Tr key={sector.id} opacity={sector.active ? 1 : 0.6}>
                    <Table.Td>
                      <Group gap="xs">
                        <Text fw={500}>{sector.name}</Text>
                        {sector.defaultSector && (
                          <Badge size="sm" variant="light">
                            Padrão
                          </Badge>
                        )}
                        {!sector.active && (
                          <Badge size="sm" color="gray" variant="light">
                            Inativo
                          </Badge>
                        )}
                      </Group>
                    </Table.Td>
                    <Table.Td>
                      {products.filter((product) => product.active && product.effectiveSectorId === sector.id).length}
                    </Table.Td>
                    <Table.Td ta="right">
                      <Button
                        variant="subtle"
                        size="xs"
                        leftSection={<Pencil size={14} />}
                        onClick={() => setEditing(sector)}
                        aria-label={`Editar ${sector.name}`}
                      >
                        Editar
                      </Button>
                    </Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
          </Table.ScrollContainer>
        </Card>
      )}

      <Modal
        opened={editing !== null}
        onClose={() => setEditing(null)}
        title={editing === 'new' ? 'Novo setor' : editing ? `Editar ${editing.name}` : ''}
      >
        {editing && (
          <SectorForm
            key={editing === 'new' ? 'new' : editing.id}
            sector={editing === 'new' ? null : editing}
            firstSector={sectors.length === 0}
            onClose={() => setEditing(null)}
          />
        )}
      </Modal>
    </Stack>
  );
}

const schema = z.object({
  name: z.string().trim().min(1, 'Informe o nome do setor.').max(60, 'Use até 60 caracteres.'),
  defaultSector: z.boolean(),
  active: z.boolean(),
});

type SectorFormValues = z.infer<typeof schema>;

function SectorForm({
  sector,
  firstSector,
  onClose,
}: {
  sector: Sector | null;
  firstSector: boolean;
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const {
    register,
    control,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<SectorFormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: sector?.name ?? '',
      defaultSector: sector?.defaultSector ?? firstSector,
      active: sector?.active ?? true,
    },
  });

  const save = useMutation({
    mutationFn: (body: SectorRequest) =>
      sector
        ? unwrap(api.PUT('/api/sectors/{id}', { params: { path: { id: sector.id } }, body }))
        : unwrap(api.POST('/api/sectors', { body })),
    onSuccess: async (saved) => {
      await invalidateCatalog(queryClient);
      notifications.show({ color: 'green', message: `Setor ${saved.name} salvo.` });
      onClose();
    },
    onError: (saveError) => applyApiError(saveError, setError),
  });

  // A loja sempre tem um setor padrão: ele só deixa de ser quando outro é marcado no lugar.
  const lockedDefault = sector?.defaultSector === true || firstSector;

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
          name="defaultSector"
          render={({ field }) => (
            <Switch
              label="Setor padrão da loja"
              description={
                firstSector
                  ? 'O primeiro setor é o padrão.'
                  : lockedDefault
                    ? 'Para trocar, marque outro setor como padrão.'
                    : 'Recebe os itens sem setor definido.'
              }
              checked={field.value}
              disabled={lockedDefault}
              onChange={(event) => field.onChange(event.currentTarget.checked)}
            />
          )}
        />
        {sector && (
          <Controller
            control={control}
            name="active"
            render={({ field }) => (
              <Switch
                label="Setor ativo"
                description={sector.defaultSector ? 'O setor padrão não pode ser desativado.' : undefined}
                checked={field.value}
                disabled={sector.defaultSector}
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
