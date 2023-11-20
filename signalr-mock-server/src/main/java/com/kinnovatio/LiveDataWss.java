package com.kinnovatio;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import org.jboss.logging.Logger;
import org.jboss.logmanager.LogManager;

@ServerEndpoint("/signalr/connect")
@ApplicationScoped
public class LiveDataWss {
    private final Logger LOG = Logger.getLogger(this.getClass());
    private final String initMessage = """
            {"C":"adf-some-id","S":1,"M":[]}
            """;

    @OnOpen
    public void onOpen(Session session) {
        session.getAsyncRemote().sendText(initMessage, result -> {
                    if (result.getException() != null) {
                        LOG.warn("Unable to send message: {}", result.getException());
                    }
                });
    }

    @OnMessage
    public void onMessage(Session session, String message) {

    }
}
