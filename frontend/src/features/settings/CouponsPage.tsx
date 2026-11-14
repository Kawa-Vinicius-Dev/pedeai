import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert,
  Badge,
  Button,
  Card,
  Group,
  Loader,
  Modal,
  NumberInput,
  SegmentedControl,
  Stack,
  Switch,
  Table,
  Text,
  TextInput,
  Title,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { CircleAlert, Gift, Pencil, Plus } from 'lucide-react';
import { useState } from 'react';
import { Controller, useForm, useWatch } from 'react-hook-form';
import { z } from 'zod';
import { api } from '../../shared/api/client';
import { errorMessage, unwrap } from '../../shared/api/errors';
import type { Coupon, CouponRequest, Store } from '../../shared/api/types';
import { applyApiError } from '../../shared/lib/forms';
import { centsToReais, decimalField, formatCents, moneyField, optionalMoneyField } from '../../shared/lib/numbers';
import { MoneyInput } from '../../shared/ui/MoneyInput';
import { settingsKeys } from '../orders/api';

const day = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeZone: 'UTC' });

/** "10%" ou "R$ 5,00". */
function discountLabel(coupon: Coupon): string {
  return coupon.type === 'PERCENT' ? `${(coupon.amount / 100).toLocaleString('pt-BR')}%` : formatCents(coupon.amount);
}

