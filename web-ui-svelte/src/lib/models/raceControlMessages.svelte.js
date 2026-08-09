import { SvelteDate } from 'svelte/reactivity';

export class RaceControlMessages {
	/**
	 * @type {RaceMessageRecord[]}
	 */
	#raceControlMessages = $state([]);

	/**
	 * @type {((message: RaceMessageRecord) => void)[]}
	 */
	#listeners = [];

	get raceControlMessages() {
		return this.#raceControlMessages;
	}

	/**
	 * Clear state cleanly on reset
	 */
	clear() {
		this.#raceControlMessages = [];
	}

	/**
	 * Subscribe to newly received live streaming race control messages
	 * @param {(message: RaceMessageRecord) => void} listener
	 * @returns {() => void} Unsubscribe cleanup function
	 */
	onMessage(listener) {
		this.#listeners.push(listener);
		return () => {
			const index = this.#listeners.indexOf(listener);
			if (index !== -1) {
				this.#listeners.splice(index, 1);
			}
		};
	}

	/**
	 *
	 * @param {Object<string, any>} initialData
	 */
	initializeData(initialData) {
		if (!initialData) {
			console.error('No initial data for the race control messages.');
			return;
		}

		if (Array.isArray(initialData.messages)) {
			this.#raceControlMessages = initialData.messages.map((element) =>
				this.#parseInitialRaceMessageRecord(element)
			);
		} else {
			console.error(
				'Initial data for race control messages is not in the expected format: ',
				initialData
			);
		}
	}

	/**
	 * Update the collection with a new race message
	 *
	 * @param {LiveTimingRecord} messageContainer
	 */
	update(messageContainer) {
		if (messageContainer.category === 'RaceControlMessages' && messageContainer.isStreaming) {
			/** @type {RaceMessageRecord[]} */
			const newRecords = [];

			const rawMessages = messageContainer.message.messages || messageContainer.message.Messages;

			if (Array.isArray(rawMessages)) {
				rawMessages.forEach((element) => {
					const record = this.#parseLiveRaceMessageRecord(messageContainer, element);
					this.#raceControlMessages.push(record);
					newRecords.push(record);
				});
			} else if (rawMessages && typeof rawMessages === 'object') {
				Object.values(rawMessages).forEach((element) => {
					const record = this.#parseLiveRaceMessageRecord(messageContainer, element);
					this.#raceControlMessages.push(record);
					newRecords.push(record);
				});
			}

			// Notify subscribers of newly streamed live messages
			newRecords.forEach((record) => {
				this.#listeners.forEach((listener) => listener(record));
			});
		} else {
			console.error(
				'Unexpected message category or streaming state for race control messages: ',
				messageContainer.category,
				messageContainer.isStreaming
			);
		}
	}

	/**
	 * @param {LiveTimingRecord} messageContainer
	 * @param {Object<string, any>} element
	 * @returns {RaceMessageRecord}
	 */
	#parseLiveRaceMessageRecord(messageContainer, element) {
		return {
			timestamp: element.utc
				? new SvelteDate(element.utc)
				: new SvelteDate(messageContainer.timestamp),
			category: element.category,
			message: element.message,
			lap: element.lap,
			flag: element.flag,
			scope: element.scope,
			sector: element.sector,
			mode: element.mode,
			status: element.status
		};
	}

	/**
	 * @param {Object<string, any>} element
	 * @returns {RaceMessageRecord}
	 */
	#parseInitialRaceMessageRecord(element) {
		return {
			timestamp: element.utc ? new SvelteDate(element.utc) : new SvelteDate(),
			category: element.category,
			message: element.message,
			lap: element.lap,
			flag: element.flag,
			scope: element.scope,
			sector: element.sector,
			mode: element.mode,
			status: element.status
		};
	}
}
