API notes

SignalR protocol:
- https://blog.3d-logic.com/2015/03/29/signalr-on-the-wire-an-informal-description-of-the-signalr-protocol/

Decoding messages:
- https://openf1.org/#api-methods
- https://biggo.com/news/202504191313_F1_Fans_Embrace_Open_Source_Live_Timing_Tool
- https://github.com/theOehrly/Fast-F1/blob/master/fastf1/livetiming/client.py


Negotiating a connection
To negotiate a connection, you do a GET request to the signalr endpoint with an appended /negotiate path. For the f1 signalr endpoint this looks like this:

https://livetiming.formula1.com/signalr/negotiate?connectionData=%5B%7B%22name%22%3A%22Streaming%22%7D%5D&clientProtocol=1.5
clientProtocol is hardcoded to 1.5.

connectionData is a urlencoded json object of the form:
```
[{"name": "Streaming"}]
```
where `“Streaming”` is the name of the hub we want to connect to. Currently only the streaming hub is known.

This returns a response with a bunch of data, like KeepAliveTimeout and LongPollDelay, but the only body value we’re interested in is the ConnectionToken.

The headers contain a cookie we have to use to connect to the hub, so grab the Set-Cookie header value as well.

An pseudo code example looks as follows:
```
function negotiate() {
	const hub = encodeURIComponent(JSON.stringify([{name:"Streaming"}]));
	const url = `https://livetiming.formula1.com/signalr/negotiate?connectionData=${hub}&clientProtocol=1.5`
	const resp = get(url);
	return resp;
}

