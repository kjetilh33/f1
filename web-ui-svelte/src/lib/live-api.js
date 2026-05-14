

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
        }
    }
    return response.json();
}

export async function getRaceControlMessages(customFech = fetch) {
    const res = await customFech(`/../api/v1/live/race-control-messages`);
    return handleResponse(res);
}

export async function getDriverList(customFech = fetch) {
    const res = await customFech(`/../api/v1/live/driver-list`);
    return handleResponse(res);
}

export async function getWeatherData(customFech = fetch) {
    const res = await customFech(`/../api/v1/live/weather-data`);
    return handleResponse(res);
}

export async function getTimingData(customFech = fetch) {
    const res = await customFech(`/../api/v1/live/weather-data`);
    return handleResponse(res);
}