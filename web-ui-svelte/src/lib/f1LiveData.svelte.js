import { SseStreamHandler } from './sseStreamHandler.svelte.js';
import { RaceControlMessages } from './models/RaceControlMessages.svelte.js';
import { TrackStatus } from './models/TrackStatus.svelte.js';

class F1LiveData {
    // main data structures
    #sessionStatus = $state({});
    #sessionData = $state({});
    #driverList = $state({});
    #raceControlMessages = new RaceControlMessages();
    #trackStatus = new TrackStatus();    
    #timingData = $state({});
    #timingAppData = $state({});
    #timingStats = $state({});
    #weatherData = $state({});

    // API endpoints for fetching data
    #urlPrefix = "/../api/v1/live";
    #sessionStatusUrl = `${this.#urlPrefix}`;
    #sessionInfoUrl = `${this.#urlPrefix}/session-info`;
    #raceMessagesUrl = `${this.#urlPrefix}/race-control-messages`;
    #driverListUrl = `${this.#urlPrefix}/driver-list`;
    #weatherDataUrl = `${this.#urlPrefix}/weather-data`;
    #timingDataUrl = `${this.#urlPrefix}/timing-data`;

    // SSE stream handler
    #sseUrl = `${this.#urlPrefix}/livetiming`;
    #sse = new SseStreamHandler();

    // Unsubscriber for the SSE stream
    /** @type {(() => void) | null} */
    #sseUnsubscribe = null;

    constructor() {

    }

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

    get raceControlMessages() {
        return this.#raceControlMessages.raceControlMessages;
    }

    get trackStatus() {
        return this.#trackStatus.trackStatus;
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
            this.#sessionStatus = await this.#getLiveTimingData(this.#sessionStatusUrl);
            this.#sessionData = await this.#getLiveTimingData(this.#sessionInfoUrl);
            this.#driverList = await this.#getLiveTimingData(this.#driverListUrl);
            this.#raceControlMessages.initializeData(await this.#getLiveTimingData(this.#raceMessagesUrl));
            this.#weatherData = await this.#getLiveTimingData(this.#weatherDataUrl);
            this.#timingData = await this.#getLiveTimingData(this.#timingDataUrl);
            // TODO TrackStatus

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

    cleanup() {
        // 1. Stop streaming data immediately
        this.#sse.disconnect();
        if (this.#sseUnsubscribe) {
            this.#sseUnsubscribe();
            this.#sseUnsubscribe = null;
        }

        // 2. Clear out application states cleanly
        this.#sessionStatus = {};
        this.#sessionData = {};
        this.#driverList = {};
        this.#raceControlMessages.clear();
        this.#trackStatus.clear();    
        this.#timingData = {};
        this.#timingAppData = {};
        this.#timingStats = {};
        this.#weatherData = {};
        this.#sse.clearMessages();
    }

    async reset() {
        // Cleanup the current state and disconnect from the SSE stream
        this.cleanup();

        // Reboot the stack cleanly
        await this.initialize();
    }

    /**
     * 
     * @param {String} url 
     */
    async #getLiveTimingData(url) {
        const response = await fetch(url);
        if (!response.ok) {
            // Attempt to parse server-provided error message, fallback to status text
            //const errorBody = await response.json().catch(() => ({}));
            //const errorMessage = errorBody.message || `HTTP ${response.status}: ${response.statusText}`;
            const errorMessage = `HTTP ${response.status}: ${response.statusText}`;
            console.error(errorMessage);
            return {
                error: errorMessage
            }
        }
        return await response.json();
    }

     /**
     * @param {LiveTimingRecord} message
     */
    #routeIncomingData(message) {
        // 1. Guard clause: Ignore keep-alives or records without streaming context
        if (!message || !message.category) return;

        // Direct fine-grained mutation updates to correct data slots
        // 2. Main data-routing junction tree based on feed categories
        switch (message.category) {
            case "RaceControlMessages":
                this.#raceControlMessages.update(message);
                break;

            case "TrackStatus":
                this.#trackStatus.update(message);
                break;
/*
            case "TimingData":
                this.#updateTimingData(message);
                break;

            case "SessionStatus":
                this.#updateSessionStatus(message);
                break;
*/
            default:
                // Gracefully ignore unknown or unimplemented categories
                break;
        }
    }
}

export const f1LiveData = new F1LiveData();