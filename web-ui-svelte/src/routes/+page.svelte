<script>
  import { Alert } from "flowbite-svelte";
  import { InfoCircleSolid } from "flowbite-svelte-icons";
</script>

<div class="mx-auto max-w-7xl px-4 py-4 sm:px-4 lg:px-4 bg-gray-300">
  <h1>Welcome!</h1>
  <p>...</p>

  <div class="p-8">
    <Alert dismissable>
      {#snippet icon()}<InfoCircleSolid class="h-5 w-5" />{/snippet}
      <span class="font-medium">Info alert!</span>
      Hi there! This is an info alert.
    </Alert>
  </div>
</div>

