<script>
  import { Alert } from "flowbite-svelte";
  import { InfoCircleSolid } from "flowbite-svelte-icons";
</script>


<h1>Welcome!</h1>
<p>...</p>

<div class="p-8">
  <Alert dismissable>
    {#snippet icon()}<InfoCircleSolid class="h-5 w-5" />{/snippet}
    <span class="font-medium">Info alert!</span>
    Change a few things up and try submitting again.
  </Alert>
</div>

