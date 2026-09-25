import { zodResolver } from '@hookform/resolvers/zod';
import { Alert, Badge, Button, Card, Group, Loader, Modal, Select, Stack, Switch, Table, Text, TextInput, Title } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { CircleAlert, Pencil, Plus } from 'lucide-react';
import { useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { z } from 'zod';
import { api } from '../../shared/api/client';
import { errorMessage, unwrap } from '../../shared/api/errors';
import type { PaymentMethod, PaymentMethodRequest, PaymentMethodType } from '../../shared/api/types';
import { applyApiError } from '../../shared/lib/forms';
import { settingsKeys, usePaymentMethods } from '../orders/api';
import { PAYMENT_TYPE_LABELS } from '../orders/labels';

const TYPES = Object.keys(PAYMENT_TYPE_LABELS) as PaymentMethodType[];

export function PaymentMethodsPage() {
  const methods = usePaymentMethods();
  const [editing, setEditing] = useState<PaymentMethod | 'new' | null>(null);

  return (
    <Stack maw={760} gap="lg">
      <Group justify="space-between" align="flex-end">
        <Stack gap={4}>
          <Title order={2}>Formas de pagamento</Title>
          <Text c="dimmed">O que aparece no pedido e no fechamento do caixa. Desative em vez de apagar.</Text>
        </Stack>
        <Button leftSection={<Plus size={18} />} onClick={() => setEditing('new')}>
          Nova forma
        </Button>
      </Group>
      <Card withBorder radius="lg" padding={0}>
        {methods.isPending && <Loader m="md" aria-label="Carregando formas de pagamento" />}
        {methods.isError && (
          <Alert m="md" color="red" icon={<CircleAlert size={18} />}>
            {errorMessage(methods.error)}
          </Alert>
        )}
        {methods.data && (
          <Table verticalSpacing="sm" highlightOnHover>
            <Table.Thead>
              <Table.Tr>
                <Table.Th>Nome</Table.Th>
                <Table.Th>Tipo</Table.Th>
                <Table.Th>Situação</Table.Th>
                <Table.Th />
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {methods.data.map((method) => (
                <Table.Tr key={method.id}>
                  <Table.Td fw={500}>{method.name}</Table.Td>
                  <Table.Td>{PAYMENT_TYPE_LABELS[method.type]}</Table.Td>
                  <Table.Td>
                    <Badge variant="light" color={method.active ? 'green' : 'gray'}>
                      {method.active ? 'Ativa' : 'Inativa'}
                    </Badge>
                  </Table.Td>
                  <Table.Td ta="right">
                    <Button
                      variant="subtle"
                      size="xs"
                      leftSection={<Pencil size={14} />}
                      onClick={() => setEditing(method)}
                      aria-label={`Editar ${method.name}`}
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
        title={editing === 'new' ? 'Nova forma de pagamento' : editing ? `Editar ${editing.name}` : ''}
      >
        {editing && (
          <MethodForm
            key={editing === 'new' ? 'new' : editing.id}
            method={editing === 'new' ? null : editing}
            onClose={() => setEditing(null)}
          />
        )}
      </Modal>
    </Stack>
  );
}

const schema = z.object({
  name: z.string().trim().min(1, 'Informe o nome.').max(60, 'Use até 60 caracteres.'),
  type: z.enum(TYPES as [PaymentMethodType, ...PaymentMethodType[]], { error: 'Escolha o tipo.' }),
  active: z.boolean(),
});

type MethodFormValues = z.infer<typeof schema>;

function MethodForm({ method, onClose }: { method: PaymentMethod | null; onClose: () => void }) {
  const queryClient = useQueryClient();
  const {
    register,
    control,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<MethodFormValues>({
    resolver: zodResolver(schema),
    defaultValues: { name: method?.name ?? '', type: method?.type ?? 'OTHER', active: method?.active ?? true },
  });
  const save = useMutation({
    mutationFn: (body: PaymentMethodRequest) =>
      method
        ? unwrap(api.PUT('/api/payment-methods/{id}', { params: { path: { id: method.id } }, body }))
        : unwrap(api.POST('/api/payment-methods', { body })),
    onSuccess: async (saved) => {
      await queryClient.invalidateQueries({ queryKey: settingsKeys.paymentMethods });
      notifications.show({ color: 'green', message: `${saved.name} salva.` });
      onClose();
    },
    onError: (error) => applyApiError(error, setError),
  });

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
          name="type"
          render={({ field }) => (
            <Select
              label="Tipo"
              description="Dinheiro é o único que aceita troco."
              data={TYPES.map((value) => ({ value, label: PAYMENT_TYPE_LABELS[value] }))}
              value={field.value}
              onChange={(value) => value && field.onChange(value)}
              allowDeselect={false}
            />
          )}
        />
        {method && (
          <Controller
            control={control}
            name="active"
            render={({ field }) => (
              <Switch
                label="Forma de pagamento ativa"
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
