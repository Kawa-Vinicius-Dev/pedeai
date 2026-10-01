import {
  ActionIcon,
  Affix,
  Alert,
  Anchor,
  Badge,
  Button,
  Card,
  Container,
  Drawer,
  Group,
  Loader,
  Stack,
  Text,
  Title,
} from '@mantine/core';
import { CircleAlert, Minus, Phone, Plus, ShoppingBag } from 'lucide-react';
import { useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { addLine, cartSubtotal, changeQuantity, type CartLine, lineTotal } from '../features/orders/cart';
import { errorMessage } from '../shared/api/errors';
import type { MenuProduct, Storefront } from '../shared/api/types';
import { formatCents } from '../shared/lib/numbers';
import { useStorefront } from './api';
import { Checkout } from './Checkout';
import { ProductSheet } from './ProductSheet';
import { cartKey, clearCart, useStored } from './storage';

/** O cardápio da loja para o cliente: escolhe os itens, revisa o carrinho e faz o pedido. */
export function StorePage() {
  const { slug = '' } = useParams();
  const storefront = useStorefront(slug);

  if (storefront.isPending) {
    return (
      <Container py="xl">
        <Loader aria-label="Carregando o cardápio" />
      </Container>
    );
  }
  if (storefront.isError) {
    return (
      <Container py="xl">
        <Alert color="red" icon={<CircleAlert size={18} />}>
          {errorMessage(storefront.error)}
        </Alert>
      </Container>
    );
  }
  return <Menu key={slug} store={storefront.data} />;
}

function Menu({ store }: { store: Storefront }) {
  const navigate = useNavigate();
  const [saved, setLines] = useStored<CartLine[]>(cartKey(store.slug), []);
  // Carrinho guardado de outro dia: o que saiu do cardápio ou acabou não pode travar o pedido.
  const orderable = new Set(
    store.categories.flatMap((category) => category.products).filter((product) => product.available).map((product) => product.id),
  );
  const lines = saved.filter((line) => orderable.has(line.productId));
  const [picking, setPicking] = useState<MenuProduct | null>(null);
  const [step, setStep] = useState<'closed' | 'cart' | 'checkout'>('closed');
  const count = lines.reduce((sum, line) => sum + line.quantity, 0);

  function placed(trackingCode: string) {
    clearCart(store.slug);
    setLines([]);
    navigate(`/loja/${store.slug}/pedido/${trackingCode}`);
  }

  return (
    <Container size="sm" py="md" pb={120}>
      <Stack gap="lg">
        <Stack gap={4}>
          <Group justify="space-between" align="flex-start" wrap="nowrap">
            <Title order={1} fz={28}>
              {store.name}
            </Title>
            <Badge color={store.open ? 'green' : 'gray'} variant="light" size="lg">
              {store.open ? 'Aberto' : 'Fechado'}
            </Badge>
          </Group>
          {store.openingHours.length > 0 && (
            <Text size="sm" c="dimmed">
              {todayHours(store)}
            </Text>
          )}
          {store.phone && (
            <Anchor href={`tel:${store.phone}`} size="sm" c="dimmed">
              <Group gap={4} component="span">
                <Phone size={14} />
                {store.phone}
              </Group>
            </Anchor>
          )}
        </Stack>
        {!store.open && (
          <Alert color="gray" icon={<CircleAlert size={18} />}>
            A loja não está recebendo pedidos agora. Dá para ver o cardápio.
          </Alert>
        )}
        {store.categories.length === 0 && <Text c="dimmed">O cardápio ainda está sendo montado.</Text>}
        {store.categories.map((category) => (
          <Stack key={category.id} gap="xs">
            <Title order={2} fz={20}>
              {category.name}
            </Title>
            {category.products.map((product) => (
              <ProductCard key={product.id} product={product} onPick={() => setPicking(product)} />
            ))}
          </Stack>
        ))}
      </Stack>

      <ProductSheet
        slug={store.slug}
        product={picking}
        optionGroups={store.optionGroups}
        onClose={() => setPicking(null)}
        onAdd={(line) => {
          setLines(addLine(lines, line));
          setPicking(null);
        }}
      />

      {count > 0 && step === 'closed' && (
        <Affix position={{ bottom: 16, left: 16, right: 16 }}>
          <Container size="sm" px={0}>
            <Button fullWidth size="lg" leftSection={<ShoppingBag size={20} />} onClick={() => setStep('cart')}>
              Ver carrinho · {count} {count === 1 ? 'item' : 'itens'} · {formatCents(cartSubtotal(lines))}
            </Button>
          </Container>
        </Affix>
      )}

      <Drawer
        opened={step !== 'closed'}
        onClose={() => setStep('closed')}
        position="bottom"
        size="90%"
        title={step === 'checkout' ? 'Finalizar pedido' : 'Seu carrinho'}
      >
        {step === 'cart' && (
          <Stack>
            {lines.map((line) => (
              <Group key={line.key} justify="space-between" align="flex-start" wrap="nowrap">
                <Stack gap={0} style={{ flex: 1 }}>
                  <Text fw={500}>{line.name}</Text>
                  {line.optionsLabel && (
                    <Text size="sm" c="dimmed">
                      {line.optionsLabel}
                    </Text>
                  )}
                  {line.notes && (
                    <Text size="sm" c="dimmed">
                      Obs.: {line.notes}
                    </Text>
                  )}
                  <Text size="sm">{formatCents(lineTotal(line))}</Text>
                </Stack>
                <Group gap={6} wrap="nowrap">
                  <ActionIcon
                    variant="default"
                    aria-label={`Tirar um ${line.name}`}
                    onClick={() => setLines(changeQuantity(lines, line.key, line.quantity - 1))}
                  >
                    <Minus size={14} />
                  </ActionIcon>
                  <Text w={20} ta="center">
                    {line.quantity}
                  </Text>
                  <ActionIcon
                    variant="default"
                    aria-label={`Mais um ${line.name}`}
                    onClick={() => setLines(changeQuantity(lines, line.key, line.quantity + 1))}
                  >
                    <Plus size={14} />
                  </ActionIcon>
                </Group>
              </Group>
            ))}
            {lines.length === 0 && <Text c="dimmed">O carrinho está vazio.</Text>}
            <Group justify="space-between">
              <Text fw={700}>Subtotal</Text>
              <Text fw={700}>{formatCents(cartSubtotal(lines))}</Text>
            </Group>
            <Button size="md" disabled={lines.length === 0} onClick={() => setStep('checkout')}>
              Continuar
            </Button>
          </Stack>
        )}
        {step === 'checkout' && <Checkout store={store} lines={lines} onPlaced={placed} />}
      </Drawer>
    </Container>
  );
}

function ProductCard({ product, onPick }: { product: MenuProduct; onPick: () => void }) {
  const customizable = product.optionGroupIds.length > 0;
  const price =
    customizable && product.priceCents === 0
      ? 'Monte o seu'
      : `${customizable ? 'a partir de ' : ''}${formatCents(product.priceCents)}`;
  return (
    <Card
      withBorder
      radius="md"
      padding="sm"
      component="button"
      type="button"
      onClick={onPick}
      disabled={!product.available}
      aria-label={product.name}
      style={{ textAlign: 'left', cursor: product.available ? 'pointer' : 'not-allowed', opacity: product.available ? 1 : 0.6 }}
    >
      <Group justify="space-between" align="flex-start" wrap="nowrap">
        <Stack gap={2}>
          <Text fw={600}>{product.name}</Text>
          {product.description && (
            <Text size="sm" c="dimmed" lineClamp={2}>
              {product.description}
            </Text>
          )}
        </Stack>
        {product.available ? (
          <Text fw={600} style={{ whiteSpace: 'nowrap' }}>
            {price}
          </Text>
        ) : (
          <Badge color="gray" variant="light">
            Esgotado
          </Badge>
        )}
      </Group>
    </Card>
  );
}

/** "Hoje: 18:00 às 23:30" ou "Fechado hoje", pelo horário que a loja cadastrou. */
function todayHours(store: Storefront): string {
  const weekday = new Date().getDay();
  const today = store.openingHours.find((hours) => hours.dayOfWeek === (weekday === 0 ? 7 : weekday));
  return today ? `Hoje: ${today.opensAt.slice(0, 5)} às ${today.closesAt.slice(0, 5)}` : 'Fechado hoje';
}
