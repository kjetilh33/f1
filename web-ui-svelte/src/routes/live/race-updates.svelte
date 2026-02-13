<script>
    import { subscribeSSE } from "./sse-client.svelte";
    import { parseNanoTimestamp } from "./utils";
    import { Badge } from "flowbite-svelte";
    import { DownloadSolid } from "flowbite-svelte-icons";

    /*
    * Race control messages
    * {"category":"RaceControlMessages",
    * "message":"{\"Messages\":[
    *   {\"Utc\":\"2025-12-07T12:20:00\",\"Lap\":1,\"Category\":\"Flag\",\"Flag\":\"GREEN\",\"Scope\":\"Track\",\"Message\":\"GREEN LIGHT - PIT EXIT OPEN\"},
    *   {\"Utc\":\"2025-12-07T12:30:00\",\"Lap\":1,\"Category\":\"Other\",\"Message\":\"PIT EXIT CLOSED\"},
    *   {\"Utc\":\"2025-12-07T12:45:02\",\"Lap\":1,\"Category\":\"Other\",\"Message\":\"RISK OF RAIN FOR F1 RACE IS 0%\"},
    *   {\"Utc\":\"2025-12-07T12:51:46\",\"Lap\":1,\"Category\":\"Flag\",\"Flag\":\"DOUBLE YELLOW\",\"Scope\":\"Sector\",\"Sector\":14,\"Message\":\"DOUBLE YELLOW IN TRACK SECTOR 14\"},
    *   {\"Utc\":\"2025-12-07T12:51:47\",\"Lap\":1,\"Category\":\"Other\",\"Message\":\"DRS DISABLED IN ZONE 1\"},
    *   {\"Utc\":\"2025-12-07T12:51:59\",\"Lap\":1,\"Category\":\"Other\",\"Message\":\"DRS ENABLED IN ZONE 1\"}
    *       ]}",
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
	 * @type {{ date: Date; category: any; message: any; }[]}
	 */
    const messageStore = $state([]);
    
    /*
    * Subscribe to SSE messages
    */
    subscribeSSE((message) => {
        if (message.category === "RaceControlMessages"
            && message.isStreaming
        ) {
            addMessage(message);
        }        
    });

    /**
     * @param {any} message
    */
    function addMessage(message) {
        //console.log(message);
        const maxLenght = 20;

        const record = {        
            date: parseNanoTimestamp(message.timestamp),
            category: message.category,
            message: JSON.parse(message.message)
        }

        messageStore.push(record);    
    }
  
</script>

<div >
    <Badge color={connectionBadgeColor} border>
        <DownloadSolid class="me-1.5 h-2.5 w-2.5" />
        {sseStore.status}
    </Badge>

</div>