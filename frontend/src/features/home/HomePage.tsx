import { Anchor, Card, List, SimpleGrid, Stack, Text, ThemeIcon, Title } from '@mantine/core';
import { Armchair, Bike, BookOpen, ChartColumn, ChefHat, ClipboardList, CreditCard, type LucideIcon, Plug, Plus, Store, Users } from 'lucide-react';
import { Link } from 'react-router';
import { ORDER_TAKERS, ORDER_VIEWERS } from '../../shared/lib/roles';
import { useSession } from '../auth/auth-context';

const UPCOMING: { label: string; icon: LucideIcon }[] = [
  { label: 'Tela da cozinha e impressão automática', icon: ChefHat },
  { label: 'Mesas e comandas', icon: Armchair },
  { label: 'Pedidos do iFood e da 99Food', icon: Plug },
  { label: 'Caixa e relatórios de vendas', icon: ChartColumn },
];

function ItemIcon({ icon: Icon, color }: { icon: LucideIcon; color: string }) {
  return (
    <ThemeIcon size={24} radius="xl" color={color} variant="light">
      <Icon size={14} />
    </ThemeIcon>
  );
}

export function HomePage() {
  const { user, store } = useSession();
  const firstName = user.name.split(' ')[0];

  return (
    <Stack gap="lg" maw={960}>
      <Stack gap={4}>
        <Title order={2}>Olá, {firstName}!</Title>
        <Text c="dimmed">Você está em {store.name}.</Text>
      </Stack>

      <SimpleGrid cols={{ base: 1, sm: 2 }}>
        {user.role === 'OWNER' && (
          <Card withBorder radius="lg" padding="lg">
            <Stack gap="sm">
              <Title order={4}>Primeiros passos</Title>
              <List spacing="sm" center>
                <List.Item icon={<ItemIcon icon={Store} color="orange" />}>
                  <Anchor component={Link} to="/configuracoes/loja">
                    Revise os dados da loja
                  </Anchor>{' '}
                  (fuso, virada do dia, taxa de serviço)
                </List.Item>
                <List.Item icon={<ItemIcon icon={Users} color="orange" />}>
                  <Anchor component={Link} to="/configuracoes/equipe">
                    Cadastre a equipe
                  </Anchor>{' '}
                  com o papel de cada pessoa
                </List.Item>
                <List.Item icon={<ItemIcon icon={BookOpen} color="orange" />}>
                  <Anchor component={Link} to="/cardapio">
                    Monte o cardápio
                  </Anchor>{' '}
                  (setores, categorias, adicionais e produtos)
                </List.Item>
                <List.Item icon={<ItemIcon icon={CreditCard} color="orange" />}>
                  <Anchor component={Link} to="/configuracoes/pagamentos">
                    Confira as formas de pagamento
                  </Anchor>{' '}
                  que a loja aceita
                </List.Item>
                <List.Item icon={<ItemIcon icon={Bike} color="orange" />}>
                  <Anchor component={Link} to="/configuracoes/taxas">
                    Cadastre as taxas de entrega
                  </Anchor>{' '}
                  por bairro, se fizer delivery
                </List.Item>
              </List>
            </Stack>
          </Card>
        )}

        {ORDER_VIEWERS.includes(user.role) && (
          <Card withBorder radius="lg" padding="lg">
            <Stack gap="sm">
              <Title order={4}>Pedidos</Title>
              <List spacing="sm" center>
                <List.Item icon={<ItemIcon icon={ClipboardList} color="orange" />}>
                  <Anchor component={Link} to="/pedidos">
                    Quadro de pedidos
                  </Anchor>{' '}
                  (atualiza sozinho em todas as telas)
                </List.Item>
                {ORDER_TAKERS.includes(user.role) && (
                  <List.Item icon={<ItemIcon icon={Plus} color="orange" />}>
                    <Anchor component={Link} to="/pedidos/novo">
                      Lançar pedido
                    </Anchor>{' '}
                    de balcão, telefone ou delivery
                  </List.Item>
                )}
              </List>
            </Stack>
          </Card>
        )}

        <Card withBorder radius="lg" padding="lg">
          <Stack gap="sm">
            <Title order={4}>Chegando nas próximas etapas</Title>
            <List spacing="sm" size="sm" center>
              {UPCOMING.map(({ label, icon }) => (
                <List.Item key={label} icon={<ItemIcon icon={icon} color="gray" />}>
                  {label}
                </List.Item>
              ))}
            </List>
          </Stack>
        </Card>
      </SimpleGrid>
    </Stack>
  );
}
