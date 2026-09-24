import type { FieldValues, Path, UseFormSetError } from 'react-hook-form';
import { ApiRequestError, errorMessage } from '../api/errors';

/**
 * Leva o erro da API para o formulário: erros de campo (400) aparecem embaixo de cada campo e a
 * mensagem geral aparece no topo (erro "root").
 */
export function applyApiError<T extends FieldValues>(error: unknown, setError: UseFormSetError<T>): void {
  if (error instanceof ApiRequestError) {
    Object.entries(error.fields).forEach(([field, message]) => {
      setError(field as Path<T>, { type: 'server', message });
    });
  }
  setError('root', { type: 'server', message: errorMessage(error) });
}
