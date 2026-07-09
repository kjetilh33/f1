import { SseStreamHandler } from './sseStreamHandler.js';

class F1LiveData {
    // main data structures
    #sessionStatus = $state({});
    #sessionData = $state({});
    #driverList = $state({});
    #raceMessages = $state({});
    #trackStatus = $state({});    
    #timingData = $state({});
    #timingAppData = $state({});
    #timingStats = $state({});
    #weatherData = $state({});

    // SSE stream handler
    #sseUrl = "/../api/v1/live/livetiming";
    #sse = new SseStreamHandler();

    // Unsubscriber for the SSE stream
    /** @type {(() => void) | null} */
    #sseUnsubscribe = null;

    // SSE stream health metrics
    get sseConnectionStatus() {
        return this.#sse.status;
    }

    get sseMessages() {
        return this.#sse.messages;
    }

    // Public getters for the private properties
    get sessionStatus() {
        return this.#sessionStatus;
    }

    get sessionData() {
        return this.#sessionData;
    }   

    get driverList() {
        return this.#driverList;
    }

    get raceMessages() {
        return this.#raceMessages;
    }

    get trackStatus() {
        return this.#trackStatus;
    }

    get timingData() {
        return this.#timingData;
    }

    get timingAppData() {
        return this.#timingAppData;
    }

    get timingStats() {
        return this.#timingStats;
    }

    get weatherData() {
        return this.#weatherData;
    }

    async initialize() {
        try {
            // Fetch initial data from the API
            //const response = await fetch('/api/live/initial-data');
            //const initialData = await response.json();

            // Setup the event dispatcher intercept hook *before* opening the stream
            this.#sseUnsubscribe = this.#sse.subscribe((message) => {
                this.#routeIncomingData(message);
            });

            // Fire network connection trigger
            this.#sse.connect(this.#sseUrl);

        } catch (error) {
            console.error("Error initializing the live data:", error);
        }
    }

    async reset() {
        // 1. Stop streaming data immediately
        this.#sse.disconnect();
        if (this.#sseUnsubscribe) {
            this.#sseUnsubscribe();
        }

        // 2. Clear out application states cleanly
        this.#sessionStatus = {};
        this.#sessionData = {};
        this.#driverList = {};
        this.#raceMessages = {};
        this.#trackStatus = {};    
        this.#timingData = {};
        this.#timingAppData = {};
        this.#timingStats = {};
        this.#weatherData = {};
        this.#sse.clearMessages();

        // 3. Reboot the stack cleanly
        await this.initialize();
    }

    
    /**
     * @param {LiveTimingRecord} message
     */
    #routeIncomingData(message) {
        // Direct fine-grained mutation updates to correct data slots
        if (message.category === "RaceControlMessages" && message.isStreaming) {
                //processMessage(message);
        }
    }    
}

export const f1LiveData = new F1LiveData();