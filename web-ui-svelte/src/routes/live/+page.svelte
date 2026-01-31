<script>
import { Table } from "flowbite-svelte";

let { data } = $props();

let items = [
    { id: 1, maker: "Toyota", type: "ABC", make: 2017 },
    { id: 2, maker: "Ford", type: "CDE", make: 2018 },
    { id: 3, maker: "Volvo", type: "FGH", make: 2019 },
    { id: 4, maker: "Saab", type: "IJK", make: 2020 }
  ];
    
</script>
<div class="mx-auto max-w-7xl px-4 py-6 sm:px-6 lg:px-8">

    <h3>SSE connection status: {data.sseStore.status}</h3>

    <Table {items} hoverable={true}></Table>

</div>
