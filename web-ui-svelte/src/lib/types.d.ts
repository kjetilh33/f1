interface LiveTimingRecord {
	category: string;
	message: Record<string, any>;
	timestamp: Date;
	isStreaming: boolean;
}

interface RaceMessageRecord {
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

interface DriverRecord {
	timestamp?: Date;
	tla: string;
	line?: string | number;
	fullName: string;
	lastName: string;
	firstName: string;
	teamName: string;
	reference?: string;
	teamColour: string;
	headshotUrl?: string;
	racingNumber: string;
	broadcastName?: string;
	publicIdRight?: string;
}

interface StintRecord {
	LapTime?: string;
	LapNumber?: number;
	LapFlags?: number;
	Compound?: string;
	New?: string | boolean;
	TyresNotChanged?: string | number;
	TotalLaps?: number;
	StartLaps?: number;
}

interface ClockRecord {
	utc: Date;
	remaining: string;
	extrapolating: boolean;
}

interface LeaderboardDriver {
	racingNumber: string;
	tla: string;
	name: string;
	teamName: string;
	teamColour: string;
	position: number;
	gapToLeader: string;
	intervalToAhead: string;
	lastLapTime: string;
	bestLapTime: string;
	sectors: Record<string, any>[];
	inPit: boolean;
	pitCount: number;
	tyreCompound: string;
	isNewTyre: boolean;
	tyreAge: number;
}
