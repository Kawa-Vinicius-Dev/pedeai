import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert,
  Button,
  Card,
  Group,
  Loader,
  NumberInput,
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
import { Controller, useForm } from 'react-hook-form';
import { z } from 'zod';
import { api } from '../../shared/api/client';
import { errorMessage, unwrap } from '../../shared/api/errors';
import type { Store, UpdateStoreRequest } from '../../shared/api/types';
import { applyApiError } from '../../shared/lib/forms';
import { decimalField } from '../../shared/lib/numbers';
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
  serviceFeePercent: decimalField('Informe a taxa (use 0 se não cobra).').pipe(
    z.number().min(0, 'A taxa não pode ser negativa.').max(30, 'A taxa pode ser no máximo 30%.'),
  ),
  autoConfirmOwnOrders: z.boolean(),
  startPreparationOnConfirm: z.boolean(),
});

type StoreFormInput = z.input<typeof schema>;
type StoreForm = z.output<typeof schema>;

function toForm(store: Store): StoreFormInput {
  return {
    name: store.name,
    document: store.document ?? '',
    phone: store.phone ?? '',
    timezone: store.timezone,
    businessDayCutoff: store.businessDayCutoff.slice(0, 5),
    serviceFeePercent: store.serviceFeeBp / 100,
    autoConfirmOwnOrders: store.autoConfirmOwnOrders,
    startPreparationOnConfirm: store.startPreparationOnConfirm,
  };
}

function toRequest(form: StoreForm): UpdateStoreRequest {
  return {
    name: form.name,
    document: form.document,
    phone: form.phone,
    timezone: form.timezone,
    businessDayCutoff: form.businessDayCutoff,
    serviceFeeBp: Math.round(form.serviceFeePercent * 100),
    autoConfirmOwnOrders: form.autoConfirmOwnOrders,
    startPreparationOnConfirm: form.startPreparationOnConfirm,
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
                name="serviceFeePercent"
                render={({ field }) => (
                  <NumberInput
                    label="Taxa de serviço (mesas)"
                    description="O cliente pode recusar. Use 0 se a loja não cobra."
                    suffix="%"
                    decimalScale={2}
                    decimalSeparator=","
                    min={0}
                    max={30}
                    maw={260}
                    value={field.value ?? ''}
                    onChange={field.onChange}
                    onBlur={field.onBlur}
                    error={errors.serviceFeePercent?.message}
                  />
                )}
              />
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
