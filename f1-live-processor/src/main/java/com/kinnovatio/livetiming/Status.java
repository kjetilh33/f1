package com.kinnovatio.livetiming;

import io.smallrye.common.annotation.RunOnVirtualThread;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.jboss.resteasy.reactive.NoCache;
import org.jboss.resteasy.reactive.ResponseHeader;

import java.time.Duration;


/// REST endpoint for streaming status updates via Server-Sent Events (SSE).
/// This class exposes a `/status` endpoint that streams live updates from the
/// `status-out` channel to connected clients. It also sends a periodic ping
/// to keep the connection alive.
@ApplicationScoped
@Path("/status")
public class Status {

    @Inject
    @Channel("status-out")
    Multi<String> statusMessages;

    @Inject
    Sse sse;

    /// Streams status updates to clients using Server-Sent Events (SSE).
    /// This method merges the `statusMessages` stream with a periodic ping stream.
    /// Each message is wrapped in an `OutboundSseEvent` with the name "status".
    ///
    /// @return A `Multi` stream of `OutboundSseEvent` objects.
    @GET
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @NoCache
    @ResponseHeader(name = "X-Accel-Buffering", value = "no")
    @RunOnVirtualThread
    public Multi<OutboundSseEvent> getStatus() {
        return Multi.createBy().merging()
                        .streams(statusMessages, emitAPeriodicPing())
                        .map(message -> sse.newEventBuilder()
                                .name("status")
                                .data(message)
                                .build());
    }

    /// Creates a stream that emits a periodic ping message.
    /// The ping message is an empty JSON object `{}` emitted every 10 seconds.
    /// This helps to keep the SSE connection alive and detect disconnected clients.
    ///
    /// @return A `Multi` stream emitting a ping string every 10 seconds.
    Multi<String> emitAPeriodicPing() {
        return Multi.createFrom().ticks().every(Duration.ofSeconds(10))
                .onItem().transform(x -> "{}");
    }

}
