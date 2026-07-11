export class RaceControlMessages {
    #raceControlMessages = $state({});

    get raceControlMessages() {
        return this.#raceControlMessages;
    }

    /**
     * Clear state cleanly on reset
     */
    clear() {
        this.#raceControlMessages = {};
    }

    /**
     * 
     * @param {Object} initialData 
     */
    initializeData(initialData) {
        this.#raceControlMessages = initialData;
    }

    /**
     * Update the collection with a new race message
     * 
     * @param {LiveTimingRecord} message 
     */
    update (message) {
        // 1. Guard clause: Ignore keep-alives or records without streaming context
        if (!message || message.category != "RaceControlMessages") return;

    }
}