export class TrackStatus {
    /**
     * @type {TrackStatusRecord}
     */
    #trackStatus = $state({});

    get trackStatus() {
        return this.#trackStatus;
    }

    /**
     * Clear state cleanly on reset
     */
    clear() {
        this.#trackStatus = {};
    }

    /**
     * 
     * @param {Object<string, any>} initialData 
     */
    initializeData(initialData) {
        if (!initialData) {
            console.error("No initial data for the track status.");
            return;            
        }

        this.#trackStatus.timestamp = new Date();
        this.#trackStatus.status = initialData.status ? initialData.status : -1;
        this.#trackStatus.message = initialData.message ? initialData.message : "unknown";
    }

    /**
     * Update the collection with a new race message
     * 
     * @param {LiveTimingRecord} messageContainer 
     */
    update (messageContainer) {
        if (messageContainer.category === "TrackStatus" && messageContainer.isStreaming) {
            this.#trackStatus.timestamp = new Date(messageContainer.timestamp);
            this.#trackStatus.status = messageContainer.message.status;
            this.#trackStatus.message = messageContainer.message.message;
        }
    }
}