/**
 * @typedef {Object} TimingStatusDictionaryItem
 * @property {String} label
 * @property {String} color
 * @property {string} comment
 */

/**
 * @constant
 * @type {Record<number, TimingStatusDictionaryItem>}
 */
export const TIMING_STATUS_DICTIONARY = {
    1: {
        label: "Personal best",
        color: "Green",
        comment: "Personal best sector time (green). Only used for sector status."
    },
    2: {
        label: "Overall best",
        color: "Purple",
        comment: "Overall best sector time (purple). Only used for sector status."
    },
    4: {
        label: "Off track",
        color: "DarkRed",
        comment: "Driver has stopped somewhere on/off track. Typically a precursor to retirement."
    },
    8: {
        label: "Unknown",
        color: "DarkGrey",
        comment: "Unknown"
    }
}