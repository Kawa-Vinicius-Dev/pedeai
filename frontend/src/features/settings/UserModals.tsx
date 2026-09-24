import { zodResolver } from '@hookform/resolvers/zod';
import { Alert, Button, Group, Modal, PasswordInput, Select, Stack, Switch, TextInput } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { CircleAlert } from 'lucide-react';
import { useEffect } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { z } from 'zod';
import { api } from '../../shared/api/client';
import { unwrap } from '../../shared/api/errors';
import type { CreateUserRequest, Role, UpdateUserRequest, User } from '../../shared/api/types';
import { applyApiError } from '../../shared/lib/forms';
import { ROLE_OPTIONS } from '../../shared/lib/roles';

const ROLES = ['OWNER', 'MANAGER', 'CASHIER', 'WAITER', 'KITCHEN'] as const satisfies readonly Role[];

const createSchema = z.object({
  name: z.string().trim().min(1, 'Informe o nome.').max(120, 'Use até 120 caracteres.'),
  email: z.string().trim().min(1, 'Informe o e-mail.').pipe(z.email('E-mail inválido.')),
  password: z.string().min(8, 'A senha deve ter pelo menos 8 caracteres.').max(72, 'Use até 72 caracteres.'),
  role: z.enum(ROLES, { error: 'Escolha o papel.' }),
});

const editSchema = z.object({
  name: z.string().trim().min(1, 'Informe o nome.').max(120, 'Use até 120 caracteres.'),
  role: z.enum(ROLES, { error: 'Escolha o papel.' }),
  active: z.boolean(),
  password: z
    .string()
    .refine((value) => value === '' || (value.length >= 8 && value.length <= 72), {
      message: 'A nova senha deve ter entre 8 e 72 caracteres.',
    }),
});

type CreateForm = z.infer<typeof createSchema>;
type EditForm = z.infer<typeof editSchema>;

export function CreateUserModal({ opened, onClose }: { opened: boolean; onClose: () => void }) {
  const queryClient = useQueryClient();
  const {
    register,
    control,
    handleSubmit,
    reset,
    setError,
    formState: { errors },
  } = useForm<CreateForm>({
    resolver: zodResolver(createSchema),
    defaultValues: { name: '', email: '', password: '', role: 'CASHIER' },
  });

  const create = useMutation({
    mutationFn: (body: CreateUserRequest) => unwrap(api.POST('/api/users', { body })),
    onSuccess: async (user) => {
      await queryClient.invalidateQueries({ queryKey: ['users'] });
      notifications.show({ color: 'green', message: `${user.name} foi adicionado(a) à equipe.` });
      close();
    },
    onError: (error) => applyApiError(error, setError),
  });

  function close() {
    reset();
    onClose();
  }

  return (
    <Modal opened={opened} onClose={close} title="Adicionar pessoa">
      <form onSubmit={handleSubmit((form) => create.mutate(form))} noValidate>
        <Stack>
          {errors.root && (
            <Alert color="red" icon={<CircleAlert size={18} />}>
              {errors.root.message}
            </Alert>
          )}
          <TextInput label="Nome" data-autofocus {...register('name')} error={errors.name?.message} />
          <TextInput label="E-mail" type="email" {...register('email')} error={errors.email?.message} />
          <PasswordInput
            label="Senha inicial"
            description="A pessoa entra com esta senha. Pelo menos 8 caracteres."
            autoComplete="new-password"
            {...register('password')}
            error={errors.password?.message}
          />
          <Controller
            control={control}
            name="role"
            render={({ field }) => (
              <RoleSelect value={field.value} onChange={field.onChange} error={errors.role?.message} />
            )}
          />
          <Group justify="flex-end">
            <Button variant="default" onClick={close}>
              Cancelar
            </Button>
            <Button type="submit" loading={create.isPending}>
              Adicionar
            </Button>
          </Group>
        </Stack>
      </form>
    </Modal>
  );
}

export function EditUserModal({ user, onClose }: { user: User | null; onClose: () => void }) {
  const queryClient = useQueryClient();
  const {
    register,
    control,
    handleSubmit,
    reset,
    setError,
    formState: { errors },
  } = useForm<EditForm>({ resolver: zodResolver(editSchema) });

  useEffect(() => {
    if (user) {
      reset({ name: user.name, role: user.role, active: user.active, password: '' });
    }
  }, [user, reset]);

  const update = useMutation({
    mutationFn: ({ id, body }: { id: string; body: UpdateUserRequest }) =>
      unwrap(api.PATCH('/api/users/{id}', { params: { path: { id } }, body })),
    onSuccess: async (updated) => {
      await queryClient.invalidateQueries({ queryKey: ['users'] });
      notifications.show({ color: 'green', message: `Dados de ${updated.name} salvos.` });
      onClose();
    },
    onError: (error) => applyApiError(error, setError),
  });

  return (
    <Modal opened={user !== null} onClose={onClose} title={user ? `Editar ${user.name}` : ''}>
      {user && (
        <form
          onSubmit={handleSubmit(({ password, ...form }) =>
            update.mutate({ id: user.id, body: password ? { ...form, password } : form }),
          )}
          noValidate
        >
          <Stack>
            {errors.root && (
              <Alert color="red" icon={<CircleAlert size={18} />}>
                {errors.root.message}
              </Alert>
            )}
            <TextInput label="Nome" {...register('name')} error={errors.name?.message} />
            <Controller
              control={control}
              name="role"
              render={({ field }) => (
                <RoleSelect value={field.value} onChange={field.onChange} error={errors.role?.message} />
              )}
            />
            <Controller
              control={control}
              name="active"
              render={({ field }) => (
                <Switch
                  label="Acesso ativo"
                  description="Desativar encerra as sessões abertas da pessoa."
                  checked={field.value ?? false}
                  onChange={(event) => field.onChange(event.currentTarget.checked)}
                />
              )}
            />
            <PasswordInput
              label="Nova senha"
              description="Deixe em branco para manter a atual. Trocar encerra as sessões abertas."
              autoComplete="new-password"
              {...register('password')}
              error={errors.password?.message}
            />
            <Group justify="flex-end">
              <Button variant="default" onClick={onClose}>
                Cancelar
              </Button>
              <Button type="submit" loading={update.isPending}>
                Salvar
              </Button>
            </Group>
          </Stack>
        </form>
      )}
    </Modal>
  );
}

function RoleSelect({ value, onChange, error }: { value: Role; onChange: (role: Role) => void; error?: string }) {
  return (
    <Select
      label="Papel"
      data={ROLE_OPTIONS}
      value={value ?? null}
      onChange={(selected) => selected && onChange(selected as Role)}
      error={error}
      allowDeselect={false}
    />
  );
}
