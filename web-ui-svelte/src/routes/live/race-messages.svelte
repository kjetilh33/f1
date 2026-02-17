<script>
    import { subscribeSSE } from "./sse-client.svelte";
    import { Card, Listgroup } from "flowbite-svelte";
    import { FlagOutline } from "flowbite-svelte-icons";
    import { onDestroy } from "svelte";

     /**
     * @typedef {Object} RaceMessageItem
     * @property {number} id - message id
     * @property {Date} timestamp - The timestamp of the record
     * @property {string} category - message category
     * @property {string} message - message
     * @property {string} [flag] - Flag color
     * @property {string} [scope] - Scope of the message
     * @property {number} [sector] - Sector of the message
     * @property {string} [mode] - Mode of safety car (VSC, etc.)
     * @property {string} [status] - Status of the safety car (deployed, ect.)
     * 
     */


    /**
	  * @type {RaceMessageItem[]}
	  */
    const messageStore = $state([]);

    /**
	  * @type {RaceMessageItem[]}
	  */
    let reversedMessageStore = $derived(messageStore.toReversed());
    
    let nextId = $state(1);

    // Formatter defined outside the map for performance
    const formatter = new Intl.DateTimeFormat('en-US', {
        month: 'short',
        day: 'numeric',
        hour: 'numeric',
        minute: 'numeric',
        second: 'numeric',
        hour12: false,
        timeZoneName: 'short',
        timeZone: 'UTC'
    });

    /*
    * Add some test data
    */
    const testData = [
      {
        timestamp: new Date("2026-02-13T16:56:58Z"),
        category: "Flag",
        message: "YELLOW IN TRACK SECTOR 15",
        id: 9999,
        flag: "YELLOW",
        scope: "Sector",
        sector: 15
      },
      {
        timestamp: new Date("2026-02-13T16:57:00Z"),
        category: "Flag",
        message: "DOUBLE YELLOW IN TRACK SECTOR 16",
        id: 9999,
        flag: "DOUBLE YELLOW",
        scope: "Sector",
        sector: 16
      },
      {
        timestamp: new Date("2026-02-13T16:57:39Z"),
        category: "Flag",
        message: "CLEAR IN TRACK SECTOR 15",
        id: 9999,
        flag: "CLEAR",
        scope: "Sector",
        sector: 15
      }

    ]
    
    messageStore.push(...testData);
    
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
                    sector: element.sector,
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
                    sector: element.sector,
                    mode: element.mode,
                    status: element.status
                });
            });   
        }

        RaceControlMessages.forEach(element => {
            messageStore.push(element);
        });
    }
  
</script>

<Card class="p-4 sm:p-8 md:p-10" size="md">
  <div class="mb-4 flex items-center justify-between">
    <h5 class="text-xl leading-none font-bold text-gray-900 dark:text-white">Race control messages</h5>
    
  </div>
  <Listgroup items={reversedMessageStore} class="border-0 dark:bg-transparent!">
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
          <div class="self-start inline-flex items-center text-sm font-light text-gray-500 dark:text-gray-400">
            {formatter.format(item.timestamp)}
          </div>
        {/if}
      </div>
    {/snippet}
  </Listgroup>

</Card>
