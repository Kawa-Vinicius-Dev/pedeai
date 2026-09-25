import { Alert, Badge, Button, Card, Group, Loader, SimpleGrid, Stack, Switch, Text, Title } from '@mantine/core';
import { CircleAlert, Pencil, Plus } from 'lucide-react';
import { useState } from 'react';
import { errorMessage } from '../../shared/api/errors';
import type { OptionGroup } from '../../shared/api/types';
import { formatCents } from '../../shared/lib/numbers';
import { useCatalog, useOptionAvailability } from './api';
import { describeChoices, PRICING_RULE_LABELS } from './labels';
import { OptionGroupDrawer } from './OptionGroupDrawer';

export function OptionGroupsTab({ manage }: { manage: boolean }) {
  const { data: catalog, error } = useCatalog();
  const availability = useOptionAvailability();
  const [editing, setEditing] = useState<OptionGroup | 'new' | null>(null);

  if (!catalog) {
    return error ? (
      <Alert color="red" icon={<CircleAlert size={18} />}>
        {errorMessage(error)}
      </Alert>
    ) : (
      <Loader aria-label="Carregando adicionais" />
    );
  }

  const { optionGroups, products } = catalog;

  return (
    <Stack>
      <Group justify="space-between" align="flex-end">
        <Text c="dimmed" size="sm" maw={560}>
          {manage
            ? 'Grupos de escolhas do produto: sabores da pizza, borda, ponto da carne, adicionais do lanche. Um grupo pode ser usado em vários produtos.'
            : 'Pause a opção que acabou (um sabor, um adicional) e libere quando voltar.'}
        </Text>
        {manage && (
          <Button leftSection={<Plus size={18} />} onClick={() => setEditing('new')}>
            Novo grupo
          </Button>
        )}
      </Group>

      {optionGroups.length === 0 && <Text c="dimmed">Nenhum grupo de adicionais ainda.</Text>}
      <SimpleGrid cols={{ base: 1, md: 2 }}>
        {optionGroups.map((group) => {
          const usedBy = products.filter((product) => product.active && product.optionGroupIds.includes(group.id));
          return (
            <Card key={group.id} withBorder radius="lg" padding="md" opacity={group.active ? 1 : 0.6}>
              <Stack gap="sm">
                <Group justify="space-between" wrap="nowrap" align="flex-start">
                  <Stack gap={2}>
                    <Group gap="xs">
                      <Title order={4}>{group.name}</Title>
                      <Badge variant="light" color="gray">
                        {PRICING_RULE_LABELS[group.pricingRule]}
                      </Badge>
                      {!group.active && (
                        <Badge variant="light" color="gray">
                          Inativo
                        </Badge>
                      )}
                    </Group>
                    <Text size="sm" c="dimmed">
                      {describeChoices(group.minChoices, group.maxChoices)} ·{' '}
                      {usedBy.length === 1 ? 'usado em 1 produto' : `usado em ${usedBy.length} produtos`}
                    </Text>
                  </Stack>
                  {manage && (
                    <Button
                      variant="subtle"
                      size="xs"
                      leftSection={<Pencil size={14} />}
                      onClick={() => setEditing(group)}
                      aria-label={`Editar ${group.name}`}
                    >
                      Editar
                    </Button>
                  )}
                </Group>
                <Stack gap={6}>
                  {group.options
                    .filter((option) => option.active)
                    .map((option) => (
                      <Group key={option.id} justify="space-between" wrap="nowrap">
                        <Stack gap={0}>
                          <Text size="sm">{option.name}</Text>
                          <Text size="xs" c="dimmed">
                            {[option.priceCents > 0 ? formatCents(option.priceCents) : 'grátis', option.code && `PDV ${option.code}`]
                              .filter(Boolean)
                              .join(' · ')}
                          </Text>
                        </Stack>
                        <Switch
                          aria-label={`${option.name} à venda`}
                          checked={option.available}
                          onChange={(event) =>
                            availability.mutate({
                              groupId: group.id,
                              optionId: option.id,
                              available: event.currentTarget.checked,
                            })
                          }
                        />
                      </Group>
                    ))}
                </Stack>
              </Stack>
            </Card>
          );
        })}
      </SimpleGrid>

      {manage && <OptionGroupDrawer target={editing} onClose={() => setEditing(null)} />}
    </Stack>
  );
}
