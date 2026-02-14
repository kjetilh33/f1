<script>
    import { subscribeSSE } from "./sse-client.svelte";
    import { parseNanoTimestamp } from "./utils";
    import { Toast, ToastContainer } from "flowbite-svelte";
    import { BellRingOutline } from "flowbite-svelte-icons";
    


    /*
    * Track status messages
    * {"category":"TrackStatus",
    * "message":"{
        "_kf": true,
        "Status": "1",
        "Message": "AllClear"
        }",
    * "timestamp":1765479570.948033200,
    * "isStreaming":true
    * }
    * 
    * {
        {
            "_kf": true,
            "Status": "6",
            "Message": "VSCDeployed"
        }
      }
    */

    /**
     * @typedef {Object} Record
     * @property {Date} date - The timestamp of the record
     * @property {string} category - Record category
     * @property {object} message - Record message
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
     * @param {any} message
    */
    function processMessage(message) {
        /**
		 * @type {Record[]}
		 */
        const RaceControlMessages = [];

        const mainRecord = {        
            date: parseNanoTimestamp(message.timestamp),
            category: message.category,
            message: JSON.parse(message.message)
        }

        // Check if there are more than one race control message in the event record
        if (Array.isArray(mainRecord.message.Messages)) {
            mainRecord.message.Messages.forEach((/** @type {any} */ element) => {
                RaceControlMessages.push({
                    date: mainRecord.date,
                    category: mainRecord.category,
                    message: element
                });
            });

        } else {
            Object.values(mainRecord.message.Messages).forEach((element) => {
                RaceControlMessages.push({
                    date: mainRecord.date,
                    category: mainRecord.category,
                    message: element
                });
            });   
        }

        RaceControlMessages.forEach(element => {
            messageStore.push(element);
        });
    }

</script>

<ToastContainer position="top-right">
  
</ToastContainer>
