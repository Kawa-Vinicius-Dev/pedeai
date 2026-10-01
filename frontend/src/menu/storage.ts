import { useEffect, useState } from 'react';
import type { CartLine } from '../features/orders/cart';

/** Dados do cliente para o próximo pedido, guardados só neste aparelho. */
export interface SavedCustomer {
  name: string;
  phone: string;
  street: string;
  number: string;
  complement: string;
  neighborhood: string;
  reference: string;
}

export const EMPTY_CUSTOMER: SavedCustomer = {
  name: '',
  phone: '',
  street: '',
  number: '',
  complement: '',
  neighborhood: '',
  reference: '',
};

/** Estado lembrado no navegador. Sem armazenamento (aba anônima, bloqueado), vale só enquanto a página está aberta. */
export function useStored<T>(key: string, initial: T): [T, (value: T) => void] {
  const [value, setValue] = useState<T>(() => read(key, initial));
  useEffect(() => {
    try {
      localStorage.setItem(key, JSON.stringify(value));
    } catch {
      // Sem armazenamento: fica só na memória.
    }
  }, [key, value]);
  return [value, setValue];
}

export function cartKey(slug: string): string {
  return `pedeai.menu.cart.${slug}`;
}

export const CUSTOMER_KEY = 'pedeai.menu.customer';

export function clearCart(slug: string): void {
  try {
    localStorage.removeItem(cartKey(slug));
  } catch {
    // Sem armazenamento: nada a limpar.
  }
}

function read<T>(key: string, initial: T): T {
  try {
    const raw = localStorage.getItem(key);
    return raw === null ? initial : (JSON.parse(raw) as T);
  } catch {
    return initial;
  }
}

export type Cart = CartLine[];
