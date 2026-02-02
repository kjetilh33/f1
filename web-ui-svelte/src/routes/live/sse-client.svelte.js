import { parseNanoTimestamp } from "./utils";

/**
 * @type {((arg0: JSON) => void)[]}
 */
const eventListeners = [];

/**
 * @type {EventSource}
 */
let eventSource;

let messageIndex = 0;

/**
 * @type {{ status: string, messages: {id: number, date: Date, category: string, message: any }[] }}
 */
export const sseStore = $state({
    status: 'disconnected',
    messages: []
});

/**
 * @param {any} message
 */
function addMessage(message) {
    //console.log(message);
    const maxLenght = 20;

    const record = {
        id: messageIndex,
        date: parseNanoTimestamp(message.timestamp),
        category: message.category,
        message: message.message
    }

    sseStore.messages.push(record);
    messageIndex++;


    if (sseStore.messages.length >= maxLenght) {
        sseStore.messages.shift();
    }
}

/**
 * @param {MessageEvent<any>} event
 */
function parseEvent(event) {
    let message = JSON.parse(event.data);
    if (Object.keys(message).length === 0 ) {
        // It is a keep alive message. Create a substitute record
        message = {
            category: "keep-alive",
            message: "Keep alive message: {}",
            timestamp: Math.floor(Date.now() / 1000)
        }
    }
    
    return message;
}

/**
 * @param {function (JSON) : void } listener
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

    messageIndex = 0;    
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