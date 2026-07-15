export class RaceControlMessages {
    /**
     * @type {RaceMessageRecord[]}
     */
    #raceControlMessages = $state([]);

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
     * 
     * @param {Object<string, any>} initialData 
     */
    initializeData(initialData) {
        if (!initialData) {
            console.error("No initial data for the race control messages.");
            return;            
        }

        if (Array.isArray(initialData.messages)) {
            this.#raceControlMessages = initialData.messages.map(element => this.#parseInitialRaceMessageRecord(element));
        } else {
            console.error("Initial data for race control messages is not in the expected format: ", initialData);
        }
    }

    /**
     * Update the collection with a new race message
     * 
     * @param {LiveTimingRecord} messageContainer 
     */
    update (messageContainer) {
        if (messageContainer.category === "RaceControlMessages" && messageContainer.isStreaming) {

            // Check if there are more than one race control message in the event record.
            // Typically these will be presented in object notation, but we need to handle both
            // array and object notation.
            if (Array.isArray(messageContainer.message.messages)) {
                messageContainer.message.messages.forEach((/** @type {Object<string, any>} */ element) => {
                    this.#raceControlMessages.push(this.#parseLiveRaceMessageRecord(messageContainer, element));
                });

            } else {
                Object.values(messageContainer.message.messages).forEach((/** @type {Object<string, any>} */ element) => {
                    this.#raceControlMessages.push(this.#parseLiveRaceMessageRecord(messageContainer, element));
                });   
            }
        }      
    }

    /**
    * @param {LiveTimingRecord} messageContainer
    * @param {Object<string, any>} element
    * @returns {RaceMessageRecord}
    */
    #parseLiveRaceMessageRecord(messageContainer, element) {
      return {
                timestamp: element.utc ? new Date(element.utc) : new Date(messageContainer.timestamp),
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
                timestamp: element.utc ? new Date(element.utc) : new Date(),
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

/*
    * Add some test data
    */
    let testData = [
      {
        timestamp: new Date("2026-02-13T16:56:58Z"),
        category: "Flag",
        message: "YELLOW IN TRACK SECTOR 15",
        id: 9999,
        flag: "YELLOW",
        scope: "Sector",
        sector: 15
      },
      {
        timestamp: new Date("2026-02-13T16:57:00Z"),
        category: "Flag",
        message: "DOUBLE YELLOW IN TRACK SECTOR 16",
        id: 9999,
        flag: "DOUBLE YELLOW",
        scope: "Sector",
        sector: 16
      },
      {
        timestamp: new Date("2026-02-13T16:57:39Z"),
        category: "Flag",
        message: "CLEAR IN TRACK SECTOR 15",
        id: 9999,
        flag: "CLEAR",
        scope: "Sector",
        sector: 15
      }

    ];