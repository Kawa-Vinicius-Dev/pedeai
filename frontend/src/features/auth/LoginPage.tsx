import { zodResolver } from '@hookform/resolvers/zod';
import { Alert, Anchor, Button, PasswordInput, Stack, Text, TextInput } from '@mantine/core';
import { CircleAlert } from 'lucide-react';
import { useForm } from 'react-hook-form';
import { Link, useLocation, useNavigate } from 'react-router';
import { z } from 'zod';
import { applyApiError } from '../../shared/lib/forms';
import { useAuth } from './auth-context';
import { AuthCard } from './AuthCard';

const schema = z.object({
  email: z.string().trim().min(1, 'Informe o e-mail.').pipe(z.email('E-mail inválido.')),
  password: z.string().min(1, 'Informe a senha.'),
});

type LoginForm = z.infer<typeof schema>;

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from ?? '/';
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<LoginForm>({ resolver: zodResolver(schema), defaultValues: { email: '', password: '' } });

  const onSubmit = handleSubmit(async ({ email, password }) => {
    try {
      await login(email, password);
      navigate(from, { replace: true });
    } catch (error) {
      applyApiError(error, setError);
    }
  });

  return (
    <AuthCard title="Entrar" subtitle="Acesse os pedidos da sua loja.">
      <form onSubmit={onSubmit} noValidate>
        <Stack>
          {errors.root && (
            <Alert color="red" icon={<CircleAlert size={18} />}>
              {errors.root.message}
            </Alert>
          )}
          <TextInput label="E-mail" type="email" autoComplete="email" {...register('email')} error={errors.email?.message} />
          <PasswordInput
            label="Senha"
            autoComplete="current-password"
            {...register('password')}
            error={errors.password?.message}
          />
          <Button type="submit" loading={isSubmitting} fullWidth>
            Entrar
          </Button>
        </Stack>
      </form>
      <Text size="sm" ta="center">
        Ainda não tem conta?{' '}
        <Anchor component={Link} to="/cadastro">
          Cadastre sua loja
        </Anchor>
      </Text>
    </AuthCard>
  );
}
