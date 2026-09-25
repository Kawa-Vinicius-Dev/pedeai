import {
  ActionIcon,
  Alert,
  Badge,
  Button,
  Card,
  Checkbox,
  Group,
  List,
  Loader,
  Stack,
  Switch,
  Table,
  Text,
  TextInput,
  ThemeIcon,
  Title,
  Tooltip,
} from '@mantine/core';
import { useMediaQuery } from '@mantine/hooks';
import { Calculator, Check, CircleAlert, Pencil, Plus, Search } from 'lucide-react';
import { useState } from 'react';
import { Link } from 'react-router';
import { errorMessage } from '../../shared/api/errors';
import type { Category, Product } from '../../shared/api/types';
import { formatCents } from '../../shared/lib/numbers';
import { normalizeSearch } from '../../shared/lib/text';
import { type CatalogData, useCatalog, useProductAvailability } from './api';
import { PriceSimulator } from './PriceSimulator';
import { ProductDrawer, type ProductTarget } from './ProductDrawer';

export function ProductsTab({ manage }: { manage: boolean }) {
  const { data: catalog, error } = useCatalog();
  const availability = useProductAvailability();
  const [search, setSearch] = useState('');
  const [showInactive, setShowInactive] = useState(false);
  const [editing, setEditing] = useState<ProductTarget | null>(null);
  const [simulating, setSimulating] = useState<Product | null>(null);
  // Tela estreita (celular, tablet com menu aberto): lista compacta, com o botão de pausa sempre à vista.
  const compact = useMediaQuery('(max-width: 61.99em)') ?? false;

  if (!catalog) {
    return error ? (
      <Alert color="red" icon={<CircleAlert size={18} />}>
        {errorMessage(error)}
      </Alert>
    ) : (
      <Loader aria-label="Carregando cardápio" />
    );
  }

  if (catalog.products.length === 0) {
    return manage ? (
      <>
        <SetupSteps catalog={catalog} onNewProduct={() => setEditing({ categoryId: null })} />
        <ProductDrawer target={editing} catalog={catalog} onClose={() => setEditing(null)} />
      </>
    ) : (
      <Text c="dimmed">O cardápio ainda está vazio.</Text>
    );
  }

  const term = normalizeSearch(search);
  const visible = catalog.products.filter(
    (product) =>
      (showInactive || product.active) &&
      (term === '' ||
        normalizeSearch(product.name).includes(term) ||
        (product.code !== null && normalizeSearch(product.code).includes(term))),
  );
  // Sem busca, quem cadastra vê também as categorias vazias, para pôr produto nelas.
  const sections = catalog.categories
    .map((category) => ({ category, products: visible.filter((product) => product.categoryId === category.id) }))
    .filter(
      (section) =>
        section.products.length > 0 || (manage && term === '' && (section.category.active || showInactive)),
    );

  return (
    <Stack>
      <Group justify="space-between">
        <Group>
          <TextInput
            aria-label="Buscar produto"
            placeholder="Buscar por nome ou código PDV"
            leftSection={<Search size={16} />}
            value={search}
            onChange={(event) => setSearch(event.currentTarget.value)}
            w={280}
          />
          <Checkbox
            label="Mostrar inativos"
            checked={showInactive}
            onChange={(event) => setShowInactive(event.currentTarget.checked)}
          />
        </Group>
        {manage && (
          <Button leftSection={<Plus size={18} />} onClick={() => setEditing({ categoryId: null })}>
            Novo produto
          </Button>
        )}
      </Group>

      {sections.length === 0 && <Text c="dimmed">Nenhum produto encontrado.</Text>}
      {sections.map(({ category, products }) => (
        <Card key={category.id} withBorder radius="lg" padding={0}>
          <Group justify="space-between" px="md" py="sm">
            <Group gap="xs">
              <Title order={4}>{category.name}</Title>
              {!category.active && (
                <Badge variant="light" color="gray">
                  Categoria inativa
                </Badge>
              )}
            </Group>
            {manage && (
              <Button
                variant="subtle"
                size="xs"
                leftSection={<Plus size={14} />}
                onClick={() => setEditing({ categoryId: category.id })}
                aria-label={`Novo produto em ${category.name}`}
              >
                Produto
              </Button>
            )}
          </Group>
          {products.length === 0 && (
            <Text size="sm" c="dimmed" px="md" pb="md">
              Nenhum produto nesta categoria.
            </Text>
          )}
          {products.length > 0 &&
            (compact ? (
              <Stack gap={0}>
                {products.map((product) => (
                  <Group
                    key={product.id}
                    justify="space-between"
                    wrap="nowrap"
                    px="md"
                    py="sm"
                    opacity={product.active ? 1 : 0.6}
                    style={{ borderTop: '1px solid var(--mantine-color-default-border)' }}
                  >
                    <Stack gap={2} style={{ minWidth: 0 }}>
                      <ProductName product={product} />
                      <Text size="sm">
                        {formatCents(product.priceCents)}
                        {product.optionGroupIds.length > 0 && ' + adicionais'}
                      </Text>
                    </Stack>
                    <Group gap={4} wrap="nowrap">
                      <AvailabilitySwitch product={product} onChange={availability.mutate} />
                      <ProductActions
                        product={product}
                        manage={manage}
                        onSimulate={setSimulating}
                        onEdit={(current) => setEditing({ product: current })}
                      />
                    </Group>
                  </Group>
                ))}
              </Stack>
            ) : (
              <Table verticalSpacing="sm" highlightOnHover layout="fixed">
                <Table.Thead>
                  <Table.Tr>
                    <Table.Th>Produto</Table.Th>
                    <Table.Th w={130}>Preço</Table.Th>
                    <Table.Th w={170}>Setor</Table.Th>
                    <Table.Th w={90}>À venda</Table.Th>
                    <Table.Th w={manage ? 90 : 56} />
                  </Table.Tr>
                </Table.Thead>
                <Table.Tbody>
                  {products.map((product) => (
                    <Table.Tr key={product.id} opacity={product.active ? 1 : 0.6}>
                      <Table.Td>
                        <ProductName product={product} />
                      </Table.Td>
                      <Table.Td>
                        <Text>{formatCents(product.priceCents)}</Text>
                        {product.optionGroupIds.length > 0 && (
                          <Text size="xs" c="dimmed">
                            + adicionais
                          </Text>
                        )}
                      </Table.Td>
                      <Table.Td>
                        <SectorCell product={product} category={category} catalog={catalog} />
                      </Table.Td>
                      <Table.Td>
                        <AvailabilitySwitch product={product} onChange={availability.mutate} />
                      </Table.Td>
                      <Table.Td>
                        <ProductActions
                          product={product}
                          manage={manage}
                          onSimulate={setSimulating}
                          onEdit={(current) => setEditing({ product: current })}
                        />
                      </Table.Td>
                    </Table.Tr>
                  ))}
                </Table.Tbody>
              </Table>
            ))}
        </Card>
      ))}

      <ProductDrawer target={editing} catalog={catalog} onClose={() => setEditing(null)} />
      <PriceSimulator product={simulating} optionGroups={catalog.optionGroups} onClose={() => setSimulating(null)} />
    </Stack>
  );
}

