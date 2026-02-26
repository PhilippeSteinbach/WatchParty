package com.watchparty.websocket;

import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class WebSocketEventListener implements ApplicationListener<SessionDisconnectEvent> {

    private final WatchPartyWebSocketHandler handler;

    public WebSocketEventListener(WatchPartyWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void onApplicationEvent(@org.springframework.lang.NonNull SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        handler.handleParticipantLeave(sessionId);
    }
}
