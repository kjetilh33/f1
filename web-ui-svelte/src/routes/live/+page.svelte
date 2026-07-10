<script>
  import { Tabs, TabItem } from "flowbite-svelte";
  import { connectSSE, disconnectSSE } from "./sse-client.svelte";
  import { onMount } from 'svelte';
  import { f1LiveData } from "$lib/f1LiveData.svelte.js";
  import SseStatus from "./SseStatus.svelte";
  import LivetimingMessages from "./LivetimingMessages.svelte";
  import RaceMessageUpdates from "./RaceMessageUpdates.svelte";
  import RaceMessages from "./RaceMessages.svelte";

  let { data } = $props();

  onMount(() => {
    // EventSource is a browser API and runs only on the client
    connectSSE("/../api/v1/live/livetiming"); 
    f1LiveData.initialize(); // Initialize the f1LiveData store
    
    // Cleanup function for when the component is destroyed
    return () => {
      disconnectSSE();
      f1LiveData.cleanup(); // Cleanup the f1LiveData store
    };
  });
    
</script>

<div class="flex justify-end mx-auto px-4 py-2 sm:px-4 lg:px-4 bg-gray-200">
  <SseStatus />
</div>
<RaceMessageUpdates />

<div class="mx-auto max-w-7xl px-4 py-4 sm:px-4 lg:px-4 bg-gray-300">
  <Tabs tabStyle="underline">
    <TabItem open title="Race">

    </TabItem>
    <TabItem title="Timing tower">
      
    </TabItem>
    <TabItem title="Race control">
      <div class="grid grid-cols-4 gap-2">
        <div class="col-span-3 bg-gray-400">
          <RaceMessages />
        </div>
        <div class="grid grid-flow-col grid-rows-4 gap-4">
          <div class="bg-gray-400">
            <p>"Status"</p>
          </div>
          <div class="bg-gray-400">
            <p>"Clock"</p>
          </div>
          <div class="row-span-2 bg-gray-400">
            <p>"Weather"</p>
          </div>          
        </div>
      </div>
    </TabItem>
    <TabItem title="Livetiming messages">
      <LivetimingMessages />
    </TabItem>
  </Tabs>

    

</div>
