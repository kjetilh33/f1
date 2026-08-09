import { getSessionStatus, getSessionInfo } from '$lib/live-api.js';

/** @type {import('./$types').LayoutLoad} */
export async function load({ fetch }) {
	const sessionStatus = await getSessionStatus(fetch);
	const sessionInfo = await getSessionInfo(fetch);

	return { sessionStatus, sessionInfo };
}
