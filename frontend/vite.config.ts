import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // Em desenvolvimento a API fica na mesma origem: o cookie de sessão funciona sem CORS.
    proxy: { '/api': 'http://localhost:8080' },
  },
  build: {
    rollupOptions: {
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
