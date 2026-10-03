import adapter from '@sveltejs/adapter-node';
import tailwindcss from '@tailwindcss/vite';
import { sveltekit } from '@sveltejs/kit/vite';
import { defineConfig } from 'vite';

export default defineConfig({
	plugins: [tailwindcss(), sveltekit({ adapter: adapter() })],
	server: {
		proxy: {
			'/api': {
				target: 'http://f1.kinnovatio.local' /* Replace with your backend server URL */,
				changeOrigin: true,
				rewrite: (path) => path.replace(/^\/api\/v1/, '/api/test')
			}
		}
	}
});
