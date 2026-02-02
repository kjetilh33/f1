<script>
import { Table } from "flowbite-svelte";
import { subscribeSSE, sseStore, connectSSE, disconnectSSE } from "./sse-client.svelte";
import { onMount } from 'svelte';
import SseStatus from "./sse-status.svelte";

let { data } = $props();

let items = [
    { id: 1, maker: "Toyota", type: "ABC", make: 2017 },
    { id: 2, maker: "Ford", type: "CDE", make: 2018 },
    { id: 3, maker: "Volvo", type: "FGH", make: 2019 },
    { id: 4, maker: "Saab", type: "IJK", make: 2020 }
  ];

  onMount(() => {
    // EventSource is a browser API and runs only on the client
    connectSSE("/../api/v1/live"); 
    
    // Cleanup function for when the component is destroyed
    return () => {
      disconnectSSE();
    };
  });
    
</script>

<div class="mx-auto max-w-7xl px-4 py-6 sm:px-6 lg:px-8">

    <SseStatus />

    <Table items={sseStore.messages} hoverable={true}></Table>

</div>
