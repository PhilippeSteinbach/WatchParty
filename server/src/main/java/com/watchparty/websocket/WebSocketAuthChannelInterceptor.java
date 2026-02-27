package com.watchparty.websocket;

import com.watchparty.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Extracts the JWT Bearer token from STOMP CONNECT headers and stores
 * the authenticated user's ID in the WebSocket session attributes.
 * Unauthenticated connections are allowed as guests with limited permissions.
 */
@Component
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketAuthChannelInterceptor.class);

    static final String USER_ID_ATTR = "userId";

    private final JwtService jwtService;

    public WebSocketAuthChannelInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                try {
                    Claims claims = jwtService.parseToken(authHeader.substring(7));
                    if (jwtService.isAccessToken(claims)) {
                        Map<String, Object> sessionAttrs = accessor.getSessionAttributes();
                        if (sessionAttrs != null) {
                            sessionAttrs.put(USER_ID_ATTR, UUID.fromString(claims.getSubject()));
                        }
                    } else {
                        log.warn("WebSocket CONNECT with non-access token type from session {}",
                                accessor.getSessionId());
                    }
                } catch (JwtException e) {
                    log.warn("WebSocket CONNECT with invalid JWT from session {}: {}",
                            accessor.getSessionId(), e.getMessage());
                }
            }
            // No token or invalid token: proceed as anonymous guest
        }
        return message;
    }
}
