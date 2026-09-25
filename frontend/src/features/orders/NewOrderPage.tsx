import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert,
  Button,
  Card,
  Divider,
  Grid,
  Group,
  Loader,
  Radio,
  SegmentedControl,
  Select,
  SimpleGrid,
  Stack,
  Switch,
  Text,
  Textarea,
  TextInput,
  Title,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { CircleAlert, Search } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { Controller, useForm, useWatch } from 'react-hook-form';
import { Link } from 'react-router';
import { z } from 'zod';
import { api } from '../../shared/api/client';
import { errorMessage, unwrap } from '../../shared/api/errors';
import type { CreateOrderRequest, Customer, Product } from '../../shared/api/types';
import { applyApiError } from '../../shared/lib/forms';
import { formatCents, moneyField, optionalMoneyField, parseDecimal } from '../../shared/lib/numbers';
import { CATALOG_MANAGERS } from '../../shared/lib/roles';
import { normalizeSearch } from '../../shared/lib/text';
import { MoneyInput } from '../../shared/ui/MoneyInput';
import { useSession } from '../auth/auth-context';
import { useCatalog } from '../catalog/api';
import { orderKeys, useDeliveryZones, usePaymentMethods } from './api';
import { addLine, type CartLine, cartSubtotal, changeQuantity, nextLineKey } from './cart';
import { CartPanel } from './CartPanel';
import { ItemBuilderModal } from './ItemBuilderModal';
import { formatPhone } from './labels';
import { ProductPicker } from './ProductPicker';

const NEW_ADDRESS = 'novo';

const schema = z
  .object({
    type: z.enum(['TAKEOUT', 'DELIVERY']),
    phone: z.string().trim().max(30, 'Telefone inválido.'),
    name: z.string().trim().max(120, 'Use até 120 caracteres.'),
    addressId: z.string(),
    street: z.string().trim().max(120, 'Use até 120 caracteres.'),
    number: z.string().trim().max(20, 'Use até 20 caracteres.'),
    complement: z.string().trim().max(80, 'Use até 80 caracteres.'),
    neighborhood: z.string().trim().max(80, 'Use até 80 caracteres.'),
    reference: z.string().trim().max(160, 'Use até 160 caracteres.'),
    deliveryFee: moneyField('Informe a taxa de entrega (0 se não cobrar).'),
    discount: moneyField('Informe o desconto (0 se não houver).'),
    paymentMethodId: z.string().nullable(),
    paid: z.boolean(),
    changeFor: optionalMoneyField('Valor inválido.'),
    notes: z.string().trim().max(500, 'Use até 500 caracteres.'),
  })
  .superRefine((form, context) => {
    const phoneDigits = form.phone.replace(/\D/g, '');
    if (phoneDigits.length > 0 && phoneDigits.length < 10) {
      context.addIssue({ code: 'custom', path: ['phone'], message: 'Informe o telefone com DDD.' });
    }
    if (phoneDigits.length > 0 && !form.name) {
      context.addIssue({ code: 'custom', path: ['name'], message: 'Informe o nome do cliente.' });
    }
    if (form.type !== 'DELIVERY') {
      return;
    }
    if (!form.name) {
      context.addIssue({ code: 'custom', path: ['name'], message: 'Informe o nome do cliente.' });
    }
    if (phoneDigits.length === 0) {
      context.addIssue({ code: 'custom', path: ['phone'], message: 'Informe o telefone para a entrega.' });
    }
    if (form.addressId === NEW_ADDRESS) {
      if (!form.street) {
        context.addIssue({ code: 'custom', path: ['street'], message: 'Informe a rua.' });
      }
      if (!form.number) {
        context.addIssue({ code: 'custom', path: ['number'], message: 'Informe o número (ou s/n).' });
      }
      if (!form.neighborhood) {
        context.addIssue({ code: 'custom', path: ['neighborhood'], message: 'Informe o bairro.' });
      }
    }
  });

type CheckoutInput = z.input<typeof schema>;
type Checkout = z.output<typeof schema>;

