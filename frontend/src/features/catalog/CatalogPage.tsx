import { Stack, Tabs, Text, Title } from '@mantine/core';
import { BookOpen, ChefHat, FolderOpen, ListPlus, type LucideIcon } from 'lucide-react';
import { Navigate, useNavigate, useParams } from 'react-router';
import { CATALOG_MANAGERS } from '../../shared/lib/roles';
import { useSession } from '../auth/auth-context';
import { CategoriesTab } from './CategoriesTab';
import { OptionGroupsTab } from './OptionGroupsTab';
import { ProductsTab } from './ProductsTab';
import { SectorsTab } from './SectorsTab';

type TabId = 'produtos' | 'categorias' | 'adicionais' | 'setores';

const TABS: { id: TabId; label: string; icon: LucideIcon; managersOnly: boolean }[] = [
  { id: 'produtos', label: 'Produtos', icon: BookOpen, managersOnly: false },
  { id: 'categorias', label: 'Categorias', icon: FolderOpen, managersOnly: true },
  { id: 'adicionais', label: 'Adicionais', icon: ListPlus, managersOnly: false },
  { id: 'setores', label: 'Setores', icon: ChefHat, managersOnly: true },
];

/** Cardápio. Dono e gerente cadastram; caixa e cozinha só pausam e liberam itens durante o serviço. */
export function CatalogPage() {
  const { tab } = useParams();
  const navigate = useNavigate();
  const { user } = useSession();
  const manage = CATALOG_MANAGERS.includes(user.role);
  const tabs = TABS.filter((current) => manage || !current.managersOnly);

  if (!tabs.some((current) => current.id === tab)) {
    return <Navigate to="/cardapio/produtos" replace />;
  }

  return (
    <Stack maw={1100} gap="lg">
      <Stack gap={4}>
        <Title order={2}>Cardápio</Title>
        <Text c="dimmed">
          {manage
            ? 'Produtos, adicionais e o setor que prepara cada item.'
            : 'Pause o que acabou e libere quando voltar.'}
        </Text>
      </Stack>
      <Tabs value={tab} onChange={(value) => value && navigate(`/cardapio/${value}`)} keepMounted={false}>
        <Tabs.List>
          {tabs.map(({ id, label, icon: Icon }) => (
            <Tabs.Tab key={id} value={id} leftSection={<Icon size={16} />}>
              {label}
            </Tabs.Tab>
          ))}
        </Tabs.List>
        <Tabs.Panel value="produtos" pt="md">
          <ProductsTab manage={manage} />
        </Tabs.Panel>
        <Tabs.Panel value="adicionais" pt="md">
          <OptionGroupsTab manage={manage} />
        </Tabs.Panel>
        {manage && (
          <>
            <Tabs.Panel value="categorias" pt="md">
              <CategoriesTab />
            </Tabs.Panel>
            <Tabs.Panel value="setores" pt="md">
              <SectorsTab />
            </Tabs.Panel>
          </>
        )}
      </Tabs>
    </Stack>
  );
}
