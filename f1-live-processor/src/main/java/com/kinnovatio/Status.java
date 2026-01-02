package com.kinnovatio;

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

import java.awt.print.Book;


@ApplicationScoped
@Path("/status")
public class Status {

    @Inject
    @Channel("status-out")
    Multi<String> statusMessages;

    @Inject
    Sse sse;

    @GET
    @RunOnVirtualThread
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public Multi<OutboundSseEvent> getStatus() {
        return statusMessages.map(message -> sse.newEventBuilder()
                .name("status")
                .data(message)
                .build());
    }
}
