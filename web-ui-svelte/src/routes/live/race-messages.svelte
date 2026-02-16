<script>
    import { subscribeSSE } from "./sse-client.svelte";
    import { Card, Listgroup } from "flowbite-svelte";
    import { FlagOutline } from "flowbite-svelte-icons";
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
     * @typedef {Object} RaceMessageItem
     * @property {number} id - message id
     * @property {Date} timestamp - The timestamp of the record
     * @property {string} category - message category
     * @property {string} message - message
     * @property {string} [flag] - Flag color
     * @property {string} [scope] - Scope of the message
     * @property {string} [mode] - Mode of safety car (VSC, etc.)
     * @property {string} [status] - Status of the safety car (deployed, ect.)
     * 
     */


    /**
	 * @type {RaceMessageItem[]}
	 */
    const messageStore = $state([]);
    
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
		 * @type {RaceMessageItem[]}
		 */
        const RaceControlMessages = [];

        // Check if there are more than one race control message in the event record
        if (Array.isArray(message.message.Messages)) {
            message.message.Messages.forEach((/** @type {any} */ element) => {
                RaceControlMessages.push({
                    timestamp: message.timestamp,
                    category: element.category,
                    message: element,
                    id: nextId++,
                    flag: element.flag,
                    scope: element.scope,
                    mode: element.mode,
                    status: element.status
                });
            });

        } else {
            Object.values(message.message.Messages).forEach((element) => {
                RaceControlMessages.push({
                    timestamp: message.timestamp,
                    category: element.category,
                    message: element,
                    id: nextId++,
                    flag: element.flag,
                    scope: element.scope,
                    mode: element.mode,
                    status: element.status
                });
            });   
        }

        RaceControlMessages.forEach(element => {
            messageStore.push(element);
        });
    }

    onDestroy(() => {
        // Clear all pending timeouts on unmount
        
    });
  
</script>

<Card >
  <div class="mb-4 flex items-center justify-between">
    <h5 class="text-xl leading-none font-bold text-gray-900 dark:text-white">Race control messages</h5>
    
  </div>
  <Listgroup items={messageStore} class="border-0 dark:bg-transparent!">
    {#snippet children(item)}
      <div class="flex items-center space-x-4 py-2 rtl:space-x-reverse">
        {#if typeof item === "object" }
          <FlagOutline />
          <div class="min-w-0 flex-1">
            <p class="truncate text-sm font-medium text-gray-900 dark:text-white">
              {item.category}
            </p>
            <p class="truncate text-sm text-gray-500 dark:text-gray-400">
              {item.message}
            </p>
          </div>
          <div class="inline-flex items-center text-base font-semibold text-gray-900 dark:text-white">
            {item.value}
          </div>
        {/if}
      </div>
    {/snippet}
  </Listgroup>

</Card>
