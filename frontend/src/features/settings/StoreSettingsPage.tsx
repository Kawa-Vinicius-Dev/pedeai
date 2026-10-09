import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert,
  Anchor,
  Button,
  Card,
  Checkbox,
  CopyButton,
  Group,
  Loader,
  Select,
  SimpleGrid,
  Stack,
  Switch,
  Text,
  TextInput,
  Title,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { CircleAlert } from 'lucide-react';
import { useEffect } from 'react';
import { Controller, useForm, useWatch } from 'react-hook-form';
import { z } from 'zod';
import { api } from '../../shared/api/client';
import { errorMessage, unwrap } from '../../shared/api/errors';
import type { Store, UpdateStoreRequest } from '../../shared/api/types';
import { applyApiError } from '../../shared/lib/forms';
import { useAuth } from '../auth/auth-context';

const TIME_ZONES = [
  { value: 'America/Sao_Paulo', label: 'Brasília (UTC-3): Sul, Sudeste, GO e DF' },
  { value: 'America/Bahia', label: 'Bahia (UTC-3)' },
  { value: 'America/Fortaleza', label: 'Fortaleza (UTC-3): CE, RN, PB, PI e MA' },
  { value: 'America/Recife', label: 'Recife (UTC-3): PE' },
  { value: 'America/Maceio', label: 'Maceió (UTC-3): AL e SE' },
  { value: 'America/Belem', label: 'Belém (UTC-3): PA e AP' },
  { value: 'America/Araguaina', label: 'Araguaína (UTC-3): TO' },
  { value: 'America/Manaus', label: 'Manaus (UTC-4): AM' },
  { value: 'America/Cuiaba', label: 'Cuiabá (UTC-4): MT' },
  { value: 'America/Campo_Grande', label: 'Campo Grande (UTC-4): MS' },
  { value: 'America/Porto_Velho', label: 'Porto Velho (UTC-4): RO' },
  { value: 'America/Boa_Vista', label: 'Boa Vista (UTC-4): RR' },
  { value: 'America/Rio_Branco', label: 'Rio Branco (UTC-5): AC' },
  { value: 'America/Noronha', label: 'Fernando de Noronha (UTC-2)' },
];

const schema = z.object({
  name: z.string().trim().min(1, 'Informe o nome da loja.').max(120, 'Use até 120 caracteres.'),
  document: z.string().trim().max(20, 'Use até 20 caracteres.'),
  phone: z.string().trim().max(20, 'Use até 20 caracteres.'),
  timezone: z.string().min(1, 'Escolha o fuso horário.'),
  businessDayCutoff: z.string().regex(/^\d{2}:\d{2}$/, 'Informe o horário.'),
  autoConfirmOwnOrders: z.boolean(),
  startPreparationOnConfirm: z.boolean(),
  slug: z
    .string()
    .trim()
    .min(3, 'Use pelo menos 3 caracteres.')
    .max(60, 'Use até 60 caracteres.')
    .regex(/^[a-z0-9]+(-[a-z0-9]+)*$/, 'Use só letras minúsculas, números e hífen (ex.: pizzaria-bella).'),
  menuAutoConfirm: z.boolean(),
  useHours: z.boolean(),
  hours: z.array(z.object({ enabled: z.boolean(), opensAt: z.string(), closesAt: z.string() })).length(7),
}).superRefine((form, context) => {
  if (!form.useHours) {
    return;
  }
  if (!form.hours.some((day) => day.enabled)) {
    context.addIssue({ code: 'custom', path: ['hours'], message: 'Marque pelo menos um dia, ou desligue o horário.' });
  }
  form.hours.forEach((day, index) => {
    if (!day.enabled) {
      return;
    }
    if (!/^\d{2}:\d{2}$/.test(day.opensAt) || !/^\d{2}:\d{2}$/.test(day.closesAt)) {
      context.addIssue({ code: 'custom', path: ['hours', index, 'opensAt'], message: 'Informe os dois horários.' });
    } else if (day.opensAt === day.closesAt) {
      context.addIssue({ code: 'custom', path: ['hours', index, 'opensAt'], message: 'Abre e fecha no mesmo horário.' });
    }
  });
});

const DAYS = ['Segunda', 'Terça', 'Quarta', 'Quinta', 'Sexta', 'Sábado', 'Domingo'];

type StoreFormInput = z.input<typeof schema>;
type StoreForm = z.output<typeof schema>;

