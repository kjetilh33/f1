<script>
import { subscribeSSE, sseStore } from "./sse-client.svelte";

	/**
	 * @type {number[]}
	 */
let messagesPerSecond = $state([]);

let messagesPerSecondAverage = $derived.by(() => {
    let sum = 0;
    for (let i = 0; i < messagesPerSecond.length; i++) {
        sum += messagesPerSecond[i];
    }
    return sum / messagesPerSecond.length;    
});

let messageCounter = 0;

/**
 * @param {number} count
 */
function logMessagesPerSecond(count) {
    messagesPerSecond.push(count);

    if (sseStore.messages.length >= 10) {
        sseStore.messages.shift();
    }
}

/*
* Subscribe to SSE messages
*/
subscribeSSE((message) => {
    messageCounter++;
});

/*
* Log number of messages per second
*/
setInterval(() => {
    logMessagesPerSecond(messageCounter);
    messageCounter = 0; // Reset for the next second
}, 1000);



</script>

<div >
    <h3>SSE connection status: {sseStore.status}</h3>
    <h3>Messages per second: {messagesPerSecondAverage}</h3>

</div>