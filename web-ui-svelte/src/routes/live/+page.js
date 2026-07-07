import { getRaceControlMessages, getDriverList, getWeatherData, getTimingData } from '$lib/live-api.js';

// This line turns this route (and its children) into a pure SPA
export const ssr = false;

/** @type {import('./$types').PageLoad} */
export async function load({ fetch }) {
    const raceControlMessages = await getRaceControlMessages(fetch); 
    const driverList = await getDriverList(fetch); 
    const weatherData = await getWeatherData(fetch);
    const timingData = await getTimingData(fetch);
    
    return { raceControlMessages, driverList, weatherData, timingData };
}
