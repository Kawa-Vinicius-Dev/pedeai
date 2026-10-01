import { Alert, Button, Divider, Group, Select, SegmentedControl, Stack, Text, Textarea, TextInput } from '@mantine/core';
import { CircleAlert } from 'lucide-react';
import { useState } from 'react';
import { cartSubtotal, type CartLine } from '../features/orders/cart';
import { errorMessage } from '../shared/api/errors';
import type { MenuOrderRequest, Storefront } from '../shared/api/types';
import { formatCents, optionalMoneyField } from '../shared/lib/numbers';
import { MoneyInput } from '../shared/ui/MoneyInput';
import { usePlaceOrder } from './api';
import { CUSTOMER_KEY, EMPTY_CUSTOMER, type SavedCustomer, useStored } from './storage';

const changeField = optionalMoneyField('Valor de troco inválido.');

/** Dados de entrega e pagamento. Paga na entrega ou na retirada; o total final é o que a API calcular. */
export function Checkout({
  store,
  lines,
  onPlaced,
}: {
  store: Storefront;
  lines: CartLine[];
  onPlaced: (trackingCode: string) => void;
}) {
  const canDeliver = store.deliveryZones.length > 0;
  const [type, setType] = useState<'DELIVERY' | 'TAKEOUT'>(canDeliver ? 'DELIVERY' : 'TAKEOUT');
  const [customer, setCustomer] = useStored<SavedCustomer>(CUSTOMER_KEY, EMPTY_CUSTOMER);
  const [paymentMethodId, setPaymentMethodId] = useState<string | null>(null);
  const [changeFor, setChangeFor] = useState<number | string>('');
  const [notes, setNotes] = useState('');
  const [problem, setProblem] = useState<string | null>(null);
  const place = usePlaceOrder(store.slug);

  const zone = store.deliveryZones.find((candidate) => candidate.neighborhood === customer.neighborhood);
  const deliveryFee = type === 'DELIVERY' ? (zone?.feeCents ?? 0) : 0;
  const subtotal = cartSubtotal(lines);
  const method = store.paymentMethods.find((candidate) => candidate.id === paymentMethodId);
  const set = (field: keyof SavedCustomer) => (value: string) => setCustomer({ ...customer, [field]: value });

  function submit() {
    const missing = [
      !customer.name.trim() && 'seu nome',
      !customer.phone.trim() && 'seu telefone',
      type === 'DELIVERY' && !customer.street.trim() && 'a rua',
      type === 'DELIVERY' && !customer.number.trim() && 'o número',
      type === 'DELIVERY' && !zone && 'o bairro',
      !method && 'como vai pagar',
    ].filter(Boolean);
    if (missing.length > 0) {
      setProblem(`Falta informar ${missing.join(', ')}.`);
      return;
    }
    const change = changeField.safeParse(changeFor);
    if (method?.type === 'CASH' && !change.success) {
      setProblem('Valor de troco inválido.');
      return;
    }
    setProblem(null);
    const body: MenuOrderRequest = {
      type,
      customerName: customer.name.trim(),
      customerPhone: customer.phone.trim(),
      deliveryAddress:
        type === 'DELIVERY'
          ? {
              street: customer.street.trim(),
              number: customer.number.trim(),
              complement: customer.complement.trim() || undefined,
              neighborhood: customer.neighborhood,
              reference: customer.reference.trim() || undefined,
            }
          : undefined,
      items: lines.map((line) => ({
        productId: line.productId,
        quantity: line.quantity,
        options: line.options,
        notes: line.notes || undefined,
      })),
      notes: notes.trim() || undefined,
      paymentMethodId: method?.id ?? '',
      changeForCents: method?.type === 'CASH' && change.success ? (change.data ?? undefined) : undefined,
    };
    place.mutate(body, { onSuccess: (placed) => onPlaced(placed.trackingCode) });
  }

  return (
    <Stack>
      {canDeliver && (
        <SegmentedControl
          fullWidth
          aria-label="Entrega ou retirada"
          value={type}
          onChange={(value) => setType(value as 'DELIVERY' | 'TAKEOUT')}
          data={[
            { value: 'DELIVERY', label: 'Entrega' },
            { value: 'TAKEOUT', label: 'Retirar na loja' },
          ]}
        />
      )}
      <TextInput label="Seu nome" value={customer.name} onChange={(event) => set('name')(event.currentTarget.value)} maxLength={120} />
      <TextInput
        label="Telefone com DDD"
        type="tel"
        inputMode="tel"
        value={customer.phone}
        onChange={(event) => set('phone')(event.currentTarget.value)}
        maxLength={30}
      />
      {type === 'DELIVERY' && (
        <>
          <Select
            label="Bairro"
            placeholder="Escolha o bairro"
            data={store.deliveryZones.map((candidate) => ({
              value: candidate.neighborhood,
              label: `${candidate.neighborhood} · entrega ${formatCents(candidate.feeCents)}`,
            }))}
            value={zone ? customer.neighborhood : null}
            onChange={(value) => set('neighborhood')(value ?? '')}
            allowDeselect={false}
          />
          <Group grow align="flex-start">
            <TextInput label="Rua" value={customer.street} onChange={(event) => set('street')(event.currentTarget.value)} maxLength={120} />
            <TextInput label="Número" value={customer.number} onChange={(event) => set('number')(event.currentTarget.value)} maxLength={20} />
          </Group>
          <TextInput
            label="Complemento"
            value={customer.complement}
            onChange={(event) => set('complement')(event.currentTarget.value)}
            maxLength={80}
          />
          <TextInput
            label="Ponto de referência"
            value={customer.reference}
            onChange={(event) => set('reference')(event.currentTarget.value)}
            maxLength={160}
          />
        </>
      )}
      <Select
        label="Pagamento na entrega ou na retirada"
        placeholder="Escolha como vai pagar"
        data={store.paymentMethods.map((candidate) => ({ value: candidate.id, label: candidate.name }))}
        value={paymentMethodId}
        onChange={setPaymentMethodId}
        allowDeselect={false}
      />
      {method?.type === 'CASH' && (
        <MoneyInput label="Troco para (deixe vazio se não precisar)" value={changeFor} onChange={setChangeFor} />
      )}
      <Textarea label="Observação do pedido" value={notes} onChange={(event) => setNotes(event.currentTarget.value)} maxLength={500} />
      <Divider />
      <Group justify="space-between">
        <Text>Itens</Text>
        <Text>{formatCents(subtotal)}</Text>
      </Group>
      {type === 'DELIVERY' && (
        <Group justify="space-between">
          <Text>Entrega</Text>
          <Text>{zone ? formatCents(deliveryFee) : 'escolha o bairro'}</Text>
        </Group>
      )}
      <Group justify="space-between">
        <Text fw={700}>Total</Text>
        <Text fw={700}>{formatCents(subtotal + deliveryFee)}</Text>
      </Group>
      {(problem || place.isError) && (
        <Alert color="red" icon={<CircleAlert size={18} />}>
          {problem ?? errorMessage(place.error)}
        </Alert>
      )}
      <Button size="md" onClick={submit} loading={place.isPending} disabled={!store.open}>
        {store.open ? 'Fazer pedido' : 'Loja fechada no momento'}
      </Button>
    </Stack>
  );
}
