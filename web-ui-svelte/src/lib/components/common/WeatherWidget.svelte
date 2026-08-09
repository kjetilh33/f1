<script>
	import { f1LiveData } from '$lib/f1LiveData.svelte';
	import { Card } from 'flowbite-svelte';

	let weather = $derived(f1LiveData.weatherData);
</script>

<Card size="sm" class="border-gray-700 bg-gray-800 p-4 text-white">
	<h5
		class="mb-3 flex items-center justify-between text-sm font-semibold tracking-wider text-gray-400 uppercase"
	>
		<span>Track Weather</span>
		{#if String(weather.rainfall) === '1'}
			<span
				class="rounded border border-blue-500/50 bg-blue-600/30 px-2 py-0.5 text-xs font-bold text-blue-400"
				>WET / RAIN</span
			>
		{:else}
			<span
				class="rounded border border-amber-500/50 bg-amber-600/30 px-2 py-0.5 text-xs font-bold text-amber-400"
				>DRY</span
			>
		{/if}
	</h5>

	<div class="grid grid-cols-2 gap-3 font-mono text-xs">
		<div class="rounded border border-gray-700/50 bg-gray-900/60 p-2">
			<span class="block font-sans text-[10px] text-gray-400 uppercase">Air Temp</span>
			<span class="text-lg font-bold text-gray-100">{weather.airTemp || 'n/a'} °C</span>
		</div>
		<div class="rounded border border-gray-700/50 bg-gray-900/60 p-2">
			<span class="block font-sans text-[10px] text-gray-400 uppercase">Track Temp</span>
			<span class="text-lg font-bold text-red-400">{weather.trackTemp || 'n/a'} °C</span>
		</div>
		<div class="rounded border border-gray-700/50 bg-gray-900/60 p-2">
			<span class="block font-sans text-[10px] text-gray-400 uppercase">Humidity</span>
			<span class="text-sm font-bold text-blue-300">{weather.humidity || 'n/a'} %</span>
		</div>
		<div class="rounded border border-gray-700/50 bg-gray-900/60 p-2">
			<span class="block font-sans text-[10px] text-gray-400 uppercase">Wind</span>
			<span class="text-sm font-bold text-gray-300"
				>{weather.windSpeed || '0'} m/s ({weather.windDirection || 0}°)</span
			>
		</div>
	</div>
</Card>
