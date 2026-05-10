/** @type {import('./$types').PageLoad} */
export async function load({ fetch }) {
    const res = await fetch(`/../api/v1/live`);
    const item = res.json();
    console.log(item);
    
    return { item };
}