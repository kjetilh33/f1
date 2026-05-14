<script>
    import { page } from '$app/state';

    console.log(page.data);
</script>

<div>
    { JSON.stringify(page.data.sessionData) }
</div>
