import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert,
  Badge,
  Button,
  Card,
  Code,
  CopyButton,
  Group,
  Loader,
  Modal,
  Stack,
  Table,
  Text,
  TextInput,
  Title,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { CircleAlert, KeyRound, Plus } from 'lucide-react';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { api } from '../../shared/api/client';
import { errorMessage, unwrap } from '../../shared/api/errors';
import type { ApiKey } from '../../shared/api/types';
import { applyApiError } from '../../shared/lib/forms';

const dateTime = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' });
const keys = ['api-keys'] as const;

/**
 * Chaves da API de pedidos (docs/05-integracoes.md#api-de-pedidos-do-pedeaí): um site, um bot ou outro cardápio manda
 * pedidos para a loja com a chave no header X-Api-Key.
 */
export function ApiKeysPage() {
  const list = useQuery({ queryKey: keys, queryFn: () => unwrap(api.GET('/api/api-keys')) });
  const [creating, setCreating] = useState(false);
  const [revoking, setRevoking] = useState<ApiKey | null>(null);

  return (
    <Stack maw={860} gap="lg">
      <Group justify="space-between" align="flex-end">
        <Stack gap={4}>
          <Title order={2}>API de pedidos</Title>
          <Text c="dimmed">
            Para um site, um bot de WhatsApp ou outro cardápio mandar pedidos direto para o quadro. Cada sistema usa a
            sua chave; revogue a que não for mais usada.
          </Text>
        </Stack>
        <Button leftSection={<Plus size={18} />} onClick={() => setCreating(true)}>
          Nova chave
        </Button>
      </Group>
      <Card withBorder radius="lg">
        <Stack gap={4}>
          <Text fw={600} size="sm">
            Como usar
          </Text>
          <Text size="sm">
            Envie a chave no header <Code>X-Api-Key</Code>. <Code>GET /api/v1/menu</Code> traz o cardápio com os ids,{' '}
            <Code>POST /api/v1/orders</Code> cria o pedido (o mesmo <Code>externalId</Code> não duplica) e{' '}
            <Code>GET /api/v1/orders/{'{id}'}</Code> mostra o status. Preço e taxa de entrega são calculados pelo PedeAí.
          </Text>
        </Stack>
      </Card>
      <Card withBorder radius="lg" padding={0}>
        {list.isPending && <Loader m="md" aria-label="Carregando chaves" />}
        {list.isError && (
          <Alert m="md" color="red" icon={<CircleAlert size={18} />}>
            {errorMessage(list.error)}
          </Alert>
        )}
        {list.data && list.data.length === 0 && (
          <Text m="md" c="dimmed">
            Nenhuma chave criada.
          </Text>
        )}
        {list.data && list.data.length > 0 && (
          <Table verticalSpacing="sm" aria-label="Chaves da API">
            <Table.Thead>
              <Table.Tr>
                <Table.Th>Nome</Table.Th>
                <Table.Th>Chave</Table.Th>
                <Table.Th>Último uso</Table.Th>
                <Table.Th>Situação</Table.Th>
                <Table.Th />
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {list.data.map((key) => (
                <Table.Tr key={key.id}>
                  <Table.Td fw={500}>{key.name}</Table.Td>
                  <Table.Td>
                    <Code>{key.keyPrefix}…</Code>
                  </Table.Td>
                  <Table.Td>{key.lastUsedAt ? dateTime.format(new Date(key.lastUsedAt)) : 'Nunca'}</Table.Td>
                  <Table.Td>
                    <Badge variant="light" color={key.revokedAt ? 'gray' : 'green'}>
                      {key.revokedAt ? 'Revogada' : 'Ativa'}
                    </Badge>
                  </Table.Td>
                  <Table.Td ta="right">
                    {!key.revokedAt && (
                      <Button variant="subtle" color="red" size="xs" onClick={() => setRevoking(key)}>
                        Revogar
                      </Button>
                    )}
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        )}
      </Card>
      <Modal opened={creating} onClose={() => setCreating(false)} title="Nova chave da API">
        {creating && <CreateKey onClose={() => setCreating(false)} />}
      </Modal>
      <Modal opened={revoking !== null} onClose={() => setRevoking(null)} title="Revogar chave">
        {revoking && <RevokeKey apiKey={revoking} onClose={() => setRevoking(null)} />}
      </Modal>
    </Stack>
  );
}

const schema = z.object({ name: z.string().trim().min(1, 'Dê um nome para a chave.').max(60, 'Use até 60 caracteres.') });

function CreateKey({ onClose }: { onClose: () => void }) {
  const queryClient = useQueryClient();
  const [secret, setSecret] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<z.infer<typeof schema>>({ resolver: zodResolver(schema), defaultValues: { name: '' } });
  const create = useMutation({
    mutationFn: (name: string) => unwrap(api.POST('/api/api-keys', { body: { name } })),
    onSuccess: async (created) => {
      setSecret(created.secret);
      await queryClient.invalidateQueries({ queryKey: keys });
    },
    onError: (error) => applyApiError(error, setError),
  });

  if (secret) {
    return (
      <Stack>
        <Alert color="orange" icon={<KeyRound size={18} />}>
          Copie a chave agora e guarde no sistema que vai usar. Ela não aparece de novo.
        </Alert>
        <Code block aria-label="Chave da API">
          {secret}
        </Code>
        <Group justify="flex-end">
          <CopyButton value={secret}>
            {({ copied, copy }) => (
              <Button variant="light" onClick={copy}>
                {copied ? 'Copiada' : 'Copiar chave'}
              </Button>
            )}
          </CopyButton>
          <Button onClick={onClose}>Pronto</Button>
        </Group>
      </Stack>
    );
  }
  return (
    <form onSubmit={handleSubmit(({ name }) => create.mutate(name))} noValidate>
      <Stack>
        {errors.root && (
          <Alert color="red" icon={<CircleAlert size={18} />}>
            {errors.root.message}
          </Alert>
        )}
        <TextInput label="Nome" placeholder="Ex.: Site da loja" data-autofocus {...register('name')} error={errors.name?.message} />
        <Button type="submit" loading={create.isPending}>
          Criar chave
        </Button>
      </Stack>
    </form>
  );
}

function RevokeKey({ apiKey, onClose }: { apiKey: ApiKey; onClose: () => void }) {
  const queryClient = useQueryClient();
  const revoke = useMutation({
    mutationFn: () => unwrap(api.DELETE('/api/api-keys/{id}', { params: { path: { id: apiKey.id } } })),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: keys });
      notifications.show({ color: 'green', message: `Chave ${apiKey.name} revogada.` });
      onClose();
    },
    onError: (error) => notifications.show({ color: 'red', message: errorMessage(error) }),
  });
  return (
    <Stack>
      <Text size="sm">
        O sistema que usa a chave <b>{apiKey.name}</b> para de conseguir mandar pedidos na hora. Não dá para desfazer.
      </Text>
      <Group justify="flex-end">
        <Button variant="default" onClick={onClose}>
          Voltar
        </Button>
        <Button color="red" loading={revoke.isPending} onClick={() => revoke.mutate()}>
          Revogar
        </Button>
      </Group>
    </Stack>
  );
}
