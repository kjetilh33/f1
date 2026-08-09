import { SvelteDate } from 'svelte/reactivity';

export class TrackStatus {
	/**
	 * @type {TrackStatusRecord}
	 */
	#trackStatus = $state({
		timestamp: new SvelteDate(),
		status: '-1',
		message: 'unknown'
	});

	get trackStatus() {
		return this.#trackStatus;
	}

	/**
	 * Clear state cleanly on reset
	 */
	clear() {
		this.#trackStatus = {
			timestamp: new SvelteDate(),
			status: '-1',
			message: 'unknown'
		};
	}

	/**
	 *
	 * @param {Object<string, any>} initialData
	 */
	initializeData(initialData) {
		if (!initialData) {
			console.error('No initial data for the track status.');
			return;
		}

		this.#trackStatus.timestamp = initialData.updatedTimestamp
			? new SvelteDate(initialData.updatedTimestamp)
			: new SvelteDate();
		this.#trackStatus.status = initialData.status ? initialData.status : '-1';
		this.#trackStatus.message = initialData.message ? initialData.message : 'unknown';
	}

	/**
	 * Update the collection with a new race message
	 *
	 * @param {LiveTimingRecord} messageContainer
	 */
	update(messageContainer) {
		if (messageContainer.category === 'TrackStatus' && messageContainer.isStreaming) {
			this.#trackStatus.timestamp = new SvelteDate(messageContainer.timestamp);
			this.#trackStatus.status = messageContainer.message.status;
			this.#trackStatus.message = messageContainer.message.message;
		} else {
			console.error(
				'Unexpected message category or streaming state for track status: ',
				messageContainer.category,
				messageContainer.isStreaming
			);
		}
	}
}
