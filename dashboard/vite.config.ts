import { dirname, resolve} from 'node:path'
import { defineConfig } from 'vite';
import solidPlugin from 'vite-plugin-solid';
import devtools from 'solid-devtools/vite';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig({
    plugins: [
        devtools(),
        solidPlugin(),
        tailwindcss()
    ],
    server: {
        port: 3000,
    },
    build: {
        target: 'esnext',
        rollupOptions: {
            input: {
                dashboard: resolve(__dirname, 'index.html'),
                admindashboard: resolve(__dirname, 'admin/index.html'),
            },
        },
    },
    base: '/dashboard/'
});