function ProductName({ product }: { product: Product }) {
  return (
    <>
      <Group gap="xs">
        <Text fw={500}>{product.name}</Text>
        {!product.available && product.active && (
          <Badge size="sm" color="yellow" variant="light">
            Pausado
          </Badge>
        )}
        {!product.active && (
          <Badge size="sm" color="gray" variant="light">
            Inativo
          </Badge>
        )}
      </Group>
      <Text size="xs" c="dimmed" lineClamp={1}>
        {[product.code && `PDV ${product.code}`, product.description].filter(Boolean).join(' · ')}
      </Text>
    </>
  );
}

function AvailabilitySwitch({
  product,
  onChange,
}: {
  product: Product;
  onChange: (change: { id: string; available: boolean }) => void;
}) {
  return (
    <Switch
      aria-label={`${product.name} à venda`}
      checked={product.available}
      disabled={!product.active}
      onChange={(event) => onChange({ id: product.id, available: event.currentTarget.checked })}
    />
  );
}

function ProductActions({
  product,
  manage,
  onSimulate,
  onEdit,
}: {
  product: Product;
  manage: boolean;
  onSimulate: (product: Product) => void;
  onEdit: (product: Product) => void;
}) {
  return (
    <Group gap={4} justify="flex-end" wrap="nowrap">
      <Tooltip label="Simular preço">
        <ActionIcon
          variant="subtle"
          color="gray"
          aria-label={`Simular preço de ${product.name}`}
          onClick={() => onSimulate(product)}
        >
          <Calculator size={18} />
        </ActionIcon>
      </Tooltip>
      {manage && (
        <Tooltip label="Editar">
          <ActionIcon variant="subtle" color="gray" aria-label={`Editar ${product.name}`} onClick={() => onEdit(product)}>
            <Pencil size={18} />
          </ActionIcon>
        </Tooltip>
      )}
    </Group>
  );
}

