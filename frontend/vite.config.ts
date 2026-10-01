import react from '@vitejs/plugin-react';
import { fileURLToPath } from 'node:url';
import type { Plugin } from 'vite';
import { defineConfig } from 'vitest/config';

/** Em desenvolvimento, /loja/... abre o cardápio digital (menu.html), como o repasse da Vercel faz em produção. */
function menuPage(): Plugin {
  return {
    name: 'pedeai-menu-page',
    configureServer(server) {
      server.middlewares.use((request, _response, next) => {
        if (request.url?.startsWith('/loja/')) {
          request.url = '/menu.html';
        }
        next();
      });
    },
  };
}

export default defineConfig({
  plugins: [react(), menuPage()],
  server: {
    port: 5173,
    // Em desenvolvimento a API fica na mesma origem: o cookie de sessão funciona sem CORS.
    proxy: { '/api': 'http://localhost:8080' },
  },
  build: {
    rollupOptions: {
      // Duas páginas: o painel da loja (index.html) e o cardápio digital do cliente (menu.html).
      input: {
        main: fileURLToPath(new URL('./index.html', import.meta.url)),
        menu: fileURLToPath(new URL('./menu.html', import.meta.url)),
      },
      output: {
        // Bibliotecas num arquivo próprio: mudam pouco entre versões do app e ficam no cache do navegador.
        manualChunks(id) {
          if (id.includes('node_modules/@mantine') || id.includes('node_modules/@floating-ui')) {
            return 'mantine';
          }
          if (id.includes('node_modules/react') || id.includes('node_modules/scheduler')) {
            return 'react';
          }
          return undefined;
        },
      },
    },
  },
  test: {
    environment: 'jsdom',
    // Os testes percorrem telas inteiras (o PDV tem 15 passos) com o app completo renderizado: em máquina ou CI
    // carregados, passam dos 5 s padrão sem estarem errados.
    testTimeout: 15_000,
    setupFiles: ['./src/test/setup.ts'],
    css: false,
  },
});
