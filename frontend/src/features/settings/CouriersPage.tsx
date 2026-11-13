import { zodResolver } from '@hookform/resolvers/zod';
import { Alert, Badge, Button, Card, Group, Loader, Modal, Stack, Switch, Table, Text, TextInput, Title } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { CircleAlert, Pencil, Plus } from 'lucide-react';
import { useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { z } from 'zod';
import { api } from '../../shared/api/client';
import { errorMessage, unwrap } from '../../shared/api/errors';
import type { Courier, CourierRequest } from '../../shared/api/types';
import { applyApiError } from '../../shared/lib/forms';
import { formatCents } from '../../shared/lib/numbers';
import { settingsKeys, useCouriers } from '../orders/api';

/** Hoje, no relógio do aparelho, como AAAA-MM-DD. */
function today(): string {
  const now = new Date();
  return new Date(now.getTime() - now.getTimezoneOffset() * 60_000).toISOString().slice(0, 10);
}

export function CouriersPage() {
  const couriers = useCouriers();
  const [editing, setEditing] = useState<Courier | 'new' | null>(null);

  return (
    <Stack maw={760} gap="lg">
      <Group justify="space-between" align="flex-end">
        <Stack gap={4}>
          <Title order={2}>Entregadores</Title>
          <Text c="dimmed">Quem leva os pedidos. O entregador é escolhido no detalhe de cada pedido de delivery.</Text>
        </Stack>
        <Button leftSection={<Plus size={18} />} onClick={() => setEditing('new')}>
          Novo entregador
        </Button>
      </Group>
      <Card withBorder radius="lg" padding={0}>
        {couriers.isPending && <Loader m="md" aria-label="Carregando entregadores" />}
        {couriers.isError && (
          <Alert m="md" color="red" icon={<CircleAlert size={18} />}>
            {errorMessage(couriers.error)}
          </Alert>
        )}
        {couriers.data && couriers.data.length === 0 && (
          <Text c="dimmed" p="md">
            Nenhum entregador cadastrado ainda.
          </Text>
        )}
        {couriers.data && couriers.data.length > 0 && (
          <Table verticalSpacing="sm" highlightOnHover>
            <Table.Thead>
              <Table.Tr>
                <Table.Th>Nome</Table.Th>
                <Table.Th>Telefone</Table.Th>
                <Table.Th>Situação</Table.Th>
                <Table.Th />
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {couriers.data.map((courier) => (
                <Table.Tr key={courier.id}>
                  <Table.Td fw={500}>{courier.name}</Table.Td>
                  <Table.Td>{courier.phone ?? '—'}</Table.Td>
                  <Table.Td>
                    <Badge variant="light" color={courier.active ? 'green' : 'gray'}>
                      {courier.active ? 'Ativo' : 'Inativo'}
                    </Badge>
                  </Table.Td>
                  <Table.Td ta="right">
                    <Button
                      variant="subtle"
                      size="xs"
                      leftSection={<Pencil size={14} />}
                      onClick={() => setEditing(courier)}
                      aria-label={`Editar ${courier.name}`}
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

      <Settlement />

      <Modal
        opened={editing !== null}
        onClose={() => setEditing(null)}
        title={editing === 'new' ? 'Novo entregador' : editing ? `Editar ${editing.name}` : ''}
      >
        {editing && (
          <CourierForm
            key={editing === 'new' ? 'new' : editing.id}
            courier={editing === 'new' ? null : editing}
            onClose={() => setEditing(null)}
          />
        )}
      </Modal>
    </Stack>
  );
}

/** Acerto do dia: quantas entregas cada um fez e quanto somam as taxas de entrega delas. */
function Settlement() {
  const [businessDate, setBusinessDate] = useState(today);
  const settlement = useQuery({
    queryKey: [...settingsKeys.couriers, 'settlement', businessDate],
    queryFn: () => unwrap(api.GET('/api/couriers/settlement', { params: { query: { businessDate } } })),
    enabled: businessDate !== '',
  });

  return (
    <Stack gap="sm">
      <Group justify="space-between" align="flex-end">
        <Stack gap={4}>
          <Title order={3}>Acerto do dia</Title>
          <Text c="dimmed" size="sm">
            Entregas não canceladas e a soma das taxas de entrega delas.
          </Text>
        </Stack>
        <TextInput
          aria-label="Dia do acerto"
          type="date"
          value={businessDate}
          onChange={(event) => setBusinessDate(event.currentTarget.value)}
        />
      </Group>
      <Card withBorder radius="lg" padding={0} aria-label="Acerto do dia">
        {settlement.isPending && <Loader m="md" aria-label="Carregando o acerto" />}
        {settlement.isError && (
          <Alert m="md" color="red" icon={<CircleAlert size={18} />}>
            {errorMessage(settlement.error)}
          </Alert>
        )}
        {settlement.data && settlement.data.length === 0 && (
          <Text c="dimmed" p="md">
            Nenhuma entrega neste dia.
          </Text>
        )}
        {settlement.data && settlement.data.length > 0 && (
          <Table verticalSpacing="sm">
            <Table.Thead>
              <Table.Tr>
                <Table.Th>Entregador</Table.Th>
                <Table.Th ta="right">Entregas</Table.Th>
                <Table.Th ta="right">Taxas de entrega</Table.Th>
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {settlement.data.map((line) => (
                <Table.Tr key={line.courierId ?? 'sem-entregador'}>
                  <Table.Td fw={500} c={line.courierId ? undefined : 'dimmed'}>
                    {line.courierName ?? 'Sem entregador'}
                  </Table.Td>
                  <Table.Td ta="right">{line.deliveries}</Table.Td>
                  <Table.Td ta="right">{formatCents(line.deliveryFeeCents)}</Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        )}
      </Card>
    </Stack>
  );
}

const schema = z.object({
  name: z.string().trim().min(1, 'Informe o nome.').max(120, 'Use até 120 caracteres.'),
  phone: z.string().trim().max(30, 'Telefone inválido.'),
  active: z.boolean(),
});

type CourierFormValues = z.infer<typeof schema>;

function CourierForm({ courier, onClose }: { courier: Courier | null; onClose: () => void }) {
  const queryClient = useQueryClient();
  const {
    register,
    control,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<CourierFormValues>({
    resolver: zodResolver(schema),
    defaultValues: { name: courier?.name ?? '', phone: courier?.phone ?? '', active: courier?.active ?? true },
  });
  const save = useMutation({
    mutationFn: (body: CourierRequest) =>
      courier
        ? unwrap(api.PUT('/api/couriers/{id}', { params: { path: { id: courier.id } }, body }))
        : unwrap(api.POST('/api/couriers', { body })),
    onSuccess: async (saved) => {
      await queryClient.invalidateQueries({ queryKey: settingsKeys.couriers });
      notifications.show({ color: 'green', message: `${saved.name} salvo.` });
      onClose();
    },
    onError: (error) => applyApiError(error, setError),
  });

  return (
    <form
      onSubmit={handleSubmit((form) => save.mutate({ name: form.name, phone: form.phone || null, active: form.active }))}
      noValidate
    >
      <Stack>
        {errors.root && (
          <Alert color="red" icon={<CircleAlert size={18} />}>
            {errors.root.message}
          </Alert>
        )}
        <TextInput label="Nome" data-autofocus {...register('name')} error={errors.name?.message} />
        <TextInput label="Telefone" type="tel" {...register('phone')} error={errors.phone?.message} />
        {courier && (
          <Controller
            control={control}
            name="active"
            render={({ field }) => (
              <Switch
                label="Entregador ativo"
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
