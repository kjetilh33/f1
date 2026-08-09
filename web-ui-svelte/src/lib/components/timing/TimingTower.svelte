<script>
	import { f1LiveData } from '$lib/f1LiveData.svelte';
	import DriverRow from './DriverRow.svelte';

	let leaderboard = $derived(f1LiveData.leaderboard);
</script>

<div class="w-full rounded-lg border border-gray-800 bg-gray-900 p-3 shadow-lg">
	<div
		class="mb-2 flex items-center justify-between border-b border-gray-800 px-3 py-2 font-sans text-xs font-semibold tracking-wider text-gray-400 uppercase"
	>
		<div class="w-1/3">Pos / Driver</div>
		<div class="text-center">Tyre & Pit</div>
		<div class="w-1/3 text-right">Gap / Interval / Last Lap</div>
	</div>

	{#if leaderboard.length === 0}
		<div class="py-8 text-center font-sans text-sm text-gray-500">
			Waiting for timing feed data...
		</div>
	{:else}
		<div class="max-h-[700px] space-y-1 overflow-y-auto pr-1">
			{#each leaderboard as driver (driver.racingNumber)}
				<DriverRow {driver} />
			{/each}
		</div>
	{/if}
</div>
