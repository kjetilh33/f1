<script>
    import { subscribeSSE, sseStore } from "./sse-client.svelte";
    import { Badge, Popover } from "flowbite-svelte";
    import { DownloadSolid } from "flowbite-svelte-icons";
    import { Chart } from "@flowbite-svelte-plugins/chart";


    /** @import { BadgeProps  } from "flowbite-svelte" */
    /** @import { ApexOptions  } from "apexcharts" */

    /**
     * @type {BadgeProps["color"]}
     */
    let connectionBadgeColor = $derived.by(() => {
        if (sseStore.status === "connected") {
            return "green";
        } else if (sseStore.status === "connecting") {
            return "yellow";
        } else {
            return "gray";
        }
    });


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

        if (messagesPerSecond.length >= 20) {
            messagesPerSecond.shift();
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
    <Badge color={connectionBadgeColor} border>
        <DownloadSolid class="me-1.5 h-2.5 w-2.5" />
        {sseStore.status}
    </Badge>
    <Popover class="w-64 text-sm font-light" >
        <div class="space-y-2">
            <h3 class="font-semibold text-gray-900 dark:text-white">Messages /s: {messagesPerSecondAverage.toFixed(2)}</h3>
            <p class="text-gray-500 dark:text-gray-400">
                Some text
            </p>
        </div>
    </Popover >

</div>