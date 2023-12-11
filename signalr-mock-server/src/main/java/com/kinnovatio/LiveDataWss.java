package com.kinnovatio;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.websocket.*;
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
        LOG.infof("Opening wss session for: %s", session.toString());
        session.getAsyncRemote().sendText(initMessage, result -> {
                    if (result.getException() != null) {
                        LOG.warnf("Unable to send message: %s", result.getException());
                    }
                });
    }

    @OnMessage
    public void onMessage(Session session, String message) {
        LOG.debugf("onMessage() called with message: %s%n and session: %s", message, session.toString());
        if (message.contains(streamSubscribe) && !sessions.containsKey(session)) {
            LOG.infof("Start subscription for live feed for session: %s", session.toString());
            LiveDataFeed feed = new LiveDataFeed(session);
            sessions.put(session, feed);
            feed.start();
        }
    }

    @OnClose
    public void onClose(Session session) {
        LOG.infof("Closing wss session for: %s", session.toString());
        if (sessions.containsKey(session)) {
            sessions.get(session).close();
            sessions.remove(session);

            LOG.infof("Closing live feed for session: %s", session.getId());
        }
    }

    @OnError
    public void onError(Session session, Throwable e) {
        LOG.warnf("Error for session: %s%n Error: %s", session.toString(), e.toString());
        if (sessions.containsKey(session)) {
            sessions.get(session).close();
            sessions.remove(session);

            LOG.infof("Closing live feed for session: %s", session.getId());
        }
    }
}
