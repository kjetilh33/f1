
/** @type {import('./$types').PageLoad} */
export async function load({ fetch }) {
    const sessionRes = await fetch(`/../api/v1/live`);
    const sessionStatus = await sessionRes.json();

    const sessionInfoRes = await fetch(`/../api/v1/live/session-info`);
    const sessionInfo = await sessionInfoRes.json();
    
    return { sessionStatus, sessionInfo };
}