/** Setor que vale para o produto e de onde ele veio: do produto, da categoria ou o padrão da loja. */
function SectorCell({ product, category, catalog }: { product: Product; category: Category; catalog: CatalogData }) {
  const sector = catalog.sectors.find((current) => current.id === product.effectiveSectorId);
  if (!sector) {
    return (
      <Text size="sm" c="red">
        Sem setor
      </Text>
    );
  }
  const origin = product.sectorId ? null : category.defaultSectorId ? 'da categoria' : 'padrão da loja';
  return (
    <>
      <Text size="sm">{sector.name}</Text>
      {origin && (
        <Text size="xs" c="dimmed">
          {origin}
        </Text>
      )}
    </>
  );
}

/** Cardápio vazio: a ordem que evita voltar atrás (o produto precisa de categoria, a categoria usa setor). */
function SetupSteps({ catalog, onNewProduct }: { catalog: CatalogData; onNewProduct: () => void }) {
  const steps = [
    {
      done: catalog.sectors.length > 0,
      title: 'Setores de produção',
      text: 'Onde cada item é preparado, como Cozinha e Bar. Define a tela e a impressora de cada item.',
      action: (
        <Button component={Link} to="/cardapio/setores" size="xs" variant="light">
          Ir para Setores
        </Button>
      ),
    },
    {
      done: catalog.categories.length > 0,
      title: 'Categorias',
      text: 'Como o cardápio se divide: Pizzas, Lanches, Bebidas.',
      action: (
        <Button component={Link} to="/cardapio/categorias" size="xs" variant="light">
          Ir para Categorias
        </Button>
      ),
    },
    {
      done: catalog.optionGroups.length > 0,
      title: 'Adicionais (se tiver)',
      text: 'Sabores de pizza, bordas, ponto da carne, adicionais do lanche.',
      action: (
        <Button component={Link} to="/cardapio/adicionais" size="xs" variant="light">
          Ir para Adicionais
        </Button>
      ),
    },
    {
      done: false,
      title: 'Produtos',
      text: 'Cada item com preço, categoria e, se usar iFood ou 99Food, o código PDV.',
      action: (
        <Button size="xs" onClick={onNewProduct} disabled={catalog.categories.length === 0}>
          Novo produto
        </Button>
      ),
    },
  ];

  return (
    <Card withBorder radius="lg" padding="lg" maw={720}>
      <Stack>
        <Stack gap={4}>
          <Title order={4}>Monte o cardápio em 4 passos</Title>
          <Text c="dimmed" size="sm">
            Nada disso é definitivo: dá para editar tudo depois.
          </Text>
        </Stack>
        <List spacing="lg" center={false}>
          {steps.map((step, index) => (
            <List.Item
              key={step.title}
              icon={
                <ThemeIcon size={28} radius="xl" color={step.done ? 'green' : 'orange'} variant="light">
                  {step.done ? <Check size={16} /> : <Text fw={700}>{index + 1}</Text>}
                </ThemeIcon>
              }
            >
              <Stack gap={6}>
                <Text fw={600}>{step.title}</Text>
                <Text size="sm" c="dimmed">
                  {step.text}
                </Text>
                {!step.done && <Group>{step.action}</Group>}
              </Stack>
            </List.Item>
          ))}
        </List>
      </Stack>
    </Card>
  );
}
