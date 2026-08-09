<script>
	import TyreBadge from '../common/TyreBadge.svelte';

	/**
	 * @type {{ driver: LeaderboardDriver }}
	 */
	let { driver } = $props();
</script>

<div
	class="group mb-1 flex items-center justify-between rounded border-l-4 border-gray-700 bg-gray-800/90 px-3 py-2 font-mono text-sm text-white shadow-sm transition-all hover:bg-gray-800"
	style="border-left-color: #{driver.teamColour || '6B7280'};"
>
	<!-- Left: Position, TLA, Driver Name & Team -->
	<div class="flex w-1/3 min-w-0 items-center gap-3">
		<span class="w-6 text-center font-bold text-gray-400 group-hover:text-white">
			{driver.position}
		</span>
		<span class="min-w-12 text-base font-bold tracking-wider text-gray-100">
			{driver.tla}
		</span>
		<span class="hidden truncate font-sans text-xs text-gray-400 md:inline">
			{driver.name}
		</span>
	</div>

	<!-- Center: Pit & Tyre status -->
	<div class="flex items-center gap-2">
		{#if driver.inPit}
			<span
				class="animate-pulse rounded bg-amber-500 px-1.5 py-0.5 text-[10px] font-bold text-black uppercase"
				>PIT</span
			>
		{:else if driver.pitCount > 0}
			<span class="rounded border border-gray-700 bg-gray-900 px-1.5 py-0.5 text-xs text-gray-400"
				>{driver.pitCount} STOP</span
			>
		{/if}
		<TyreBadge compound={driver.tyreCompound} isNew={driver.isNewTyre} age={driver.tyreAge} />
	</div>

	<!-- Right: Gaps & Last Lap Time -->
	<div class="flex w-1/3 items-center justify-end gap-4 text-right">
		<span class="w-16 truncate text-xs text-gray-400">
			{driver.gapToLeader}
		</span>
		<span class="hidden w-16 truncate text-xs text-gray-400 sm:inline">
			{driver.intervalToAhead}
		</span>
		<span class="w-20 text-right font-bold text-yellow-400">
			{driver.lastLapTime}
		</span>
	</div>
</div>