export function CouponsPage() {
  const coupons = useQuery({ queryKey: settingsKeys.coupons, queryFn: () => unwrap(api.GET('/api/coupons')) });
  const [editing, setEditing] = useState<Coupon | 'new' | null>(null);

  return (
    <Stack maw={860} gap="lg">
      <Group justify="space-between" align="flex-end">
        <Stack gap={4}>
          <Title order={2}>Cupons de desconto</Title>
          <Text c="dimmed">
            O cliente digita o código no cardápio digital. O desconto é sobre os itens, não sobre a entrega.
          </Text>
        </Stack>
        <Button leftSection={<Plus size={18} />} onClick={() => setEditing('new')}>
          Novo cupom
        </Button>
      </Group>
      <Card withBorder radius="lg" padding={0}>
        {coupons.isPending && <Loader m="md" aria-label="Carregando cupons" />}
        {coupons.isError && (
          <Alert m="md" color="red" icon={<CircleAlert size={18} />}>
            {errorMessage(coupons.error)}
          </Alert>
        )}
        {coupons.data && coupons.data.length === 0 && (
          <Text c="dimmed" p="md">
            Nenhum cupom cadastrado ainda.
          </Text>
        )}
        {coupons.data && coupons.data.length > 0 && (
          <Table.ScrollContainer minWidth={620}>
            <Table verticalSpacing="sm" highlightOnHover>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>Código</Table.Th>
                  <Table.Th>Desconto</Table.Th>
                  <Table.Th>Pedido mínimo</Table.Th>
                  <Table.Th>Usos</Table.Th>
                  <Table.Th>Validade</Table.Th>
                  <Table.Th>Situação</Table.Th>
                  <Table.Th />
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {coupons.data.map((coupon) => (
                  <Table.Tr key={coupon.id}>
                    <Table.Td fw={600}>{coupon.code}</Table.Td>
                    <Table.Td>{discountLabel(coupon)}</Table.Td>
                    <Table.Td>{coupon.minOrderCents > 0 ? formatCents(coupon.minOrderCents) : '—'}</Table.Td>
                    <Table.Td>
                      {coupon.usedCount}
                      {coupon.maxUses !== null && ` de ${coupon.maxUses}`}
                    </Table.Td>
                    <Table.Td>
                      {coupon.validUntil ? day.format(new Date(`${coupon.validUntil}T00:00:00Z`)) : 'Sem validade'}
                    </Table.Td>
                    <Table.Td>
                      <Badge variant="light" color={coupon.active ? 'green' : 'gray'}>
                        {coupon.active ? 'Ativo' : 'Inativo'}
                      </Badge>
                    </Table.Td>
                    <Table.Td ta="right">
                      <Button
                        variant="subtle"
                        size="xs"
                        leftSection={<Pencil size={14} />}
                        onClick={() => setEditing(coupon)}
                        aria-label={`Editar ${coupon.code}`}
                      >
                        Editar
                      </Button>
                    </Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
          </Table.ScrollContainer>
        )}
      </Card>
      <Modal
        opened={editing !== null}
        onClose={() => setEditing(null)}
        title={editing === 'new' ? 'Novo cupom' : editing ? `Editar ${editing.code}` : ''}
      >
        {editing && (
          <CouponForm
            key={editing === 'new' ? 'new' : editing.id}
            coupon={editing === 'new' ? null : editing}
            onClose={() => setEditing(null)}
          />
        )}
      </Modal>
      <LoyaltyCard />
    </Stack>
  );
}

const schema = z.object({
  code: z
    .string()
    .trim()
    .regex(/^[A-Za-z0-9 ]{3,30}$/, 'Use de 3 a 30 letras e números, sem acento.'),
  type: z.enum(['PERCENT', 'FIXED']),
  percent: decimalField('Informe a porcentagem.').pipe(
    z.number().min(1, 'A porcentagem deve ser pelo menos 1%.').max(100, 'A porcentagem pode ser no máximo 100%.'),
  ),
  fixed: moneyField('Informe o valor do desconto.'),
  minOrder: moneyField('Informe o pedido mínimo (0 se não houver).'),
  maxUses: z.union([z.number(), z.string()]).transform((value, context) => {
    if (value === '') {
      return null;
    }
    const uses = Number(value);
    if (!Number.isInteger(uses) || uses < 1) {
      context.addIssue({ code: 'custom', message: 'Informe um número inteiro, ou deixe vazio.' });
      return z.NEVER;
    }
    return uses;
  }),
  validUntil: z.string(),
  active: z.boolean(),
});

type CouponFormInput = z.input<typeof schema>;
type CouponFormValues = z.output<typeof schema>;

function CouponForm({ coupon, onClose }: { coupon: Coupon | null; onClose: () => void }) {
  const queryClient = useQueryClient();
  const {
    register,
    control,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<CouponFormInput, unknown, CouponFormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      code: coupon?.code ?? '',
      type: coupon?.type ?? 'PERCENT',
      percent: coupon?.type === 'PERCENT' ? coupon.amount / 100 : 10,
      fixed: coupon?.type === 'FIXED' ? centsToReais(coupon.amount) : 5,
      minOrder: coupon ? centsToReais(coupon.minOrderCents) : 0,
      maxUses: coupon?.maxUses ?? '',
      validUntil: coupon?.validUntil ?? '',
      active: coupon?.active ?? true,
    },
  });
  const type = useWatch({ control, name: 'type' });
  const save = useMutation({
    mutationFn: (body: CouponRequest) =>
      coupon
        ? unwrap(api.PUT('/api/coupons/{id}', { params: { path: { id: coupon.id } }, body }))
        : unwrap(api.POST('/api/coupons', { body })),
    onSuccess: async (saved) => {
      await queryClient.invalidateQueries({ queryKey: settingsKeys.coupons });
      notifications.show({ color: 'green', message: `Cupom ${saved.code} salvo.` });
      onClose();
    },
    onError: (error) => applyApiError(error, setError, { minOrderCents: 'minOrder' }),
  });

  function submit(form: CouponFormValues) {
    save.mutate({
      code: form.code,
      type: form.type,
      // Porcentagem vai em pontos-base (10% = 1000); valor fixo, em centavos.
      amount: form.type === 'PERCENT' ? Math.round(form.percent * 100) : form.fixed,
      minOrderCents: form.minOrder,
      maxUses: form.maxUses,
      validUntil: form.validUntil || null,
      active: form.active,
    });
  }

  return (
    <form onSubmit={handleSubmit(submit)} noValidate>
      <Stack>
        {errors.root && (
          <Alert color="red" icon={<CircleAlert size={18} />}>
            {errors.root.message}
          </Alert>
        )}
        <TextInput
          label="Código"
          description="O que o cliente digita. Maiúsculas e minúsculas não fazem diferença."
          placeholder="BEMVINDO10"
          data-autofocus
          {...register('code')}
          error={errors.code?.message}
        />
        <Controller
          control={control}
          name="type"
          render={({ field }) => (
            <SegmentedControl
              fullWidth
              aria-label="Tipo do desconto"
              value={field.value}
              onChange={field.onChange}
              data={[
                { value: 'PERCENT', label: 'Porcentagem' },
                { value: 'FIXED', label: 'Valor fixo' },
              ]}
            />
          )}
        />
        {type === 'PERCENT' ? (
          <Controller
            control={control}
            name="percent"
            render={({ field }) => (
              <NumberInput
                label="Desconto"
                suffix="%"
                decimalScale={2}
                decimalSeparator=","
                min={1}
                max={100}
                hideControls
                value={field.value}
                onChange={field.onChange}
                onBlur={field.onBlur}
                error={errors.percent?.message}
              />
            )}
          />
        ) : (
          <Controller
            control={control}
            name="fixed"
            render={({ field }) => (
              <MoneyInput
                label="Desconto"
                value={field.value}
                onChange={field.onChange}
                onBlur={field.onBlur}
                error={errors.fixed?.message}
              />
            )}
          />
        )}
        <Controller
          control={control}
          name="minOrder"
          render={({ field }) => (
            <MoneyInput
              label="Pedido mínimo"
              description="Valor dos itens a partir do qual o cupom vale. 0 para qualquer pedido."
              value={field.value}
              onChange={field.onChange}
              onBlur={field.onBlur}
              error={errors.minOrder?.message}
            />
          )}
        />
        <Group grow align="flex-start">
          <Controller
            control={control}
            name="maxUses"
            render={({ field }) => (
              <NumberInput
                label="Limite de usos"
                placeholder="Sem limite"
                min={1}
                allowDecimal={false}
                hideControls
                value={field.value}
                onChange={field.onChange}
                onBlur={field.onBlur}
                error={errors.maxUses?.message}
              />
            )}
          />
          <TextInput label="Válido até" type="date" {...register('validUntil')} error={errors.validUntil?.message} />
        </Group>
        {coupon && (
          <Controller
            control={control}
            name="active"
            render={({ field }) => (
              <Switch
                label="Cupom ativo"
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

const loyaltySchema = z
  .object({
    enabled: z.boolean(),
    orders: decimalField('Informe quantos pedidos.').pipe(
      z.number().int('Use um número inteiro.').min(2, 'Use pelo menos 2 pedidos.').max(100, 'Use até 100 pedidos.'),
    ),
    discount: optionalMoneyField('Informe um valor válido.'),
  })
  .refine((value) => !value.enabled || (value.discount !== null && value.discount > 0), {
    path: ['discount'],
    message: 'Informe o valor do desconto.',
  });
type LoyaltyInput = z.input<typeof loyaltySchema>;
type LoyaltyForm = z.output<typeof loyaltySchema>;

/**
 * Fidelidade simples: a cada N pedidos concluídos, o cliente ganha um desconto fixo no próximo. O cliente é
 * reconhecido pelo telefone; no PDV o caixa marca "usar o prêmio", no cardápio digital o desconto entra sozinho.
 */
function LoyaltyCard() {
  const store = useQuery({ queryKey: ['store'], queryFn: () => unwrap(api.GET('/api/store')) });
  return (
    <Card withBorder radius="lg" padding="lg">
      <Stack>
        <Group gap="xs">
          <Gift size={20} />
          <Title order={3}>Programa de fidelidade</Title>
        </Group>
        <Text c="dimmed" size="sm">
          A cada tantos pedidos concluídos, o cliente ganha um desconto no próximo. Ele é reconhecido pelo telefone.
          No PDV, o caixa vê o prêmio e marca para usar; no cardápio digital, o desconto entra sozinho. Não soma com
          cupom.
        </Text>
        {store.isPending && <Loader aria-label="Carregando fidelidade" />}
        {store.isError && (
          <Alert color="red" icon={<CircleAlert size={18} />}>
            {errorMessage(store.error)}
          </Alert>
        )}
        {store.data && <LoyaltyFields key={store.data.id} store={store.data} />}
      </Stack>
    </Card>
  );
}

function loyaltyDefaults(store: Store): LoyaltyInput {
  return {
    enabled: store.loyaltyOrders !== null,
    orders: store.loyaltyOrders ?? 10,
    discount: store.loyaltyOrders !== null ? centsToReais(store.loyaltyDiscountCents) : '',
  };
}

function LoyaltyFields({ store }: { store: Store }) {
  const queryClient = useQueryClient();
  const {
    control,
    handleSubmit,
    setError,
    reset,
    formState: { errors, isDirty },
  } = useForm<LoyaltyInput, unknown, LoyaltyForm>({
    resolver: zodResolver(loyaltySchema),
    defaultValues: loyaltyDefaults(store),
  });
  const enabled = useWatch({ control, name: 'enabled' });
  const save = useMutation({
    mutationFn: (form: LoyaltyForm) =>
      unwrap(
        api.PUT('/api/store/loyalty', {
          body: form.enabled
            ? { orders: form.orders, discountCents: form.discount ?? 0 }
            : { orders: null, discountCents: 0 },
        }),
      ),
    onSuccess: (updated) => {
      queryClient.setQueryData(['store'], updated);
      reset(loyaltyDefaults(updated));
      notifications.show({
        color: 'green',
        message: updated.loyaltyOrders === null ? 'Programa de fidelidade desligado.' : 'Programa de fidelidade salvo.',
      });
    },
    onError: (error) => applyApiError(error, setError, { orders: 'orders', discountCents: 'discount' }),
  });

  return (
    <form onSubmit={handleSubmit((form) => save.mutate(form))}>
      <Stack>
        <Controller
          control={control}
          name="enabled"
          render={({ field }) => (
            <Switch
              label="Ligar o programa de fidelidade"
              checked={field.value}
              onChange={(event) => field.onChange(event.currentTarget.checked)}
            />
          )}
        />
        {enabled && (
          <Group align="flex-start" grow>
            <Controller
              control={control}
              name="orders"
              render={({ field }) => (
                <NumberInput
                  label="A cada quantos pedidos"
                  min={2}
                  max={100}
                  allowDecimal={false}
                  value={field.value}
                  onChange={field.onChange}
                  onBlur={field.onBlur}
                  error={errors.orders?.message}
                />
              )}
            />
            <Controller
              control={control}
              name="discount"
              render={({ field }) => (
                <MoneyInput
                  label="Desconto do prêmio"
                  value={field.value}
                  onChange={field.onChange}
                  onBlur={field.onBlur}
                  error={errors.discount?.message}
                />
              )}
            />
          </Group>
        )}
        {errors.root && (
          <Alert color="red" icon={<CircleAlert size={18} />}>
            {errors.root.message}
          </Alert>
        )}
        <Group justify="flex-end">
          <Button type="submit" loading={save.isPending} disabled={!isDirty}>
            Salvar fidelidade
          </Button>
        </Group>
      </Stack>
    </form>
  );
}
