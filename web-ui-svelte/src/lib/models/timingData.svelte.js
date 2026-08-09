import { SvelteSet } from 'svelte/reactivity';

export class TimingDataModel {
	/** @type {Record<string, any>} */
	#timingData = $state({});

	/** @type {Record<string, any>} */
	#timingAppData = $state({});

	get rawTimingData() {
		return this.#timingData;
	}

	get rawTimingAppData() {
		return this.#timingAppData;
	}

	clear() {
		this.#timingData = {};
		this.#timingAppData = {};
	}

	/**
	 * Initialize REST timing data payload
	 * @param {Record<string, any>} initialTiming
	 * @param {Record<string, any>} [initialAppData]
	 */
	initializeData(initialTiming, initialAppData) {
		if (initialTiming && initialTiming.Lines) {
			this.#timingData = initialTiming.Lines;
		} else if (initialTiming && typeof initialTiming === 'object') {
			this.#timingData = initialTiming;
		}

		if (initialAppData && initialAppData.Lines) {
			this.#timingAppData = initialAppData.Lines;
		} else if (initialAppData && typeof initialAppData === 'object') {
			this.#timingAppData = initialAppData;
		}
	}

	/**
	 * Deep updates from streaming SSE delta ticks for TimingData
	 * @param {LiveTimingRecord} messageContainer
	 */
	updateTimingData(messageContainer) {
		if (messageContainer.category === 'TimingData' && messageContainer.message) {
			const lines = messageContainer.message.Lines || messageContainer.message;
			this.#timingData = this.#mergeDeep({ ...this.#timingData }, lines);
		}
	}

	/**
	 * Deep updates from streaming SSE delta ticks for TimingAppData
	 * @param {LiveTimingRecord} messageContainer
	 */
	updateTimingAppData(messageContainer) {
		if (messageContainer.category === 'TimingAppData' && messageContainer.message) {
			const lines = messageContainer.message.Lines || messageContainer.message;
			this.#timingAppData = this.#mergeDeep({ ...this.#timingAppData }, lines);
		}
	}

	/**
	 * Compute a clean, reactive leaderboard list using driver metadata
	 * @param {Record<string, DriverRecord>} drivers
	 * @returns {LeaderboardDriver[]}
	 */
	getLeaderboard(drivers = {}) {
		const result = [];
		const allDriverKeys = new SvelteSet([
			...Object.keys(drivers),
			...Object.keys(this.#timingData),
			...Object.keys(this.#timingAppData)
		]);

		for (const num of allDriverKeys) {
			if (num === '_kf' || num === 'Withheld') continue;

			const driver = drivers[num] || {};
			const timing = this.#timingData[num] || {};
			const appData = this.#timingAppData[num] || {};

			// Stints array (latest stint is current tyre)
			const stints = Array.isArray(appData.Stints)
				? appData.Stints
				: appData.Stints
					? Object.values(appData.Stints)
					: [];
			const currentStint = stints.length > 0 ? stints[stints.length - 1] : null;

			// Extract sectors array
			const sectorsObj = timing.Sectors || {};
			const sectors = Array.isArray(sectorsObj) ? sectorsObj : Object.values(sectorsObj);

			// Compute numeric position for sorting
			const posStr = timing.Position || timing.Line || appData.Line || driver.line || '99';
			const position = parseInt(posStr, 10) || 99;

			result.push({
				racingNumber: num,
				tla: driver.tla || `#${num}`,
				name: driver.fullName || driver.broadcastName || `Driver ${num}`,
				teamName: driver.teamName || 'Formula 1',
				teamColour: (driver.teamColour || '6B7280').replace('#', ''),
				position: position,
				gapToLeader: timing.GapToLeader || timing.DiffToLeader || '-',
				intervalToAhead: timing.IntervalToAhead?.Value || timing.DiffToAhead || '-',
				lastLapTime: timing.LastLapTime?.Value || timing.LapTime || '-',
				bestLapTime: timing.BestLapTime?.Value || '-',
				sectors: sectors,
				inPit: Boolean(timing.InPit || timing.Pit),
				pitCount: timing.NumberOfPitstops || (stints.length > 1 ? stints.length - 1 : 0),
				tyreCompound: (currentStint?.Compound || 'MEDIUM').toUpperCase(),
				isNewTyre: currentStint?.New === 'true' || currentStint?.New === true,
				tyreAge: parseInt(currentStint?.TotalLaps || 0, 10)
			});
		}

		return result.sort((a, b) => a.position - b.position);
	}

	/**
	 * Recursively merge nested object patches (SSE delta ticks)
	 * @param {Record<string, any>} target
	 * @param {Record<string, any>} source
	 * @returns {Record<string, any>}
	 */
	#mergeDeep(target, source) {
		if (!source || typeof source !== 'object') return target;

		for (const key of Object.keys(source)) {
			if (source[key] && typeof source[key] === 'object' && !Array.isArray(source[key])) {
				target[key] = this.#mergeDeep(target[key] || {}, source[key]);
			} else {
				target[key] = source[key];
			}
		}
		return target;
	}
}
