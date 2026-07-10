export class SseStreamHandler {
    #status = $state('disconnected');
    
    /** @type {LiveTimingRecord[]} */
    #messages = $state([]);
    #maxLengthMessageArray = 20;

    /** @type {EventSource | null} */
    #eventSource = null;

    /** @type {((arg0: LiveTimingRecord) => void)[]} */
    #eventListeners = [];

    // Public read-only getters for the private properties
    get status() {
        return this.#status;
    }

    get messages() {
        return this.#messages;
    }


    /**
     * Connects to the SSE streaming endpoint
     * @param {string | URL} url 
     */
    connect(url) {
        this.disconnect(); // Guard: Automatically clean up any existing connection first

        this.#eventSource = new EventSource(url);
        this.#status = 'connecting';

        this.#eventSource.onopen = () => {
            this.#status = 'connected';
            console.log("Connected to the SSE endpoint");
        };

        this.#eventSource.onmessage = (event) => {
            const parsedData = this.#parseEvent(event);
            this.#addMessage(parsedData);
            
            // Notify manual hook listeners
            this.#eventListeners.forEach(listener => listener(parsedData));
        };

        this.#eventSource.onerror = () => {
            console.error("SSE connection dropped");
            this.disconnect(); // Safely closes ports and resets state flags
        };
    }

    /**
     * Shuts down connections and cleanly flips state statuses
     */
    disconnect() {
        if (this.#eventSource) {
            this.#eventSource.close();
            this.#eventSource = null;
        }
        this.#status = 'disconnected';
    }

    /**
     * Allows external modules (like your AppStateManager) to tap directly into raw stream ticks
     * @param {function (LiveTimingRecord) : void } listener
     * @returns {() => void} Unsubscribe cleanup function
     */
    subscribe(listener) {
        this.#eventListeners.push(listener);
        console.log("Added SSE event listener");
        
        return () => {
            const index = this.#eventListeners.indexOf(listener);
            if (index !== -1) {
                this.#eventListeners.splice(index, 1);
                console.log("Removed SSE event listener");
            }
        };
    }

    /**
     * Completely purges the debug message buffer memory
     */
    clearMessages() {
        this.#messages = [];
    }

    // 4. Private Helper Methods (Unreachable from outside the class)
    /**
     * @param {LiveTimingRecord} message
     */
    #addMessage(message) {
        this.#messages.push(message);

        // Maintain sliding historical log window
        if (this.#messages.length >= this.#maxLengthMessageArray) {
            this.#messages.shift();        
        }
    }

    /**
     * @param {MessageEvent<any>} event
     * @returns {LiveTimingRecord}
     */
    #parseEvent(event) {
        let message = JSON.parse(event.data);
        
        if (Object.keys(message).length === 0) {
            // It is a keep alive message. Create a substitute record
            message = {
                category: "keep-alive",
                message: JSON.stringify({ message: "Keep alive message" }),
                timestamp: new Date().toISOString(),
                isStreaming: false
            };
        }

        return {
            ...message,
            message: JSON.parse(message.message),
            timestamp: new Date(message.timestamp)
        };
    }
}

