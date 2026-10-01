import { Alert, Anchor, Button, FileInput, Group, Modal, Stack, Text } from '@mantine/core';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { CircleAlert, FileSpreadsheet } from 'lucide-react';
import { useState } from 'react';
import { api } from '../../shared/api/client';
import { errorMessage, unwrap } from '../../shared/api/errors';
import type { CatalogImport } from '../../shared/api/types';
import { catalogKeys } from './api';
import { ImportResult } from './ImportResult';

/** Modelo com as colunas que o importador entende, separado por ponto e vírgula como o Excel salva no Brasil. */
const TEMPLATE = [
  'categoria;produto;preco;descricao;codigo;disponivel;adicionais',
  'Lanches;X-Burger;32,90;Pão, carne e queijo;100;sim;',
  'Lanches;X-Salada;29,90;;101;sim;',
  'Bebidas;Refrigerante lata;7,00;350 ml;900;sim;',
].join('\r\n');

/**
 * Importar produtos de uma planilha (CSV). Primeiro mostra o que vai acontecer; só grava quando a pessoa confirma.
 * Lê em UTF-8 e, se não for, no formato antigo do Excel (Windows-1252), para os acentos não virarem lixo.
 */
export function SpreadsheetImportModal({ opened, onClose }: { opened: boolean; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [content, setContent] = useState<string | null>(null);
  const [fileError, setFileError] = useState<string | null>(null);
  const [result, setResult] = useState<{ data: CatalogImport; preview: boolean } | null>(null);
  const run = useMutation({
    mutationFn: (dryRun: boolean) =>
      unwrap(api.POST('/api/catalog/imports/spreadsheet', { body: { content: content ?? '', dryRun } })),
    onSuccess: async (data, dryRun) => {
      setResult({ data, preview: dryRun });
      if (data.applied) {
        await queryClient.invalidateQueries({ queryKey: catalogKeys.all });
      }
    },
  });

  async function read(file: File | null) {
    setResult(null);
    setFileError(null);
    setContent(null);
    if (!file) {
      return;
    }
    if (file.size > 2_000_000) {
      setFileError('A planilha pode ter até 2 MB.');
      return;
    }
    const bytes = await file.arrayBuffer();
    try {
      setContent(new TextDecoder('utf-8', { fatal: true }).decode(bytes));
    } catch {
      setContent(new TextDecoder('windows-1252').decode(bytes));
    }
  }

  function close() {
    setContent(null);
    setResult(null);
    setFileError(null);
    onClose();
  }

  const canApply = result?.preview === true && result.data.errors.length === 0;

  return (
    <Modal opened={opened} onClose={close} title="Importar cardápio de planilha" size="lg">
      <Stack>
        <Text size="sm" c="dimmed">
          Uma linha por produto. Colunas: categoria, produto e preço; e, se quiser, descrição, código PDV, disponível
          (sim/não) e adicionais (nomes de grupos já cadastrados, separados por |). Produto com o mesmo código, ou o mesmo
          nome na mesma categoria, é atualizado.
        </Text>
        <Anchor
          href={`data:text/csv;charset=utf-8,${encodeURIComponent('﻿' + TEMPLATE)}`}
          download="modelo-cardapio-pedeai.csv"
          size="sm"
        >
          Baixar planilha modelo
        </Anchor>
        <FileInput
          label="Planilha (.csv)"
          placeholder="Escolha o arquivo"
          accept=".csv,text/csv,text/plain"
          leftSection={<FileSpreadsheet size={16} />}
          onChange={(file) => void read(file)}
          error={fileError}
          clearable
        />
        {run.isError && (
          <Alert color="red" icon={<CircleAlert size={18} />}>
            {errorMessage(run.error)}
          </Alert>
        )}
        {result && <ImportResult result={result.data} preview={result.preview} />}
        <Group justify="flex-end">
          {result && !result.preview && result.data.applied ? (
            <Button onClick={close}>Fechar</Button>
          ) : (
            <>
              <Button variant="default" disabled={!content} loading={run.isPending && run.variables} onClick={() => run.mutate(true)}>
                Pré-visualizar
              </Button>
              <Button disabled={!canApply} loading={run.isPending && !run.variables} onClick={() => run.mutate(false)}>
                Importar
              </Button>
            </>
          )}
        </Group>
      </Stack>
    </Modal>
  );
}
