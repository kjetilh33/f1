<script>
    import { subscribeSSE } from "./sse-client.svelte";
    import { Badge } from "flowbite-svelte";
    import { FlagOutline, TruckOutline } from "flowbite-svelte-icons";
    
    /** @import { BadgeProps  } from "flowbite-svelte" */


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
     * @typedef {Object} TrackStatusItem
     * @property {Date} timestamp - The timestamp of the record
     * @property {string} category - Record category
     * @property {string} message - track status message
     * @property {string} status - track status 
     */

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


    /**
	 * @type {TrackStatusItem}
	 */
    let trackStatus = $state(
        {
            timestamp: new Date(),
            category: "TrackStatus",
            message: "Unknown",
            status: "-1"            
        }
    );

    /**
     * @type {BadgeProps["color"]}
     */
    let trackStatusBadgeColor = $derived.by(() => {
        if (trackStatus.status === "1") {
            return "green";
        } else if (trackStatus.status === "5") {
            return "red";
        } else if (trackStatus.status === "-1") {
            return "gray";
        }else {
            return "yellow";
        }
    });
    
    /*
    * Subscribe to SSE messages
    */
    subscribeSSE((message) => {
        if (message.category === "TrackStatus"
            && message.isStreaming
        ) {
            processMessage(message);
        }        
    });

    /**
     * @param {LiveTimingRecord} message
    */
    function processMessage(message) {
        trackStatus = {
            timestamp: message.timestamp,
            category: message.category,
            message: message.message.Message,
            status: message.message.Status
        };
    }

</script>

<div class="mb-4 flex justify-between w-sm">
    <Badge color={trackStatusBadgeColor} large border>
        <FlagOutline class="me-1.5 h-2.5 w-2.5" />
        {trackStatus.message}
    </Badge>
    <div class="self-start inline-flex items-center text-sm font-light text-gray-500 dark:text-gray-400">
        {formatter.format(trackStatus.timestamp)}
    </div>
</div>
