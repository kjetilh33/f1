import { getDriverList, getTimingData } from '$lib/live-api.js';

// This line turns this route (and its children) into a pure SPA
export const ssr = false;

/** @type {import('./$types').PageLoad} */
export async function load({ fetch }) {
	const driverList = await getDriverList(fetch);
	const timingData = await getTimingData(fetch);

	return { driverList, timingData };
}
