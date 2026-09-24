import { zodResolver } from '@hookform/resolvers/zod';
import { Alert, Anchor, Button, PasswordInput, Stack, Text, TextInput } from '@mantine/core';
import { CircleAlert } from 'lucide-react';
import { useForm } from 'react-hook-form';
import { Link, useNavigate } from 'react-router';
import { z } from 'zod';
import { applyApiError } from '../../shared/lib/forms';
import { useAuth } from './auth-context';
import { AuthCard } from './AuthCard';

const schema = z
  .object({
    storeName: z.string().trim().min(1, 'Informe o nome da loja.').max(120, 'Use até 120 caracteres.'),
    ownerName: z.string().trim().min(1, 'Informe o seu nome.').max(120, 'Use até 120 caracteres.'),
    email: z.string().trim().min(1, 'Informe o e-mail.').pipe(z.email('E-mail inválido.')),
    password: z.string().min(8, 'A senha deve ter pelo menos 8 caracteres.').max(72, 'Use até 72 caracteres.'),
    confirmPassword: z.string(),
  })
  .refine((form) => form.password === form.confirmPassword, {
    path: ['confirmPassword'],
    message: 'As senhas não conferem.',
  });

type SignupForm = z.infer<typeof schema>;

export function SignupPage() {
  const { signup } = useAuth();
  const navigate = useNavigate();
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<SignupForm>({
    resolver: zodResolver(schema),
    defaultValues: { storeName: '', ownerName: '', email: '', password: '', confirmPassword: '' },
  });

  const onSubmit = handleSubmit(async ({ storeName, ownerName, email, password }) => {
    try {
      await signup({ storeName, ownerName, email, password });
      navigate('/', { replace: true });
    } catch (error) {
      applyApiError(error, setError);
    }
  });

  return (
    <AuthCard title="Cadastrar loja" subtitle="Crie a conta do restaurante. Você será o dono dela.">
      <form onSubmit={onSubmit} noValidate>
        <Stack>
          {errors.root && (
            <Alert color="red" icon={<CircleAlert size={18} />}>
              {errors.root.message}
            </Alert>
          )}
          <TextInput label="Nome da loja" {...register('storeName')} error={errors.storeName?.message} />
          <TextInput label="Seu nome" autoComplete="name" {...register('ownerName')} error={errors.ownerName?.message} />
          <TextInput label="E-mail" type="email" autoComplete="email" {...register('email')} error={errors.email?.message} />
          <PasswordInput
            label="Senha"
            description="Pelo menos 8 caracteres."
            autoComplete="new-password"
            {...register('password')}
            error={errors.password?.message}
          />
          <PasswordInput
            label="Confirme a senha"
            autoComplete="new-password"
            {...register('confirmPassword')}
            error={errors.confirmPassword?.message}
          />
          <Button type="submit" loading={isSubmitting} fullWidth>
            Criar conta
          </Button>
        </Stack>
      </form>
      <Text size="sm" ta="center">
        Já tem conta?{' '}
        <Anchor component={Link} to="/login">
          Entrar
        </Anchor>
      </Text>
    </AuthCard>
  );
}
