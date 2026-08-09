<script>
	import { f1LiveData } from '$lib/f1LiveData.svelte';
	import { Toast, ToastContainer } from 'flowbite-svelte';
	import { BellRingOutline } from 'flowbite-svelte-icons';
	import { fly } from 'svelte/transition';
	import { onMount, onDestroy } from 'svelte';

	/**
	 * @typedef {Object} ToastItem
	 * @property {number} id
	 * @property {string} category
	 * @property {string} message
	 * @property {boolean} visible
	 * @property {ReturnType<typeof setTimeout>} [timeoutId]
	 */

	/** @type {ToastItem[]} */
	let toasts = $state([]);
	let nextId = 1;

	onMount(() => {
		// Subscribe to parsed live streaming messages from the model
		const unsubscribe = f1LiveData.onRaceControlMessage((record) => {
			addToast(record);
		});
		return unsubscribe;
	});

	/**
	 * @param {RaceMessageRecord} record
	 */
	function addToast(record) {
		const id = nextId++;
		const timeoutId = setTimeout(() => {
			dismissToast(id);
		}, 5000);

		toasts = [
			...toasts,
			{
				id,
				category: record.category || 'Race Control',
				message: record.message,
				visible: true,
				timeoutId
			}
		];
	}

	/**
	 * @param {number} id
	 */
	function dismissToast(id) {
		const toast = toasts.find((t) => t.id === id);
		if (toast?.timeoutId) {
			clearTimeout(toast.timeoutId);
		}

		toasts = toasts.map((t) => (t.id === id ? { ...t, visible: false } : t));
		setTimeout(() => {
			toasts = toasts.filter((t) => t.id !== id);
		}, 300);
	}

	onDestroy(() => {
		toasts.forEach((t) => {
			if (t.timeoutId) clearTimeout(t.timeoutId);
		});
	});
</script>

<ToastContainer position="top-right">
	{#each toasts as toast (toast.id)}
		<Toast
			align={false}
			dismissable={true}
			transition={fly}
			transitionParams={{ x: 200, duration: 400 }}
			onclose={() => dismissToast(toast.id)}
			bind:toastStatus={toast.visible}
		>
			{#snippet icon()}
				<BellRingOutline class="h-5 w-5 text-red-500" />
			{/snippet}
			<div class="text-sm font-medium">
				<span class="font-bold text-red-400">[{toast.category}]</span>
				{toast.message}
			</div>
		</Toast>
	{/each}
</ToastContainer>
