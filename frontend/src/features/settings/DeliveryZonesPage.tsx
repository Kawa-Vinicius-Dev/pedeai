import { zodResolver } from '@hookform/resolvers/zod';
import { Alert, Badge, Button, Card, Group, Loader, Modal, Stack, Switch, Table, Text, TextInput, Title } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { CircleAlert, Pencil, Plus } from 'lucide-react';
import { useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { z } from 'zod';
import { api } from '../../shared/api/client';
import { errorMessage, unwrap } from '../../shared/api/errors';
import type { DeliveryZone, DeliveryZoneRequest } from '../../shared/api/types';
import { applyApiError } from '../../shared/lib/forms';
import { centsToReais, formatCents, moneyField } from '../../shared/lib/numbers';
import { MoneyInput } from '../../shared/ui/MoneyInput';
import { settingsKeys, useDeliveryZones } from '../orders/api';

export function DeliveryZonesPage() {
  const zones = useDeliveryZones();
  const [editing, setEditing] = useState<DeliveryZone | 'new' | null>(null);

  return (
    <Stack maw={760} gap="lg">
      <Group justify="space-between" align="flex-end">
        <Stack gap={4}>
          <Title order={2}>Taxas de entrega</Title>
          <Text c="dimmed">Taxa por bairro. No pedido, a taxa entra sozinha e ainda dá para digitar outra.</Text>
        </Stack>
        <Button leftSection={<Plus size={18} />} onClick={() => setEditing('new')}>
          Novo bairro
        </Button>
      </Group>
      <Card withBorder radius="lg" padding={0}>
        {zones.isPending && <Loader m="md" aria-label="Carregando taxas" />}
        {zones.isError && (
          <Alert m="md" color="red" icon={<CircleAlert size={18} />}>
            {errorMessage(zones.error)}
          </Alert>
        )}
        {zones.data && zones.data.length === 0 && (
          <Text c="dimmed" p="md">
            Nenhum bairro cadastrado ainda.
          </Text>
        )}
        {zones.data && zones.data.length > 0 && (
          <Table verticalSpacing="sm" highlightOnHover>
            <Table.Thead>
              <Table.Tr>
                <Table.Th>Bairro</Table.Th>
                <Table.Th>Taxa</Table.Th>
                <Table.Th>Situação</Table.Th>
                <Table.Th />
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {zones.data.map((zone) => (
                <Table.Tr key={zone.id}>
                  <Table.Td fw={500}>{zone.neighborhood}</Table.Td>
                  <Table.Td>{formatCents(zone.feeCents)}</Table.Td>
                  <Table.Td>
                    <Badge variant="light" color={zone.active ? 'green' : 'gray'}>
                      {zone.active ? 'Ativa' : 'Inativa'}
                    </Badge>
                  </Table.Td>
                  <Table.Td ta="right">
                    <Button
                      variant="subtle"
                      size="xs"
                      leftSection={<Pencil size={14} />}
                      onClick={() => setEditing(zone)}
                      aria-label={`Editar ${zone.neighborhood}`}
                    >
                      Editar
                    </Button>
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        )}
      </Card>
      <Modal
        opened={editing !== null}
        onClose={() => setEditing(null)}
        title={editing === 'new' ? 'Novo bairro' : editing ? `Editar ${editing.neighborhood}` : ''}
      >
        {editing && (
          <ZoneForm
            key={editing === 'new' ? 'new' : editing.id}
            zone={editing === 'new' ? null : editing}
            onClose={() => setEditing(null)}
          />
        )}
      </Modal>
    </Stack>
  );
}

const schema = z.object({
  neighborhood: z.string().trim().min(1, 'Informe o bairro.').max(80, 'Use até 80 caracteres.'),
  fee: moneyField('Informe a taxa (0 para entrega grátis).'),
  active: z.boolean(),
});

type ZoneFormInput = z.input<typeof schema>;
type ZoneForm = z.output<typeof schema>;

function ZoneForm({ zone, onClose }: { zone: DeliveryZone | null; onClose: () => void }) {
  const queryClient = useQueryClient();
  const {
    register,
    control,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<ZoneFormInput, unknown, ZoneForm>({
    resolver: zodResolver(schema),
    defaultValues: {
      neighborhood: zone?.neighborhood ?? '',
      fee: zone ? centsToReais(zone.feeCents) : '',
      active: zone?.active ?? true,
    },
  });
  const save = useMutation({
    mutationFn: (body: DeliveryZoneRequest) =>
      zone
        ? unwrap(api.PUT('/api/delivery-zones/{id}', { params: { path: { id: zone.id } }, body }))
        : unwrap(api.POST('/api/delivery-zones', { body })),
    onSuccess: async (saved) => {
      await queryClient.invalidateQueries({ queryKey: settingsKeys.deliveryZones });
      notifications.show({ color: 'green', message: `Taxa de ${saved.neighborhood} salva.` });
      onClose();
    },
    onError: (error) => applyApiError(error, setError, { feeCents: 'fee' }),
  });

  return (
    <form
      onSubmit={handleSubmit((form) =>
        save.mutate({ neighborhood: form.neighborhood, feeCents: form.fee, active: form.active }),
      )}
      noValidate
    >
      <Stack>
        {errors.root && (
          <Alert color="red" icon={<CircleAlert size={18} />}>
            {errors.root.message}
          </Alert>
        )}
        <TextInput label="Bairro" data-autofocus {...register('neighborhood')} error={errors.neighborhood?.message} />
        <Controller
          control={control}
          name="fee"
          render={({ field }) => (
            <MoneyInput
              label="Taxa de entrega"
              value={field.value}
              onChange={field.onChange}
              onBlur={field.onBlur}
              error={errors.fee?.message}
            />
          )}
        />
        {zone && (
          <Controller
            control={control}
            name="active"
            render={({ field }) => (
              <Switch
                label="Taxa ativa"
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
