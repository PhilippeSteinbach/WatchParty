package com.watchparty.websocket;

import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * Handles WebSocket session disconnect events.
 * This is the single point of disconnect handling — the @EventListener
 * in WatchPartyWebSocketHandler has been removed to avoid duplicate processing.
 */
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
