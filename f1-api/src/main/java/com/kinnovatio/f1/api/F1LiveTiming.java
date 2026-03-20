package com.kinnovatio.f1.api;

import io.quarkus.runtime.StartupEvent;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.smallrye.mutiny.Multi;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.*;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.ResponseHeader;
import org.jboss.resteasy.reactive.NoCache;

import java.time.Duration;

@ApplicationScoped
@Path("/live/livetiming")
public class F1LiveTiming {
    private static final Logger LOG = Logger.getLogger(F1LiveTiming.class);

    @Inject
    @Channel("f1-live-processed")
    Multi<String> LiveTimingMessage;

    @Inject
    Sse sse;

    @ConfigProperty(name = "app.log.source")
    String logSource;

    /// Initializes the processor on startup.
    /// This method is triggered by the `StartupEvent`. It logs the startup configuration
    /// and ensures that the necessary database table exists.
    ///
    /// @param ev The startup event.
    public void onStartup(@Observes StartupEvent ev) {
        LOG.infof("Starting the live timing api...");
        LOG.infof("Config picked up from %s", logSource);

        LOG.infof("The api is ready. Waiting for live timing messages...");
    }

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
    public Multi<OutboundSseEvent> getLiveTimingStream() {
        //TODO: add filter for messages (i.e. team radio which needs to be transcribed first).
        
        return Multi.createBy().merging()
                .streams(LiveTimingMessage, emitAPeriodicPing())
                .map(message -> sse.newEventBuilder()
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
