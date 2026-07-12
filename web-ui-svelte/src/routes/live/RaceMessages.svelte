<script>
    import { f1LiveData } from "$lib/f1LiveData.svelte";
    import { Card, Listgroup } from "flowbite-svelte";
    import { FlagOutline, TruckOutline } from "flowbite-svelte-icons";

    /**
	  * @type {RaceMessageRecord[]}
	  */
    let reversedMessageStore = $derived(f1LiveData.raceControlMessages.toReversed());
    
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

<Card class="p-4 sm:p-8 md:p-10" size="md">
  <div class="mb-4 flex items-center justify-between">
    <h5 class="text-xl leading-none font-bold text-gray-900 dark:text-white">Race control messages</h5>
    
  </div>
  <Listgroup items={reversedMessageStore} class="border-0 dark:bg-transparent!">
    {#snippet children(item)}
      <div class="flex items-center space-x-4 py-2 rtl:space-x-reverse">
        {#if item && typeof item === "object" }
          {#if item.category === "SafetyCar"}
            <TruckOutline />
          {:else}
            <FlagOutline />
          {/if}
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
