export class DriverList {
	/** @type {Record<string, DriverRecord>} */
	#driverList = $state({});

	get driverList() {
		return this.#driverList;
	}

	clear() {
		this.#driverList = {};
	}

	/**
	 * @param {Record<string, any>} initialData
	 */
	initializeData(initialData) {
		if (!initialData || typeof initialData !== 'object') {
			return;
		}

		/** @type {Record<string, DriverRecord>} */
		const parsed = {};
		for (const [num, driver] of Object.entries(initialData)) {
			if (num === '_kf') continue;
			parsed[num] = this.#normalizeDriverRecord(driver);
		}
		this.#driverList = parsed;
	}

	/**
	 * Update driver list from live stream tick
	 * @param {LiveTimingRecord} messageContainer
	 */
	update(messageContainer) {
		if (messageContainer.category === 'DriverList' && messageContainer.message) {
			const lines = messageContainer.message;
			const updated = { ...this.#driverList };
			for (const [num, driver] of Object.entries(lines)) {
				if (num === '_kf') continue;
				updated[num] = {
					...updated[num],
					...this.#normalizeDriverRecord(driver)
				};
			}
			this.#driverList = updated;
		}
	}

	/**
	 * Helper to normalize raw driver payloads (PascalCase or camelCase)
	 * @param {Record<string, any>} raw
	 * @returns {DriverRecord}
	 */
	#normalizeDriverRecord(raw) {
		return {
			racingNumber: raw.RacingNumber || raw.racingNumber || '',
			tla: raw.Tla || raw.tla || '',
			fullName: raw.FullName || raw.fullName || '',
			firstName: raw.FirstName || raw.firstName || '',
			lastName: raw.LastName || raw.lastName || '',
			broadcastName: raw.BroadcastName || raw.broadcastName || '',
			teamName: raw.TeamName || raw.teamName || 'Unknown Team',
			teamColour: raw.TeamColour || raw.teamColour || '808080',
			headshotUrl: raw.HeadshotUrl || raw.headshotUrl || '',
			line: raw.Line || raw.line || '99',
			reference: raw.Reference || raw.reference || '',
			publicIdRight: raw.PublicIdRight || raw.publicIdRight || ''
		};
	}
}
