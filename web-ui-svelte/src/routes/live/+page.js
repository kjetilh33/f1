import { subscribeSSE, connectSSE } from "./sse-client";

export function load() {

    return {
        subscribeSSE: subscribeSSE,
        sseStore: connectSSE("http://api.kinnovatio.local/f1/api/v1/live")    
    };
    
}