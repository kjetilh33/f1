<script>
    import { f1LiveData } from "$lib/f1LiveData.svelte";
    import { Badge } from "flowbite-svelte";
    import { FlagOutline, TruckOutline } from "flowbite-svelte-icons";
    
    /** @import { BadgeProps  } from "flowbite-svelte" */

    let trackStatus = f1LiveData.trackStatus;

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