{
  Url: '/signalr',
  ConnectionToken: 'nK9Qb1XANYP2FkmdEVHxb4olwu22b6TJRqE+o3p/vqi/BxkJx9PWDQkNhmRK9hNX5yRxwN0MpJL1N7tPc6aqC4nHkVveXmJYHEhCLlm4IK5VPpPIGG423nPJkb0sSOXX',
  ConnectionId: '55ac0b16-cc69-4aa4-b1e8-9602b6b29a37',
  KeepAliveTimeout: 20,
  DisconnectTimeout: 30,
  ConnectionTimeout: 110,
  TryWebSockets: true,
  ProtocolVersion: '1.5',
  TransportConnectTimeout: 10,
  LongPollDelay: 1
}
{
  'transfer-encoding': 'chunked',
  'content-type': 'application/json; charset=UTF-8',
  server: 'Microsoft-HTTPAPI/2.0',
  'x-server': 'streamrepeater-live-zxx4',
  'x-content-type-options': 'nosniff',
  date: 'Mon, 25 Jul 2022 10:09:38 GMT',
  via: '1.1 google',
  'set-cookie': [ 'GCLB=CKf_q6yD58XUUg; path=/; HttpOnly' ],
  'alt-svc': 'h3=":443"; ma=2592000,h3-29=":443"; ma=2592000',
  connection: 'close'
}
Websocket connection
Connecting
```
Once you have the data from the negotiation, you  need to build a websocket connection to the server. This happens over wss, The url is as follows:

```
wss://livetiming.formula1.com/signalr/connect?clientProtocol=1.5&transport=webSockets&connectionToken=<sometoken>&connectionData=%5B%7B%22name%22%3A%22Streaming%22%7D%5D
```

Where clientProtocol again is always 1.5, connectionData is again the json stringified hub to connect to, and connectionToken is the urlencoded connection token you got from the negotiation. In addition, you’ll have to supply the following headers:

```
User-Agent: BestHTTP
Accept-Encoding: gzip,identity
Cookie: <cookie from negotiation>
```
>NOTE: The headers are case sensitive for some reason, and the server will 500 if you pass in the wrong case. It’ll 400 if some required header is missing.

```
received {"C":"d-DB2F4380-B,0|FlOl,0|FlOm,1","S":1,"M":[]}
received {}
Invoking methods
```
If all went well, you should have a websocket connection with the signalr endpoint at this point, what’s left is to invoke the Subscribe method with the data you want to receive. This is done by sending a json message over the websocket connection with the following body:

```
{
	"H": "Streaming",
	"M": "Subscribe",
	"A": [["TimingData", "Heartbeat"]],
	"I": 1
}
```
>NOTE: The “A” field really is an array of array of string.

The structure is as follows:
```
{
	H: The hub to invoke the method on
	M: The method to invoke
	A: The arguments to pass to the method
	I: Client side id for the request/response
}
```
For the f1 endpoint, hub is always Streaming, the method is always: Subscribe.

For the subscribe method the following datastreams are available:

- `"Heartbeat"`
- `"CarData.z"`
- `"Position.z"`
- `"ExtrapolatedClock"`
- `"TopThree"`
- `"RcmSeries"`
- `"TimingStats"`
- `"TimingAppData"`
- `"WeatherData"`
- `"TrackStatus"`
- `"DriverList"`
- `"RaceControlMessages"`
- `"SessionInfo"`
- `"SessionData"`
- `"LapCount"`
- `"TimingData"`

After invoking the method, you should be seeing data coming back if there’s a session going 

## SignalR

### Messages from server
The properties you can find in the message are as follows:

- C – message id, present for all non-KeepAlive messages. 
- M – an array containing actual data.

`{"C":"d-9B7A6976-B,2|C,2","M":["Welcome!"]}`

- S – indicates that the transport was initialized (a.k.a. init message)

```
{"C":"s-0,2CDDE7A|1,23ADE88|2,297B01B|3,3997404|4,33239B5","S":1,"M":[]}
```

- G – groups token – an encrypted string representing group membership

```
{"C":"d-6CD4082D-B,0|C,2|D,0","G":"92OXaCStiSZGy5K83cEEt8aR2ocER=","M":[]}
```
### KeepAlive messages
KeepAlive messages are empty object JSon strings (i.e. `{}`) and can be used by SignalR clients to detect network problems. SignalR server will send keep alive messages at the configured time interval. If the client has not received any message (including a keep alive message) from the server within a certain period of time it will try to restart the connection.
Sending keep alive messages by the server can be turned off by setting the KeepAlive server configuration property to null.

### Hubs messages
Hubs API makes it possible to invoke server methods from the client and client methods from the server.
When a client invokes a server method it no longer sends a free-flow string as it was for persistent connections. Instead it sends a JSon string containing all necessary information needed to invoke the method. Here is a sample message a client would send to invoke a server method:

```Json
{"H":"chathub","M":"Send","A":["JS Client","Test message"],"I":0, "S":{"customProperty" : "abc"}}
```

The payload has the following properties:
I – invocation identifier – allows to match up responses with requests
H – the name of the hub
M – the name of the method
A – arguments (an array, can be empty if the method does not have any parameters)
S – state – a dictionary containing additional custom data (optional, currently not supported by the C++ client)

The message sent from the server to the client can be one of the following:
- a result of a server method call
- an invocation of a client method
- a progress message

Here are sample results of a server method call:
`{"I":"0"}`
A server void method whose invocation identifier was "0" completed successfully.

`{"I":"0", "R":42}`
A server method returning a number whose invocation identifier was "0" completed successfully and returned the value 42.

`{"I":"0", "E":"Error occurred"}`
A server method whose invocation identifier was "0" failed with the error "Error occurred"

`{"I":"0","E":"Hub error occurred", "H":true, "D":{"ErrorNumber":42}}`
A server method whose invocation identifier was "0" failed with the hub error "Hub error occurred" and sent some additional error data.

Here is the full list of properties that can be present in the result of server method invocation:

I – invocation Id (always present)
R – the value returned by the server method (present if the method is not void)
E – error message
H – true if this is a hub error
D – an object containing additional error data (can only be present for hub errors)
T – stack trace (if detailed error reporting (i.e. the HubConfiguration.EnableDetailedErrors property) is turned on on the server).