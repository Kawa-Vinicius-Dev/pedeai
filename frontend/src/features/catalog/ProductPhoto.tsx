import { Button, FileButton, Group, Image, Stack, Text } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { ImagePlus, Trash2 } from 'lucide-react';
import { useState } from 'react';
import { api } from '../../shared/api/client';
import { errorMessage, unwrap } from '../../shared/api/errors';
import type { Product } from '../../shared/api/types';
import { invalidateCatalog } from './api';

const MAX_SIDE = 1200;

/** Reduz no navegador (lado maior 1200 px, JPEG): a foto do celular cabe nos 2 MB e carrega rápido no cardápio. */
async function shrink(file: File): Promise<Blob> {
  const bitmap = await createImageBitmap(file);
  const scale = Math.min(1, MAX_SIDE / Math.max(bitmap.width, bitmap.height));
  const canvas = document.createElement('canvas');
  canvas.width = Math.round(bitmap.width * scale);
  canvas.height = Math.round(bitmap.height * scale);
  canvas.getContext('2d')?.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
  bitmap.close();
  return new Promise((resolve, reject) =>
    canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new Error('Não foi possível ler a foto.'))), 'image/jpeg', 0.85),
  );
}

/** Foto do produto: aparece no cardápio digital e vai para o iFood com o produto. */
export function ProductPhoto({ product }: { product: Product }) {
  const queryClient = useQueryClient();
  const [url, setUrl] = useState(product.imageUrl);
  const done = async (saved: Product) => {
    setUrl(saved.imageUrl);
    await invalidateCatalog(queryClient);
  };
  const upload = useMutation({
    mutationFn: async (file: File) => {
      const form = new FormData();
      form.append('file', await shrink(file), 'foto.jpg');
      return unwrap(
        api.POST('/api/products/{id}/image', { params: { path: { id: product.id } }, body: form as never }),
      );
    },
    onSuccess: done,
    onError: (error) => notifications.show({ color: 'red', message: errorMessage(error) }),
  });
  const remove = useMutation({
    mutationFn: () => unwrap(api.DELETE('/api/products/{id}/image', { params: { path: { id: product.id } } })),
    onSuccess: done,
    onError: (error) => notifications.show({ color: 'red', message: errorMessage(error) }),
  });

  return (
    <Stack gap={6}>
      <Text size="sm" fw={500}>
        Foto
      </Text>
      {url && <Image src={url} alt={`Foto de ${product.name}`} h={160} w={240} radius="md" fit="cover" />}
      <Group gap="xs">
        <FileButton accept="image/jpeg,image/png,image/webp" onChange={(file) => file && upload.mutate(file)}>
          {(props) => (
            <Button {...props} variant="default" leftSection={<ImagePlus size={16} />} loading={upload.isPending}>
              {url ? 'Trocar foto' : 'Escolher foto'}
            </Button>
          )}
        </FileButton>
        {url && (
          <Button
            variant="subtle"
            color="red"
            leftSection={<Trash2 size={16} />}
            loading={remove.isPending}
            onClick={() => remove.mutate()}
          >
            Remover
          </Button>
        )}
      </Group>
    </Stack>
  );
}