function toForm(store: Store): StoreFormInput {
  return {
    name: store.name,
    document: store.document ?? '',
    phone: store.phone ?? '',
    timezone: store.timezone,
    businessDayCutoff: store.businessDayCutoff.slice(0, 5),
    autoConfirmOwnOrders: store.autoConfirmOwnOrders,
    startPreparationOnConfirm: store.startPreparationOnConfirm,
    slug: store.slug,
    menuAutoConfirm: store.menuAutoConfirm,
    useHours: store.openingHours.length > 0,
    hours: DAYS.map((_, index) => {
      const day = store.openingHours.find((hours) => hours.dayOfWeek === index + 1);
      return day
        ? { enabled: true, opensAt: day.opensAt.slice(0, 5), closesAt: day.closesAt.slice(0, 5) }
        : { enabled: false, opensAt: '18:00', closesAt: '23:00' };
    }),
  };
}

function toRequest(form: StoreForm): UpdateStoreRequest {
  return {
    name: form.name,
    document: form.document,
    phone: form.phone,
    timezone: form.timezone,
    businessDayCutoff: form.businessDayCutoff,
    autoConfirmOwnOrders: form.autoConfirmOwnOrders,
    startPreparationOnConfirm: form.startPreparationOnConfirm,
    slug: form.slug,
    menuAutoConfirm: form.menuAutoConfirm,
    openingHours: form.useHours
      ? form.hours.flatMap((day, index) =>
          day.enabled ? [{ dayOfWeek: index + 1, opensAt: day.opensAt, closesAt: day.closesAt }] : [],
        )
      : [],
  };
}

