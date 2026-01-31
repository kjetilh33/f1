
/**
 * @type {((arg0: JSON) => void)[]}
 */
const eventListeners = [];

/**
 * @type {{ status: string, messages: { date: Date, message: any, messageShort: any }[] }}
 */
export const sseStore = {
    status: 'disconnected',
    messages: []
};

/**
 * @param {any} message
 */
function addMessage(message) {
    const maxLenght = 20;

    const record = {
        date: new Date(),
        message: message,
        messageShort: message
    }
    sseStore.messages.push(record);

    if (sseStore.messages.length >= maxLenght) {
        sseStore.messages.shift();
    }
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
    const eventSource = new EventSource(url);

    sseStore.status = 'connecting';

    eventSource.onopen = () => {
        sseStore.status = 'connected';
        console.log("Connected to SSE endpoint");
    };

    eventSource.onmessage = (event) => {
        const data = JSON.parse(event.data);
        addMessage(data);
        eventListeners.forEach(listener => listener(data));
    };

    eventSource.onerror = () => {
        sseStore.status = 'disconnected';
        eventSource.close();
    };

    return sseStore;
}
