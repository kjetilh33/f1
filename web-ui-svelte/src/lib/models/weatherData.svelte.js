import { SvelteDate } from 'svelte/reactivity';

export class WeatherData {
	/**
	 * @type {Partial<WeatherRecord>}
	 */
	#weatherData = $state({});

	get weatherData() {
		return this.#weatherData;
	}

	/**
	 * Clear state cleanly on reset
	 */
	clear() {
		this.#weatherData = {};
	}

	/**
	 *
	 * @param {Object<string, any>} initialData
	 */
	initializeData(initialData) {
		if (!initialData) {
			console.error('No initial data for the weather data.');
			return;
		}

		this.#weatherData.timestamp = initialData.updatedTimestamp
			? new SvelteDate(initialData.updatedTimestamp)
			: new SvelteDate();
		this.#weatherData.airTemp = initialData.airTemp ? initialData.airTemp : 'n/a';
		this.#weatherData.humidity = initialData.humidity ? initialData.humidity : 'n/a';
		this.#weatherData.pressure = initialData.pressure ? initialData.pressure : 'n/a';
		this.#weatherData.rainfall = initialData.rainfall ? initialData.rainfall : 'n/a';
		this.#weatherData.trackTemp = initialData.trackTemp ? initialData.trackTemp : 'n/a';
		this.#weatherData.windSpeed = initialData.windSpeed ? initialData.windSpeed : 'n/a';
		this.#weatherData.windDirection = initialData.windDirection ? initialData.windDirection : 'n/a';
	}

	/**
	 * Update the collection with a new race message
	 *
	 * @param {LiveTimingRecord} messageContainer
	 */
	update(messageContainer) {
		if (messageContainer.category === 'WeatherData' && messageContainer.isStreaming) {
			this.#weatherData.timestamp = new SvelteDate(messageContainer.timestamp);
			this.#weatherData.airTemp = messageContainer.message.airTemp;
			this.#weatherData.humidity = messageContainer.message.humidity;
			this.#weatherData.pressure = messageContainer.message.pressure;
			this.#weatherData.rainfall = messageContainer.message.rainfall;
			this.#weatherData.trackTemp = messageContainer.message.trackTemp;
			this.#weatherData.windSpeed = messageContainer.message.windSpeed;
			this.#weatherData.windDirection = messageContainer.message.windDirection;
		} else {
			console.error(
				'Unexpected message category or streaming state for weather data: ',
				messageContainer.category,
				messageContainer.isStreaming
			);
		}
	}
}
