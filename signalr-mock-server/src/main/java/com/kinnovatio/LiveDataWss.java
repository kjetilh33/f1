package com.kinnovatio;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import org.jboss.logging.Logger;

@ServerEndpoint("/signalr/connect")
@ApplicationScoped
public class LiveDataWss {
    private final Logger LOG = Logger.getLogger(this.getClass());
    private final String initMessage = """
            {"C":"adf-some-id","S":1,"M":[]}
            """;

    private final String streamSubscribe = "Subscribe";

    private final Map<Session, LiveDataFeed> sessions = new ConcurrentHashMap<>();

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
        if (message.contains(streamSubscribe) && !sessions.containsKey(session)) {
            LiveDataFeed feed = new LiveDataFeed(session);
            sessions.put(session, feed);
            feed.start();
        }
    }

    @OnClose
    public void onClose(Session session) {
        if (sessions.containsKey(session)) {
            sessions.get(session).close();
            sessions.remove(session);

            LOG.info("Closing session: {}", session.getId());
        }
    }
}
