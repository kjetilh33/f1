<script>
    import { sseStore } from "./sse-client.svelte";
    import { Table } from "flowbite-svelte";  

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

    let tableData = $derived(sseStore.messages.map(element => (
    {
        //id: messageIndex++,
        timeStamp: formatter.format(element.timestamp),
        category: element.category,
        message: JSON.stringify(element.message),
        isStreaming: element.isStreaming
    }))
    );

</script>

<Table items={tableData} hoverable={true}></Table>