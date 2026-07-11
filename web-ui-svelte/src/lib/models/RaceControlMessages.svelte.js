export class RaceControlMessages {
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
     * @param {Object} initialData 
     */
    initializeData(initialData) {
        //this.#raceControlMessages = initialData;
    }

    /**
     * Update the collection with a new race message
     * 
     * @param {LiveTimingRecord} message 
     */
    update (message) {
        if (message.category === "RaceControlMessages" && message.isStreaming) {
            /**
             * @type {RaceMessageRecord[]}
             */
            const RaceControlMessages = [];

            // Check if there are more than one race control message in the event record
            if (Array.isArray(message.message.messages)) {
                message.message.Messages.forEach((/** @type {any} */ element) => {
                    RaceControlMessages.push(this.#parseLiveRaceMessageRecord(message, element));
                });

            } else {
                Object.values(message.message.messages).forEach((element) => {
                    RaceControlMessages.push(this.#parseLiveRaceMessageRecord(message, element));
                });   
            }

            RaceControlMessages.forEach(element => {
                this.#raceControlMessages.push(element);
            });
        }      
    }

    /**
    * @param {LiveTimingRecord} messageContainer
    * @param {any} element
    * @returns {RaceMessageRecord}
    */
    #parseLiveRaceMessageRecord(messageContainer, element) {
      return {
                timestamp: element.utc ? element.utc : messageContainer.timestamp,
                category: element.category,
                message: element,
                lap: element.Lap,
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