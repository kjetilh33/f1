
/**
 * @param {number} timestamp
 */
export function parseNanoTimestamp(timestamp) {
    return new Date(Math.floor(timestamp * 1000))
}