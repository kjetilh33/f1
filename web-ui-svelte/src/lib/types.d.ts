interface LiveTimingRecord {
    category: string;
    message: Record<string, any>;
    timestamp: Date;
    isStreaming: boolean;
}

interface RaceMessageRecord {
    //id: number;
    timestamp: Date;
    category: string;
    message: string;
    lap?: string;
    flag?: string;
    scope?: string;
    sector?: number;
    mode?: string;
    status?: string;
}

interface TrackStatusRecord {
    timestamp: Date;
    status: string;
    message: string;
}

interface WeatherRecord {
    timestamp: Date;
    airTemp: string;
    humidity: string;
    pressure: string;
    rainfall: string;
    trackTemp: string;
    windSpeed: string;
    windDirection: string;            
}
