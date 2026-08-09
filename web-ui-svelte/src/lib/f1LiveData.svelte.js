import { SseStreamHandler } from './sseStreamHandler.svelte.js';
import { RaceControlMessages } from './models/raceControlMessages.svelte.js';
import { TrackStatus } from './models/trackStatus.svelte.js';
import { WeatherData } from './models/weatherData.svelte.js';
import { DriverList } from './models/driverList.svelte.js';
import { TimingDataModel } from './models/timingData.svelte.js';
import { ExtrapolatedClock } from './models/extrapolatedClock.svelte.js';

class F1LiveData {
	// main data structures
	#sessionStatus = $state({});
	#sessionData = $state({});
	#driverList = new DriverList();
	#raceControlMessages = new RaceControlMessages();
	#trackStatus = new TrackStatus();
	#timingDataModel = new TimingDataModel();
	#weatherData = new WeatherData();
	#clock = new ExtrapolatedClock();

	// API endpoints for fetching data
	#urlPrefix = '/../api/v1/live';
	#sessionStatusUrl = `${this.#urlPrefix}`;
	#sessionInfoUrl = `${this.#urlPrefix}/session-info`;
	#raceMessagesUrl = `${this.#urlPrefix}/race-control-messages`;
	#driverListUrl = `${this.#urlPrefix}/driver-list`;
	#trackStatusUrl = `${this.#urlPrefix}/track-status`;
	#weatherDataUrl = `${this.#urlPrefix}/weather-data`;
	#timingDataUrl = `${this.#urlPrefix}/timing-data`;

	// SSE stream handler
	#sseUrl = `${this.#urlPrefix}/livetiming`;
	#sse = new SseStreamHandler();

	/** @type {(() => void) | null} */
	#sseUnsubscribe = null;

	constructor() {}

	// SSE stream health metrics
	get sseConnectionStatus() {
		return this.#sse.status;
	}

	get sseMessages() {
		return this.#sse.messages;
	}

	// Public getters
	get sessionStatus() {
		return this.#sessionStatus;
	}

	get sessionData() {
		return this.#sessionData;
	}

	get driverList() {
		return this.#driverList.driverList;
	}

	get raceControlMessages() {
		return this.#raceControlMessages.raceControlMessages;
	}

	get trackStatus() {
		return this.#trackStatus.trackStatus;
	}

	get timingData() {
		return this.#timingDataModel;
	}

	get leaderboard() {
		return this.#timingDataModel.getLeaderboard(this.#driverList.driverList);
	}

	get weatherData() {
		return this.#weatherData.weatherData;
	}

	get clock() {
		return this.#clock.clock;
	}

	async initialize() {
		try {
			// Fetch initial REST snapshot data
			this.#sessionStatus = await this.#getLiveTimingData(this.#sessionStatusUrl);
			this.#sessionData = await this.#getLiveTimingData(this.#sessionInfoUrl);

			const rawDrivers = await this.#getLiveTimingData(this.#driverListUrl);
			this.#driverList.initializeData(rawDrivers);

			this.#trackStatus.initializeData(await this.#getLiveTimingData(this.#trackStatusUrl));
			this.#raceControlMessages.initializeData(
				await this.#getLiveTimingData(this.#raceMessagesUrl)
			);
			this.#weatherData.initializeData(await this.#getLiveTimingData(this.#weatherDataUrl));

			const rawTiming = await this.#getLiveTimingData(this.#timingDataUrl);
			this.#timingDataModel.initializeData(rawTiming);

			// Intercept SSE ticks before connecting
			this.#sseUnsubscribe = this.#sse.subscribe((message) => {
				this.#routeIncomingData(message);
			});

			// Connect to live stream
			this.#sse.connect(this.#sseUrl);
		} catch (error) {
			console.error('Error initializing the live data:', error);
		}
	}

	cleanup() {
		this.#sse.disconnect();
		if (this.#sseUnsubscribe) {
			this.#sseUnsubscribe();
			this.#sseUnsubscribe = null;
		}

		this.#sessionStatus = {};
		this.#sessionData = {};
		this.#driverList.clear();
		this.#raceControlMessages.clear();
		this.#trackStatus.clear();
		this.#timingDataModel.clear();
		this.#weatherData.clear();
		this.#clock.clear();
		this.#sse.clearMessages();
	}

	async reset() {
		this.cleanup();
		await this.initialize();
	}

	/**
	 * @param {function (LiveTimingRecord) : void } listener
	 * @returns {() => void}
	 */
	subscribeSSE(listener) {
		return this.#sse.subscribe(listener);
	}

	/**
	 * Subscribe to newly received streaming race control messages
	 * @param {function (RaceMessageRecord) : void} listener
	 * @returns {() => void} Unsubscribe cleanup function
	 */
	onRaceControlMessage(listener) {
		return this.#raceControlMessages.onMessage(listener);
	}

	/**
	 * @param {String} url
	 */
	async #getLiveTimingData(url) {
		const response = await fetch(url);
		if (!response.ok) {
			const errorMessage = `HTTP ${response.status}: ${response.statusText}`;
			console.error(errorMessage);
			return { error: errorMessage };
		}
		return await response.json();
	}

	/**
	 * @param {LiveTimingRecord} message
	 */
	#routeIncomingData(message) {
		if (!message || !message.category) return;

		switch (message.category) {
			case 'RaceControlMessages':
				this.#raceControlMessages.update(message);
				break;

			case 'TrackStatus':
				this.#trackStatus.update(message);
				break;

			case 'WeatherData':
				this.#weatherData.update(message);
				break;

			case 'DriverList':
				this.#driverList.update(message);
				break;

			case 'TimingData':
				this.#timingDataModel.updateTimingData(message);
				break;

			case 'TimingAppData':
				this.#timingDataModel.updateTimingAppData(message);
				break;

			case 'ExtrapolatedClock':
				this.#clock.update(message);
				break;

			default:
				break;
		}
	}
}

export const f1LiveData = new F1LiveData();
