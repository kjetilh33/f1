<script>
    import { subscribeSSE } from "./sse-client.svelte";
    import { Toast, ToastContainer } from "flowbite-svelte";
    import { BellRingOutline } from "flowbite-svelte-icons";
    import { fly } from "svelte/transition";
    import { onDestroy } from "svelte";

    /*
    * Race control messages
    * {"category":"RaceControlMessages",
    * "message":"{\"Messages\":[
    *   {\"Utc\":\"2025-12-07T12:20:00\",\"Lap\":1,\"Category\":\"Flag\",\"Flag\":\"GREEN\",\"Scope\":\"Track\",\"Message\":\"GREEN LIGHT - PIT EXIT OPEN\"},
    *   {\"Utc\":\"2025-12-07T12:30:00\",\"Lap\":1,\"Category\":\"Other\",\"Message\":\"PIT EXIT CLOSED\"}
    *  ]}",
    * "timestamp":1765479570.948033200,
    * "isStreaming":false
    * }
    * 
    * {
        "Messages": {
            "17": {
            "Utc": "2026-02-13T08:11:28",
            "Message": "YELLOW IN PIT LANE",
            "Category": "Other"
            }
        }
      }
    */

    /**
     * @typedef {Object} Record
     * @property {Date} timestamp - The timestamp of the record
     * @property {string} category - Record category
     * @property {object} message - Record message
     */

     /**
     * @typedef {Object} ToastItem
     * @property {Date} timestamp - The timestamp of the record
     * @property {string} category - toast category
     * @property {object} message - toast message
     * @property {number} id - toast id
     * @property {boolean} visible - toast visible
     * @property {ReturnType<typeof setTimeout>} [timeoutId] - toast timeout id
     */


    /**
	 * @type {Record[]}
	 */
    const messageStore = $state([]);
    /**
	 * @type {ToastItem[]}
	 */
    let toasts = $state([]);
    let nextId = $state(1);
    
    /*
    * Subscribe to SSE messages
    */
    subscribeSSE((message) => {
        if (message.category === "RaceControlMessages"
            && message.isStreaming
        ) {
            processMessage(message);
        }        
    });

    /**
     * @param {LiveTimingRecord} message
    */
    function processMessage(message) {
        /**
		 * @type {Record[]}
		 */
        const RaceControlMessages = [];

        // Check if there are more than one race control message in the event record
        if (Array.isArray(message.message.Messages)) {
            message.message.Messages.forEach((/** @type {any} */ element) => {
                RaceControlMessages.push({
                    timestamp: message.timestamp,
                    category: message.category,
                    message: element
                });
            });

        } else {
            Object.values(message.message.Messages).forEach((element) => {
                RaceControlMessages.push({
                    timestamp: message.timestamp,
                    category: message.category,
                    message: element
                });
            });   
        }

        RaceControlMessages.forEach(element => {
            messageStore.push(element);
            addToast(element);
        });
    }

    /**
	 * @param {Record} record
	 */
    function addToast(record) {
        /** @type {ToastItem} */
        const newToast = {...record, id: nextId, visible: true};

        // Auto-dismiss after 5 seconds
        const timeoutId = setTimeout(() => {
            dismissToast(newToast.id);
            }, 5000);
        newToast.timeoutId = timeoutId;

        toasts = [...toasts, newToast];
        nextId++;
    }

    /**
	 * @param {number} id
	 */
    function dismissToast(id) {
        // Clear timeout if it exists
        const toast = toasts.find((t) => t.id === id);
        if (toast?.timeoutId) {
            clearTimeout(toast.timeoutId);
        }

        // Set visible to false to trigger outro transition
        toasts = toasts.map((t) => (t.id === id ? { ...t, visible: false } : t));

        setTimeout(() => {
            toasts = toasts.filter((t) => t.id !== id);
        }, 300); // Slightly longer than transition duration
    }

    /**
	 * @param {number} id
	 */
    function handleClose(id) {
        return () => {
            dismissToast(id);
        };
    }

    onDestroy(() => {
        // Clear all pending timeouts on unmount
        toasts.forEach((toast) => {
            if (toast.timeoutId) {
                clearTimeout(toast.timeoutId);
            }
        });
    });
  
</script>

<ToastContainer position="top-right">
  {#each toasts as toast (toast.id)}
    <Toast align={false} dismissable={true} transition={fly} params={{ x: 200, duration: 800 }} onclose={handleClose(toast.id)} bind:toastStatus={toast.visible}>
        {#snippet icon()}
            <BellRingOutline class="h-6 w-6" />
        {/snippet}

        {toast.message}
    </Toast>
  {/each}
</ToastContainer>