export function StoreSettingsPage() {
  const queryClient = useQueryClient();
  const { updateStoreName } = useAuth();
  const storeQuery = useQuery({ queryKey: ['store'], queryFn: () => unwrap(api.GET('/api/store')) });
  const {
    register,
    control,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isDirty },
  } = useForm<StoreFormInput, unknown, StoreForm>({ resolver: zodResolver(schema) });
  const useHours = useWatch({ control, name: 'useHours' });
  const hours = useWatch({ control, name: 'hours' });

  useEffect(() => {
    if (storeQuery.data) {
      reset(toForm(storeQuery.data));
    }
  }, [storeQuery.data, reset]);

  const save = useMutation({
    mutationFn: (body: UpdateStoreRequest) => unwrap(api.PATCH('/api/store', { body })),
    onSuccess: (store) => {
      queryClient.setQueryData(['store'], store);
      updateStoreName(store.name);
      reset(toForm(store));
      notifications.show({ color: 'green', message: 'Dados da loja salvos.' });
    },
    onError: (error) => applyApiError(error, setError),
  });

  if (storeQuery.isPending) {
    return <Loader aria-label="Carregando dados da loja" />;
  }
  if (storeQuery.isError) {
    return (
      <Alert color="red" icon={<CircleAlert size={18} />}>
        {errorMessage(storeQuery.error)}
      </Alert>
    );
  }

  return (
    <Stack maw={760} gap="lg">
      <Stack gap={4}>
        <Title order={2}>Loja</Title>
        <Text c="dimmed">Dados do restaurante e regras que valem para toda a operação.</Text>
      </Stack>

      <form onSubmit={handleSubmit((form) => save.mutate(toRequest(form)))} noValidate>
        <Stack gap="lg">
          {errors.root && (
            <Alert color="red" icon={<CircleAlert size={18} />}>
              {errors.root.message}
            </Alert>
          )}

          <Card withBorder radius="lg" padding="lg">
            <Stack>
              <Title order={4}>Dados da loja</Title>
              <TextInput label="Nome da loja" {...register('name')} error={errors.name?.message} />
              <SimpleGrid cols={{ base: 1, sm: 2 }}>
                <TextInput label="CNPJ ou CPF" {...register('document')} error={errors.document?.message} />
                <TextInput label="Telefone" type="tel" {...register('phone')} error={errors.phone?.message} />
              </SimpleGrid>
            </Stack>
          </Card>

          <Card withBorder radius="lg" padding="lg">
            <Stack>
              <Title order={4}>Cardápio digital</Title>
              <TextInput
                label="Endereço do cardápio"
                description="É o link que o cliente abre para pedir. Abrir e fechar para pedidos fica no quadro de pedidos."
                leftSection={<Text size="sm" c="dimmed">/loja/</Text>}
                leftSectionWidth={52}
                {...register('slug')}
                error={errors.slug?.message}
              />
              <Controller
                control={control}
                name="menuAutoConfirm"
                render={({ field }) => (
                  <Switch
                    label="Aceitar pedidos do cardápio automaticamente"
                    description="O pedido vai direto para a cozinha e a impressora, sem esperar em Recebidos."
                    checked={field.value ?? false}
                    onChange={(event) => field.onChange(event.currentTarget.checked)}
                  />
                )}
              />
              <Controller
                control={control}
                name="useHours"
                render={({ field }) => (
                  <Switch
                    label="Usar horário de funcionamento"
                    description="Fora do horário, o cardápio fica fechado para pedidos mesmo com a chave do quadro ligada."
                    checked={field.value ?? false}
                    onChange={(event) => field.onChange(event.currentTarget.checked)}
                  />
                )}
              />
              {useHours && (
                <Stack gap={6} aria-label="Horário de funcionamento">
                  {DAYS.map((label, index) => (
                    <Group key={label} gap="sm" wrap="nowrap" align="flex-start">
                      <Controller
                        control={control}
                        name={`hours.${index}.enabled`}
                        render={({ field }) => (
                          <Checkbox
                            label={label}
                            w={100}
                            mt={8}
                            checked={field.value ?? false}
                            onChange={(event) => field.onChange(event.currentTarget.checked)}
                          />
                        )}
                      />
                      <TextInput
                        type="time"
                        aria-label={`${label}: abre às`}
                        disabled={!hours?.[index]?.enabled}
                        {...register(`hours.${index}.opensAt`)}
                        error={errors.hours?.[index]?.opensAt?.message}
                      />
                      <TextInput
                        type="time"
                        aria-label={`${label}: fecha às`}
                        disabled={!hours?.[index]?.enabled}
                        {...register(`hours.${index}.closesAt`)}
                      />
                    </Group>
                  ))}
                  <Text size="xs" c="dimmed">
                    Fechar antes de abrir passa da meia-noite (ex.: 18:00 às 02:00).
                  </Text>
                  {(errors.hours?.root?.message ?? errors.hours?.message) && (
                    <Text size="sm" c="red">
                      {errors.hours?.root?.message ?? errors.hours?.message}
                    </Text>
                  )}
                </Stack>
              )}
              <Group gap="xs">
                <Anchor href={menuUrl(storeQuery.data.slug)} target="_blank" rel="noreferrer" size="sm">
                  {menuUrl(storeQuery.data.slug)}
                </Anchor>
                <CopyButton value={menuUrl(storeQuery.data.slug)}>
                  {({ copied, copy }) => (
                    <Button size="compact-xs" variant="light" onClick={copy}>
                      {copied ? 'Copiado' : 'Copiar link'}
                    </Button>
                  )}
                </CopyButton>
              </Group>
            </Stack>
          </Card>

          <Card withBorder radius="lg" padding="lg">
            <Stack>
              <Title order={4}>Operação</Title>
              <SimpleGrid cols={{ base: 1, sm: 2 }}>
                <Controller
                  control={control}
                  name="timezone"
                  render={({ field }) => (
                    <Select
                      label="Fuso horário"
                      data={TIME_ZONES}
                      value={field.value ?? null}
                      onChange={(value) => field.onChange(value ?? '')}
                      error={errors.timezone?.message}
                      allowDeselect={false}
                      searchable
                    />
                  )}
                />
                <TextInput
                  label="Virada do dia operacional"
                  description="Pedidos antes deste horário contam no dia anterior."
                  type="time"
                  {...register('businessDayCutoff')}
                  error={errors.businessDayCutoff?.message}
                />
              </SimpleGrid>
              <Controller
                control={control}
                name="autoConfirmOwnOrders"
                render={({ field }) => (
                  <Switch
                    label="Pedidos lançados pela equipe já nascem confirmados"
                    checked={field.value ?? false}
                    onChange={(event) => field.onChange(event.currentTarget.checked)}
                  />
                )}
              />
              <Controller
                control={control}
                name="startPreparationOnConfirm"
                render={({ field }) => (
                  <Switch
                    label="Confirmar o pedido já coloca em preparo (loja sem tela de cozinha)"
                    checked={field.value ?? false}
                    onChange={(event) => field.onChange(event.currentTarget.checked)}
                  />
                )}
              />
            </Stack>
          </Card>

          <Group justify="flex-end">
            <Button type="submit" loading={save.isPending} disabled={!isDirty}>
              Salvar
            </Button>
          </Group>
        </Stack>
      </form>
    </Stack>
  );
}

/** O link do cardápio, no mesmo endereço do painel. */
function menuUrl(slug: string): string {
  return `${window.location.origin}/loja/${slug}`;
}
