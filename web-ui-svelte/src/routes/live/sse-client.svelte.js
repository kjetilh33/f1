import { parseNanoTimestamp } from "./utils";

/**
 * @type {((arg0: LiveTimingRecord) => void)[]}
 */
const eventListeners = [];

/**
 * @type {EventSource}
 */
let eventSource;

const maxLenghtMessageArray = 20;

/**
 * @type {{ status: string, messages: LiveTimingRecord[] }}
 */
export const sseStore = $state({
    status: 'disconnected',
    messages: []
});

/**
 * @param {LiveTimingRecord} message
 */
function addMessage(message) {
    //console.log(message);
    sseStore.messages.push(message);

    if (sseStore.messages.length >= maxLenghtMessageArray) {
        sseStore.messages.shift();        
    }

    //console.log("Number of SSE messages: ", sseStore.messages.length);
    //console.log($state.snapshot(sseStore.messages));
}

/**
 * @param {MessageEvent<any>} event
 * @returns {LiveTimingRecord}
 */
function parseEvent(event) {
    let message = JSON.parse(event.data);
    if (Object.keys(message).length === 0 ) {
        // It is a keep alive message. Create a substitute record
        message = {
            category: "keep-alive",
            message: JSON.stringify({message: "Keep alive message"}),
            timestamp: Math.floor(Date.now() / 1000),
            isStreaming: false
        }
    }

    message = {...message, message: JSON.parse(message.message), timestamp: parseNanoTimestamp(message.timestamp)};
    
    return message;
}

/**
 * @param {function (LiveTimingRecord) : void } listener
 */
export function subscribeSSE(listener) {
    eventListeners.push(listener);
    console.log("Added SSE listener");
}


/**
 * @param {string | URL} url
 */
export function connectSSE(url) {
    if (eventSource != null) {
        eventSource.close();
    }

    //messageIndex = 0;    
    eventSource = new EventSource(url);

    sseStore.status = 'connecting';

    eventSource.onopen = () => {
        sseStore.status = 'connected';
        console.log("Connected to SSE endpoint");
    };

    eventSource.onmessage = (event) => {
        const data = parseEvent(event);
        addMessage(data);
        eventListeners.forEach(listener => listener(data));
    };

    eventSource.onerror = () => {
        sseStore.status = 'disconnected';
        eventSource.close();
    };

    return sseStore;
}

/**
 * 
 */
export function disconnectSSE() {
    if (eventSource != null) {
        eventSource.close();
    }
    sseStore.status = 'disconnected';
}