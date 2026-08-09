/**
 * Helper function to handle response status and parsing
 * @param {Response} response
 */
async function handleResponse(response) {
	if (!response.ok) {
		// Attempt to parse server-provided error message, fallback to status text
		//const errorBody = await response.json().catch(() => ({}));
		//const errorMessage = errorBody.message || `HTTP ${response.status}: ${response.statusText}`;
		const errorMessage = `HTTP ${response.status}: ${response.statusText}`;
		console.error(errorMessage);
		return {
			error: errorMessage
		};
	}
	return response.json();
}

export async function getSessionStatus(customFetch = fetch) {
	const res = await customFetch(`/../api/v1/live`);
	return handleResponse(res);
}

export async function getSessionInfo(customFetch = fetch) {
	const res = await customFetch(`/../api/v1/live/session-info`);
	return handleResponse(res);
}

export async function getRaceControlMessages(customFetch = fetch) {
	const res = await customFetch(`/../api/v1/live/race-control-messages`);
	return handleResponse(res);
}

export async function getDriverList(customFetch = fetch) {
	const res = await customFetch(`/../api/v1/live/driver-list`);
	return handleResponse(res);
}

export async function getWeatherData(customFetch = fetch) {
	const res = await customFetch(`/../api/v1/live/weather-data`);
	return handleResponse(res);
}

export async function getTimingData(customFetch = fetch) {
	const res = await customFetch(`/../api/v1/live/timing-data`);
	return handleResponse(res);
}
