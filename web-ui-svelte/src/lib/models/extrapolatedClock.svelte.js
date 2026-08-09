import { SvelteDate } from 'svelte/reactivity';

export class ExtrapolatedClock {
	/** @type {ClockRecord} */
	#clock = $state({
		utc: new SvelteDate(),
		remaining: '00:00:00',
		extrapolating: false
	});

	get clock() {
		return this.#clock;
	}

	clear() {
		this.#clock = {
			utc: new SvelteDate(),
			remaining: '00:00:00',
			extrapolating: false
		};
	}

	/**
	 * @param {Record<string, any>} initialData
	 */
	initializeData(initialData) {
		if (!initialData) return;
		this.#clock = {
			utc: initialData.Utc ? new SvelteDate(initialData.Utc) : new SvelteDate(),
			remaining: initialData.Remaining || '00:00:00',
			extrapolating: Boolean(initialData.Extrapolating)
		};
	}

	/**
	 * @param {LiveTimingRecord} messageContainer
	 */
	update(messageContainer) {
		if (messageContainer.category === 'ExtrapolatedClock' && messageContainer.message) {
			const msg = messageContainer.message;
			this.#clock = {
				utc: msg.Utc ? new SvelteDate(msg.Utc) : new SvelteDate(messageContainer.timestamp),
				remaining: msg.Remaining || '00:00:00',
				extrapolating: Boolean(msg.Extrapolating)
			};
		}
	}
}
