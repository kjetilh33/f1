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
        color: "Grey",
        comment: "Unknown"
    },
    16: {
        label: "Pit lane",
        color: "DarkGrey",
        comment: "Went through this mini sector in the pit lane."
    },
    32: {
        label: "Pit exit",
        color: "DarkGrey",
        comment: "Recently exited the pit lane."
    },
    64: {
        label: "Racing",
        color: "Black",
        comment: "Indicates 'normal' in races. Most common status. Only turned off when the driver has retired."
    },
    128: {
        label: "Unknown",
        color: "Grey",
        comment: "Unknown"
    },
    256: {
        label: "Unknown",
        color: "Grey",
        comment: "Unknown"
    },
    512: {
        label: "Out lap",
        color: "DarkGrey",
        comment: "Unknown"
    },
    1024: {
        label: "Checkered flag",
        color: "DarkBlue",
        comment: "The driver passes the checkered flag."
    },
    2048: {
        label: "Segment completed",
        color: "Gold",
        comment: "Segment completed. If this is the only status, then a yellow segment."
    },
    4096: {
        label: "Overtook",
        color: "DarkGreen",
        comment: "The driver has recently overtaken another driver."
    },
    8192: {
        label: "Overtaken",
        color: "DarkRed",
        comment: "The driver has recently been overtaken by another driver."
    }
}