const EMPTY: CheckoutInput = {
  type: 'TAKEOUT',
  phone: '',
  name: '',
  addressId: NEW_ADDRESS,
  street: '',
  number: '',
  complement: '',
  neighborhood: '',
  reference: '',
  deliveryFee: 0,
  discount: 0,
  paymentMethodId: null,
  paid: false,
  changeFor: '',
  notes: '',
};

type Lookup =
  | { status: 'idle' }
  | { status: 'searching'; digits: string }
  | { status: 'found'; digits: string; customer: Customer }
  | { status: 'new'; digits: string };

const digitsOf = (phone: string) => phone.replace(/\D/g, '');

/** Valor do campo de dinheiro enquanto a pessoa digita, para mostrar o total antes de validar. */
function reaisToCents(value: number | string): number {
  const reais = typeof value === 'number' ? value : parseDecimal(value);
  return Number.isFinite(reais) ? Math.round(reais * 100) : 0;
}

/** PDV: pedido de balcão, telefone e delivery. O preço de cada item vem da API; a tela só soma para mostrar. */
export function NewOrderPage() {
  const { user } = useSession();
  const queryClient = useQueryClient();
  const { data: catalog, error: catalogError } = useCatalog();
  const paymentMethods = usePaymentMethods();
  const deliveryZones = useDeliveryZones();
  const [lines, setLines] = useState<CartLine[]>([]);
  const [building, setBuilding] = useState<Product | null>(null);
  const [lookup, setLookup] = useState<Lookup>({ status: 'idle' });

  const {
    register,
    control,
    handleSubmit,
    reset,
    setError,
    setValue,
    getValues,
    formState: { errors },
  } = useForm<CheckoutInput, unknown, Checkout>({ resolver: zodResolver(schema), defaultValues: EMPTY });
  const [type, addressId, neighborhood, deliveryFee, discount, paymentMethodId, changeForValue] = useWatch({
    control,
    name: ['type', 'addressId', 'neighborhood', 'deliveryFee', 'discount', 'paymentMethodId', 'changeFor'],
  });
  // Taxa que a tela preencheu sozinha, para saber se pode trocá-la quando o bairro muda.
  const autoFee = useRef<number | null>(null);

  const savedAddresses = lookup.status === 'found' ? lookup.customer.addresses : [];
  const selectedAddress = savedAddresses.find((address) => address.id === addressId);
  const deliveryNeighborhood = selectedAddress?.neighborhood ?? neighborhood;
  const zone = (deliveryZones.data ?? []).find(
    (current) =>
      current.active && deliveryNeighborhood && normalizeSearch(current.neighborhood) === normalizeSearch(deliveryNeighborhood),
  );
  const method = (paymentMethods.data ?? []).find((current) => current.id === paymentMethodId);

  // Bairro com taxa cadastrada: a taxa entra sozinha (e ainda dá para digitar outra). Se o bairro muda para um
  // sem taxa, a taxa do bairro anterior sai do campo, para ninguém cobrar a taxa errada sem perceber.
  useEffect(() => {
    if (type !== 'DELIVERY') {
      return;
    }
    if (zone) {
      setValue('deliveryFee', zone.feeCents / 100);
      autoFee.current = zone.feeCents;
    } else if (autoFee.current !== null) {
      if (reaisToCents(getValues('deliveryFee')) === autoFee.current) {
        setValue('deliveryFee', '');
      }
      autoFee.current = null;
    }
  }, [type, zone, setValue, getValues]);

  const subtotal = cartSubtotal(lines);
  const feeCents = type === 'DELIVERY' ? reaisToCents(deliveryFee) : 0;
  const discountCents = Math.min(reaisToCents(discount), subtotal);
  const total = subtotal - discountCents + feeCents;

  const create = useMutation({
    mutationFn: (body: CreateOrderRequest) => unwrap(api.POST('/api/orders', { body })),
    onSuccess: async (order) => {
      notifications.show({ color: 'green', title: `Pedido ${order.number} lançado`, message: 'Já está no quadro.' });
      setLines([]);
      setLookup({ status: 'idle' });
      autoFee.current = null;
      reset({ ...EMPTY, type: getValues('type') });
      await queryClient.invalidateQueries({ queryKey: orderKeys.all });
    },
    onError: (error) => applyApiError(error, setError, { deliveryFeeCents: 'deliveryFee', discountCents: 'discount' }),
  });

  async function searchCustomer() {
    const phone = getValues('phone');
    const digits = digitsOf(phone);
    // Sair do campo e clicar em "Buscar" chamam a busca duas vezes: a segunda não precisa ir à API.
    if (digits.length < 10 || (lookup.status !== 'idle' && lookup.digits === digits)) {
      return;
    }
    setLookup({ status: 'searching', digits });
    try {
      const page = await unwrap(api.GET('/api/customers', { params: { query: { phone } } }));
      const customer = page.content[0];
      if (customer) {
        setLookup({ status: 'found', digits, customer });
        setValue('name', customer.name);
        setValue('addressId', customer.addresses[0]?.id ?? NEW_ADDRESS);
      } else {
        setLookup({ status: 'new', digits });
        setValue('addressId', NEW_ADDRESS);
      }
    } catch (error) {
      setLookup({ status: 'idle' });
      notifications.show({ color: 'red', message: errorMessage(error) });
    }
  }

  /** Telefone corrigido depois da busca: os endereços eram de outro cliente. */
  function phoneChanged(phone: string) {
    if (lookup.status !== 'idle' && lookup.digits !== digitsOf(phone)) {
      setLookup({ status: 'idle' });
      setValue('addressId', NEW_ADDRESS);
    }
  }

  function pick(product: Product) {
    if (product.optionGroupIds.length > 0) {
      setBuilding(product);
      return;
    }
    setLines((current) =>
      addLine(current, {
        key: nextLineKey(),
        productId: product.id,
        name: product.name,
        quantity: 1,
        unitPriceCents: product.priceCents,
        options: [],
        optionsLabel: '',
        notes: '',
      }),
    );
  }

  function submit(form: Checkout) {
    if (lines.length === 0) {
      setError('root', { message: 'Adicione pelo menos um item.' });
      return;
    }
    const cash = method?.type === 'CASH';
    if (form.paymentMethodId && cash && form.changeFor !== null && form.changeFor < total) {
      setError('changeFor', { message: `O troco precisa ser para ${formatCents(total)} ou mais.` });
      return;
    }
    const delivery = form.type === 'DELIVERY';
    const address = delivery
      ? form.addressId === NEW_ADDRESS
        ? {
            street: form.street,
            number: form.number,
            complement: form.complement || undefined,
            // Bairro com taxa cadastrada vai com o nome da lista ("centro" digitado vira "Centro").
            neighborhood: zone?.neighborhood ?? form.neighborhood,
            reference: form.reference || undefined,
          }
        : savedAddresses.find((saved) => saved.id === form.addressId)
      : undefined;
    const paymentAmount = total;
    create.mutate({
      type: form.type,
      customer: form.name ? { name: form.name, phone: form.phone || undefined } : undefined,
      deliveryAddress: address
        ? {
            label: undefined,
            street: address.street,
            number: address.number,
            complement: address.complement ?? undefined,
            neighborhood: address.neighborhood,
            reference: address.reference ?? undefined,
          }
        : undefined,
      items: lines.map((line) => ({
        productId: line.productId,
        quantity: line.quantity,
        options: line.options,
        notes: line.notes || undefined,
      })),
      notes: form.notes || undefined,
      discountCents: form.discount,
      deliveryFeeCents: delivery ? form.deliveryFee : 0,
      payments:
        form.paymentMethodId && paymentAmount > 0
          ? [
              {
                paymentMethodId: form.paymentMethodId,
                amountCents: paymentAmount,
                changeForCents: cash ? (form.changeFor ?? undefined) : undefined,
                paid: form.paid,
              },
            ]
          : [],
    });
  }

  if (!catalog) {
    return catalogError ? (
      <Alert color="red" icon={<CircleAlert size={18} />}>
        {errorMessage(catalogError)}
      </Alert>
    ) : (
      <Loader aria-label="Carregando cardápio" />
    );
  }

  const changeFor = reaisToCents(changeForValue ?? '');
  const manager = CATALOG_MANAGERS.includes(user.role);

  return (
    <Stack gap="md">
      <Group justify="space-between">
        <Title order={2}>Novo pedido</Title>
        <Button component={Link} to="/pedidos" variant="default">
          Ver quadro
        </Button>
      </Group>
      <Grid gap="lg">
        <Grid.Col span={{ base: 12, md: 7 }}>
          {catalog.products.length === 0 ? (
            <Alert color="orange" icon={<CircleAlert size={18} />}>
              O cardápio está vazio. <Link to="/cardapio">Cadastre os produtos</Link> para lançar pedidos.
            </Alert>
          ) : (
            <ProductPicker catalog={catalog} onPick={pick} />
          )}
        </Grid.Col>
        <Grid.Col span={{ base: 12, md: 5 }}>
          <form onSubmit={handleSubmit(submit)} noValidate>
            <Card withBorder radius="lg" padding="md">
              <Stack gap="md">
                <Controller
                  control={control}
                  name="type"
                  render={({ field }) => (
                    <SegmentedControl
                      fullWidth
                      value={field.value}
                      onChange={(value) => field.onChange(value)}
                      data={[
                        { value: 'TAKEOUT', label: 'Retirada' },
                        { value: 'DELIVERY', label: 'Delivery' },
                      ]}
                    />
                  )}
                />
                <CartPanel lines={lines} onQuantity={(key, quantity) => setLines((current) => changeQuantity(current, key, quantity))} />
                <Divider />

                <Stack gap="xs">
                  <Group align="flex-end" wrap="nowrap" gap="xs">
                    <TextInput
                      label="Telefone"
                      type="tel"
                      placeholder="(11) 99999-0000"
                      style={{ flex: 1 }}
                      {...register('phone', {
                        onBlur: () => void searchCustomer(),
                        onChange: (event: { target: { value: string } }) => phoneChanged(event.target.value),
                      })}
                      onKeyDown={(event) => {
                        if (event.key === 'Enter') {
                          event.preventDefault();
                          void searchCustomer();
                        }
                      }}
                      error={errors.phone?.message}
                    />
                    <Button
                      variant="default"
                      leftSection={<Search size={16} />}
                      onClick={() => void searchCustomer()}
                      loading={lookup.status === 'searching'}
                    >
                      Buscar
                    </Button>
                  </Group>
                  {lookup.status === 'found' && (
                    <Text size="xs" c="green.8">
                      Cliente cadastrado: {lookup.customer.name}, {formatPhone(lookup.customer.phone)}.
                    </Text>
                  )}
                  {lookup.status === 'new' && (
                    <Text size="xs" c="dimmed">
                      Cliente novo: fica cadastrado ao lançar o pedido.
                    </Text>
                  )}
                  <TextInput
                    label={type === 'DELIVERY' ? 'Nome do cliente' : 'Nome do cliente (para chamar na retirada)'}
                    {...register('name')}
                    error={errors.name?.message}
                  />
                </Stack>

                {type === 'DELIVERY' && (
                  <Stack gap="xs">
                    {savedAddresses.length > 0 && (
                      <Controller
                        control={control}
                        name="addressId"
                        render={({ field }) => (
                          <Radio.Group label="Endereço de entrega" value={field.value} onChange={field.onChange}>
                            <Stack gap={6} mt={4}>
                              {savedAddresses.map((address) => (
                                <Radio
                                  key={address.id}
                                  value={address.id}
                                  label={`${address.street}, ${address.number}${address.complement ? `, ${address.complement}` : ''} · ${address.neighborhood}`}
                                />
                              ))}
                              <Radio value={NEW_ADDRESS} label="Outro endereço" />
                            </Stack>
                          </Radio.Group>
                        )}
                      />
                    )}
                    {addressId === NEW_ADDRESS && (
                      <>
                        <SimpleGrid cols={{ base: 1, xs: 3 }} spacing="xs">
                          <TextInput
                            label="Rua"
                            style={{ gridColumn: 'span 2' }}
                            {...register('street')}
                            error={errors.street?.message}
                          />
                          <TextInput label="Número" {...register('number')} error={errors.number?.message} />
                        </SimpleGrid>
                        <SimpleGrid cols={{ base: 1, xs: 2 }} spacing="xs">
                          <TextInput label="Bairro" {...register('neighborhood')} error={errors.neighborhood?.message} />
                          <TextInput label="Complemento" {...register('complement')} error={errors.complement?.message} />
                        </SimpleGrid>
                        <TextInput label="Referência" {...register('reference')} error={errors.reference?.message} />
                      </>
                    )}
                    <Controller
                      control={control}
                      name="deliveryFee"
                      render={({ field }) => (
                        <MoneyInput
                          label="Taxa de entrega"
                          description={
                            zone
                              ? `Taxa do bairro ${zone.neighborhood}.`
                              : deliveryNeighborhood
                                ? 'Bairro sem taxa cadastrada: digite a taxa.'
                                : undefined
                          }
                          value={field.value}
                          onChange={field.onChange}
                          onBlur={field.onBlur}
                          error={errors.deliveryFee?.message}
                        />
                      )}
                    />
                  </Stack>
                )}

                <Stack gap="xs">
                  <Controller
                    control={control}
                    name="paymentMethodId"
                    render={({ field }) => (
                      <Select
                        label="Pagamento"
                        placeholder="Receber depois"
                        data={(paymentMethods.data ?? [])
                          .filter((current) => current.active)
                          .map((current) => ({ value: current.id, label: current.name }))}
                        value={field.value}
                        onChange={field.onChange}
                        clearable
                      />
                    )}
                  />
                  {method?.type === 'CASH' && (
                    <Controller
                      control={control}
                      name="changeFor"
                      render={({ field }) => (
                        <MoneyInput
                          label="Troco para"
                          placeholder="Sem troco"
                          description={
                            changeFor > total ? `Levar ${formatCents(changeFor - total)} de troco.` : undefined
                          }
                          value={field.value}
                          onChange={field.onChange}
                          onBlur={field.onBlur}
                          error={errors.changeFor?.message}
                        />
                      )}
                    />
                  )}
                  {method && (
                    <Controller
                      control={control}
                      name="paid"
                      render={({ field }) => (
                        <Switch
                          label="Já foi pago"
                          description={field.value ? undefined : 'Fica a receber na entrega ou na retirada.'}
                          checked={field.value}
                          onChange={(event) => field.onChange(event.currentTarget.checked)}
                        />
                      )}
                    />
                  )}
                </Stack>

                {manager && (
                  <Controller
                    control={control}
                    name="discount"
                    render={({ field }) => (
                      <MoneyInput
                        label="Desconto"
                        value={field.value}
                        onChange={field.onChange}
                        onBlur={field.onBlur}
                        error={errors.discount?.message}
                      />
                    )}
                  />
                )}
                <Textarea label="Observação do pedido" autosize minRows={1} {...register('notes')} error={errors.notes?.message} />

                <Stack gap={4} aria-label="Totais do pedido">
                  <Group justify="space-between">
                    <Text size="sm">Itens</Text>
                    <Text size="sm">{formatCents(subtotal)}</Text>
                  </Group>
                  {discountCents > 0 && (
                    <Group justify="space-between">
                      <Text size="sm">Desconto</Text>
                      <Text size="sm">- {formatCents(discountCents)}</Text>
                    </Group>
                  )}
                  {type === 'DELIVERY' && (
                    <Group justify="space-between">
                      <Text size="sm">Entrega</Text>
                      <Text size="sm">{formatCents(feeCents)}</Text>
                    </Group>
                  )}
                  <Group justify="space-between">
                    <Text fw={700}>Total</Text>
                    <Text fw={700}>{formatCents(total)}</Text>
                  </Group>
                </Stack>

                {errors.root && (
                  <Alert color="red" icon={<CircleAlert size={18} />}>
                    {errors.root.message}
                  </Alert>
                )}
                <Button type="submit" size="md" loading={create.isPending} disabled={lines.length === 0}>
                  Lançar pedido
                </Button>
              </Stack>
            </Card>
          </form>
        </Grid.Col>
      </Grid>

      <ItemBuilderModal
        product={building}
        optionGroups={catalog.optionGroups}
        onClose={() => setBuilding(null)}
        onAdd={(line) => {
          setLines((current) => addLine(current, line));
          setBuilding(null);
        }}
      />
    </Stack>
  );
}
