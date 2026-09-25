import { Badge, Chip, Group, Paper, ScrollArea, SimpleGrid, Stack, Text, TextInput, UnstyledButton } from '@mantine/core';
import { Search } from 'lucide-react';
import { useState } from 'react';
import type { Product } from '../../shared/api/types';
import { formatCents } from '../../shared/lib/numbers';
import { normalizeSearch } from '../../shared/lib/text';
import type { CatalogData } from '../catalog/api';

const ALL = 'all';

/** Grade de produtos do PDV: busca por nome ou código PDV e filtro por categoria. */
export function ProductPicker({ catalog, onPick }: { catalog: CatalogData; onPick: (product: Product) => void }) {
  const [search, setSearch] = useState('');
  const [categoryId, setCategoryId] = useState<string>(ALL);
  const categories = catalog.categories.filter((category) => category.active);
  const term = normalizeSearch(search);
  const products = catalog.products.filter(
    (product) =>
      product.active &&
      categories.some((category) => category.id === product.categoryId) &&
      (categoryId === ALL || product.categoryId === categoryId) &&
      (term === '' ||
        normalizeSearch(product.name).includes(term) ||
        (product.code !== null && normalizeSearch(product.code) === term)),
  );

  return (
    <Stack gap="sm">
      <TextInput
        aria-label="Buscar produto"
        placeholder="Buscar por nome ou código PDV"
        leftSection={<Search size={16} />}
        value={search}
        onChange={(event) => setSearch(event.currentTarget.value)}
        autoFocus
      />
      <ScrollArea type="auto" offsetScrollbars>
        <Chip.Group value={categoryId} onChange={(value) => setCategoryId(value as string)}>
          <Group gap="xs" wrap="nowrap">
            <Chip value={ALL} size="sm">
              Todos
            </Chip>
            {categories.map((category) => (
              <Chip key={category.id} value={category.id} size="sm">
                {category.name}
              </Chip>
            ))}
          </Group>
        </Chip.Group>
      </ScrollArea>
      {products.length === 0 && <Text c="dimmed">Nenhum produto encontrado.</Text>}
      <SimpleGrid cols={{ base: 2, sm: 3 }} spacing="sm">
        {products.map((product) => (
          <UnstyledButton
            key={product.id}
            onClick={() => onPick(product)}
            disabled={!product.available}
            aria-label={product.available ? `Adicionar ${product.name}` : `${product.name} (pausado)`}
          >
            <Paper withBorder radius="md" p="sm" h="100%" opacity={product.available ? 1 : 0.5}>
              <Stack gap={4}>
                <Text fw={600} size="sm" lineClamp={2}>
                  {product.name}
                </Text>
                <Group gap={6}>
                  <Text size="sm" c="dimmed">
                    {product.optionGroupIds.length > 0 && product.priceCents === 0
                      ? 'Escolher'
                      : formatCents(product.priceCents)}
                  </Text>
                  {!product.available && (
                    <Badge size="xs" color="yellow" variant="light">
                      Pausado
                    </Badge>
                  )}
                </Group>
              </Stack>
            </Paper>
          </UnstyledButton>
        ))}
      </SimpleGrid>
    </Stack>
  );
}
