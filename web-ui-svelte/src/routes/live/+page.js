import { subscribeSSE, connectSSE } from "./sse-client";

export function load() {

    return {
        subscribeSSE: subscribeSSE,
        sseStore: connectSSE("/../api/v1/live")    
    };
    
}
