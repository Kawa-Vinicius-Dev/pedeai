import { zodResolver } from '@hookform/resolvers/zod';
import {
  ActionIcon,
  Alert,
  Box,
  Button,
  Drawer,
  Group,
  NumberInput,
  Paper,
  Radio,
  SimpleGrid,
  Stack,
  Switch,
  Text,
  TextInput,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { ArrowDown, ArrowUp, CircleAlert, Plus, Trash2 } from 'lucide-react';
import { Controller, useFieldArray, useForm, useWatch } from 'react-hook-form';
import { z } from 'zod';
import { api } from '../../shared/api/client';
import { unwrap } from '../../shared/api/errors';
import type { OptionGroup, OptionGroupRequest } from '../../shared/api/types';
import { applyApiError } from '../../shared/lib/forms';
import { centsToReais, decimalField, moneyField } from '../../shared/lib/numbers';
import { MoneyInput } from '../../shared/ui/MoneyInput';
import { invalidateCatalog } from './api';
import { PRICING_RULE_HELP, PRICING_RULE_LABELS, PRICING_RULES } from './labels';

const optionSchema = z.object({
  // "optionId" e não "id": o useFieldArray usa "id" para a chave de cada linha.
  optionId: z.string().nullable(),
  name: z.string().trim().min(1, 'Informe o nome.').max(80, 'Use até 80 caracteres.'),
  price: moneyField('Informe o preço (use 0 se não cobra).'),
  code: z.string().trim().max(40, 'Use até 40 caracteres.'),
  available: z.boolean(),
});

const schema = z
  .object({
    name: z.string().trim().min(1, 'Informe o nome do grupo.').max(80, 'Use até 80 caracteres.'),
    pricingRule: z.enum(PRICING_RULES),
    minChoices: decimalField('Informe o mínimo (0 se for opcional).').pipe(
      z.number().int().min(0, 'O mínimo não pode ser negativo.').max(50, 'O mínimo pode ser no máximo 50.'),
    ),
    maxChoices: decimalField('Informe o máximo.').pipe(
      z.number().int().min(1, 'O máximo deve ser pelo menos 1.').max(50, 'O máximo pode ser no máximo 50.'),
    ),
    active: z.boolean(),
    options: z.array(optionSchema).min(1, 'Cadastre pelo menos uma opção.').max(100, 'Use até 100 opções.'),
  })
  .refine((form) => form.minChoices <= form.maxChoices, {
    path: ['minChoices'],
    message: 'O mínimo não pode ser maior que o máximo.',
  });

type GroupFormInput = z.input<typeof schema>;
type GroupForm = z.output<typeof schema>;

function emptyOption(): GroupFormInput['options'][number] {
  return { optionId: null, name: '', price: '', code: '', available: true };
}

function toForm(group: OptionGroup | null): GroupFormInput {
  if (!group) {
    return { name: '', pricingRule: 'SUM', minChoices: 0, maxChoices: 1, active: true, options: [emptyOption()] };
  }
  return {
    name: group.name,
    pricingRule: group.pricingRule,
    minChoices: group.minChoices,
    maxChoices: group.maxChoices,
    active: group.active,
    // Opções retiradas não voltam para a tela. Ficam fora da lista enviada, e continuam retiradas.
    options: group.options
      .filter((option) => option.active)
      .map((option) => ({
        optionId: option.id,
        name: option.name,
        price: centsToReais(option.priceCents),
        code: option.code ?? '',
        available: option.available,
      })),
  };
}

function toRequest(form: GroupForm): OptionGroupRequest {
  return {
    name: form.name,
    pricingRule: form.pricingRule,
    minChoices: form.minChoices,
    maxChoices: form.maxChoices,
    active: form.active,
    options: form.options.map((option) => ({
      id: option.optionId ?? undefined,
      name: option.name,
      priceCents: option.price,
      code: option.code || undefined,
      available: option.available,
      active: true,
    })),
  };
}

export function OptionGroupDrawer({ target, onClose }: { target: OptionGroup | 'new' | null; onClose: () => void }) {
  const group = target === 'new' ? null : target;
  return (
    <Drawer
      opened={target !== null}
      onClose={onClose}
      position="right"
      size="xl"
      title={group ? `Editar ${group.name}` : 'Novo grupo de adicionais'}
    >
      {target && <GroupFormBody key={group?.id ?? 'new'} group={group} onClose={onClose} />}
    </Drawer>
  );
}

function GroupFormBody({ group, onClose }: { group: OptionGroup | null; onClose: () => void }) {
  const queryClient = useQueryClient();
  const {
    register,
    control,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<GroupFormInput, unknown, GroupForm>({
    resolver: zodResolver(schema),
    defaultValues: toForm(group),
  });
  const { fields, append, remove, swap } = useFieldArray({ control, name: 'options' });
  const pricingRule = useWatch({ control, name: 'pricingRule' });

  const save = useMutation({
    mutationFn: (body: OptionGroupRequest) =>
      group
        ? unwrap(api.PUT('/api/option-groups/{id}', { params: { path: { id: group.id } }, body }))
        : unwrap(api.POST('/api/option-groups', { body })),
    onSuccess: async (saved) => {
      await invalidateCatalog(queryClient);
      notifications.show({ color: 'green', message: `Grupo ${saved.name} salvo.` });
      onClose();
    },
    onError: (error) => applyApiError(error, setError, { priceCents: 'price', id: 'optionId' }),
  });

  return (
    <form onSubmit={handleSubmit((form) => save.mutate(toRequest(form)))} noValidate>
      <Stack>
        {errors.root && (
          <Alert color="red" icon={<CircleAlert size={18} />}>
            {errors.root.message}
          </Alert>
        )}
        <TextInput
          label="Nome do grupo"
          description="Como aparece no pedido: Sabores, Borda, Adicionais."
          data-autofocus
          {...register('name')}
          error={errors.name?.message}
        />
        <Controller
          control={control}
          name="pricingRule"
          render={({ field }) => (
            <Radio.Group label="Como as opções entram no preço" value={field.value} onChange={field.onChange}>
              <Stack gap="xs" mt={6}>
                {PRICING_RULES.map((rule) => (
                  <Radio key={rule} value={rule} label={PRICING_RULE_LABELS[rule]} description={PRICING_RULE_HELP[rule]} />
                ))}
              </Stack>
            </Radio.Group>
          )}
        />
        <SimpleGrid cols={2}>
          <Controller
            control={control}
            name="minChoices"
            render={({ field }) => (
              <NumberInput
                label="Mínimo de escolhas"
                description="0 deixa o grupo opcional."
                min={0}
                max={50}
                allowDecimal={false}
                allowNegative={false}
                value={field.value}
                onChange={field.onChange}
                onBlur={field.onBlur}
                error={errors.minChoices?.message}
              />
            )}
          />
          <Controller
            control={control}
            name="maxChoices"
            render={({ field }) => (
              <NumberInput
                label="Máximo de escolhas"
                description={pricingRule === 'SUM' ? 'Conta repetições (2x bacon).' : 'Pizza meio a meio: 2.'}
                min={1}
                max={50}
                allowDecimal={false}
                allowNegative={false}
                value={field.value}
                onChange={field.onChange}
                onBlur={field.onBlur}
                error={errors.maxChoices?.message}
              />
            )}
          />
        </SimpleGrid>

        <Stack gap="xs">
          <Text fw={500} size="sm">
            Opções
          </Text>
          {fields.length > 0 && (
            <Group gap="xs" wrap="nowrap" px={11} visibleFrom="sm" aria-hidden>
              <SimpleGrid cols={3} spacing="xs" style={{ flex: 1 }}>
                <Text size="xs" c="dimmed">
                  Nome
                </Text>
                <Text size="xs" c="dimmed">
                  Preço
                </Text>
                <Text size="xs" c="dimmed">
                  Código PDV (opcional)
                </Text>
              </SimpleGrid>
              <Box w={88} />
            </Group>
          )}
          {fields.map((field, index) => (
            <Paper key={field.id} withBorder p="xs" radius="md">
              <Group align="flex-start" gap="xs" wrap="nowrap">
                <SimpleGrid cols={{ base: 1, sm: 3 }} spacing="xs" style={{ flex: 1 }}>
                  <TextInput
                    aria-label={`Nome da opção ${index + 1}`}
                    placeholder="Nome (ex.: Calabresa)"
                    {...register(`options.${index}.name`)}
                    error={errors.options?.[index]?.name?.message}
                  />
                  <Controller
                    control={control}
                    name={`options.${index}.price`}
                    render={({ field: priceField }) => (
                      <MoneyInput
                        aria-label={`Preço da opção ${index + 1}`}
                        placeholder="R$ 0,00"
                        value={priceField.value}
                        onChange={priceField.onChange}
                        onBlur={priceField.onBlur}
                        error={errors.options?.[index]?.price?.message}
                      />
                    )}
                  />
                  <TextInput
                    aria-label={`Código PDV da opção ${index + 1}`}
                    placeholder="Código PDV (opcional)"
                    {...register(`options.${index}.code`)}
                    error={errors.options?.[index]?.code?.message}
                  />
                </SimpleGrid>
                <Group gap={2} wrap="nowrap">
                  <ActionIcon
                    variant="subtle"
                    color="gray"
                    aria-label={`Subir opção ${index + 1}`}
                    disabled={index === 0}
                    onClick={() => swap(index, index - 1)}
                  >
                    <ArrowUp size={16} />
                  </ActionIcon>
                  <ActionIcon
                    variant="subtle"
                    color="gray"
                    aria-label={`Descer opção ${index + 1}`}
                    disabled={index === fields.length - 1}
                    onClick={() => swap(index, index + 1)}
                  >
                    <ArrowDown size={16} />
                  </ActionIcon>
                  <ActionIcon
                    variant="subtle"
                    color="red"
                    aria-label={`Remover opção ${index + 1}`}
                    onClick={() => remove(index)}
                  >
                    <Trash2 size={16} />
                  </ActionIcon>
                </Group>
              </Group>
            </Paper>
          ))}
          {(errors.options?.message ?? errors.options?.root?.message) && (
            <Text c="red" size="sm">
              {errors.options?.message ?? errors.options?.root?.message}
            </Text>
          )}
          <Group>
            <Button variant="light" size="xs" leftSection={<Plus size={14} />} onClick={() => append(emptyOption())}>
              Adicionar opção
            </Button>
          </Group>
          {group && (
            <Text size="xs" c="dimmed">
              Remover uma opção tira ela do cardápio, mas pedidos antigos continuam com ela. Para trazer de volta,
              cadastre de novo com o mesmo código PDV.
            </Text>
          )}
        </Stack>

        {group && (
          <Controller
            control={control}
            name="active"
            render={({ field }) => (
              <Switch
                label="Grupo ativo"
                description="Grupo inativo deixa de aparecer nos produtos que o usam."
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
