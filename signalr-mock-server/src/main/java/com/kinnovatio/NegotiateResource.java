package com.kinnovatio;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.RestResponse;

@Path("/signalr/negotiate")
public class NegotiateResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public RestResponse<String> negotiate() {
        return RestResponse.ResponseBuilder.ok(
                """
                        {
                            "Url": "/signalr",
                            "ConnectionToken": "blahblah",
                            "ConnectionId": "759a9c8d-047b-4c6a-bca0-ad234b2840a5",
                            "KeepAliveTimeout": 20.0,
                            "DisconnectTimeout": 30.0,
                            "ConnectionTimeout": 110.0,
                            "TryWebSockets": true,
                            "ProtocolVersion": "1.5",
                            "TransportConnectTimeout": 10.0,
                            "LongPollDelay": 1.0
                        }
                        """
        )
                .header("Set-Cookie", "my-cookie")
                .build();
    }
}
