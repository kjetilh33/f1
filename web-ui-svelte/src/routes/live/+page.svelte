<script>
  import { Table } from "flowbite-svelte";
  import { subscribeSSE, sseStore, connectSSE, disconnectSSE } from "./sse-client.svelte";
  import { onMount } from 'svelte';
  import SseStatus from "./sse-status.svelte";

  let { data } = $props();

  onMount(() => {
    // EventSource is a browser API and runs only on the client
    connectSSE("/../api/v1/live"); 
    
    // Cleanup function for when the component is destroyed
    return () => {
      disconnectSSE();
    };
  });
    
</script>
<div class="flex justify-end mx-auto px-4 py-6 sm:px-6 lg:px-8">
  <SseStatus />
</div>

<div class="mx-auto max-w-7xl px-4 py-6 sm:px-6 lg:px-8">

    <Table items={sseStore.messages} hoverable={true}></Table>

</div>
