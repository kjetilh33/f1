<script>
	import { Tabs, TabItem } from 'flowbite-svelte';
	import { onMount } from 'svelte';
	import { f1LiveData } from '$lib/f1LiveData.svelte.js';
	import SseStatus from './SseStatus.svelte';
	import LivetimingMessages from './LivetimingMessages.svelte';
	import RaceMessageUpdates from './RaceMessageUpdates.svelte';
	import RaceMessages from './RaceMessages.svelte';
	import TrackStatusCurrent from './TrackStatusCurrent.svelte';
	import TimingTower from '$lib/components/timing/TimingTower.svelte';
	import WeatherWidget from '$lib/components/common/WeatherWidget.svelte';

	onMount(() => {
		// The data api uses EventSource which is a browser API and runs only on the client
		f1LiveData.initialize();

		return () => {
			f1LiveData.cleanup();
		};
	});
</script>

<div
	class="mx-auto flex justify-end border-b border-gray-800 bg-gray-900 px-4 py-2 sm:px-4 lg:px-4"
>
	<SseStatus />
</div>
<RaceMessageUpdates />

<div class="mx-auto max-w-7xl px-4 py-4 sm:px-4 lg:px-4">
	<Tabs tabStyle="underline">
		<TabItem open title="Race Dashboard">
			<div class="mt-4 grid grid-cols-1 gap-6 lg:grid-cols-3">
				<div class="lg:col-span-2">
					<h3 class="mb-3 text-lg font-bold text-gray-200">Live Leaderboard</h3>
					<TimingTower />
				</div>
				<div class="space-y-4">
					<div>
						<h3 class="mb-3 text-lg font-bold text-gray-200">Track Status</h3>
						<TrackStatusCurrent />
					</div>
					<div>
						<WeatherWidget />
					</div>
				</div>
			</div>
		</TabItem>

		<TabItem title="Timing Tower">
			<div class="mt-4">
				<TimingTower />
			</div>
		</TabItem>

		<TabItem title="Race Control">
			<div class="mt-4 grid grid-cols-1 gap-4 md:grid-cols-4">
				<div class="md:col-span-3">
					<RaceMessages />
				</div>
				<div class="space-y-4">
					<div class="rounded-lg border border-gray-700 bg-gray-800 p-3">
						<h4 class="mb-2 text-xs font-bold text-gray-400 uppercase">Track Status</h4>
						<TrackStatusCurrent />
					</div>
					<WeatherWidget />
				</div>
			</div>
		</TabItem>

		<TabItem title="Raw SSE Stream">
			<div class="mt-4">
				<LivetimingMessages />
			</div>
		</TabItem>
	</Tabs>
</div>
