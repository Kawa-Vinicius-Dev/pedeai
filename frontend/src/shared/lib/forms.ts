import type { FieldValues, Path, UseFormSetError } from 'react-hook-form';
import { ApiRequestError, errorMessage } from '../api/errors';

/**
 * Leva o erro da API para o formulário: erros de campo (400) aparecem embaixo de cada campo e a
 * mensagem geral aparece no topo (erro "root"). `fieldNames` traduz o nome do campo na API para o
 * do formulário quando eles diferem (ex.: priceCents na API, price em reais no formulário).
 */
export function applyApiError<T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
  fieldNames: Record<string, string> = {},
): void {
  if (error instanceof ApiRequestError) {
    Object.entries(error.fields).forEach(([field, message]) => {
      setError(toFormPath(field, fieldNames) as Path<T>, { type: 'server', message });
    });
  }
  setError('root', { type: 'server', message: errorMessage(error) });
}

/** "options[2].priceCents" (Spring) → "options.2.price" (react-hook-form). */
function toFormPath(field: string, fieldNames: Record<string, string>): string {
  return field
    .replace(/\[(\d+)\]/g, '.$1')
    .split('.')
    .map((part) => fieldNames[part] ?? part)
    .join('.');
}
