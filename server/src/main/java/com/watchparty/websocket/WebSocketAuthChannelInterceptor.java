package com.watchparty.websocket;

import com.watchparty.service.JwtService;
import io.jsonwebtoken.JwtException;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Extracts the JWT Bearer token from STOMP CONNECT headers and stores
 * the authenticated user's ID in the WebSocket session attributes.
 */
@Component
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    static final String USER_ID_ATTR = "userId";

    private final JwtService jwtService;

    public WebSocketAuthChannelInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        var accessor = StompHeaderAccessor.wrap(message);
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            var authHeader = accessor.getFirstNativeHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                try {
                    var claims = jwtService.parseToken(authHeader.substring(7));
                    if (jwtService.isAccessToken(claims)) {
                        var sessionAttrs = accessor.getSessionAttributes();
                        if (sessionAttrs != null) {
                            sessionAttrs.put(USER_ID_ATTR, UUID.fromString(claims.getSubject()));
                        }
                    }
                } catch (JwtException ignored) {
                    // Unauthenticated – proceed as anonymous
                }
            }
        }
        return message;
    }
}
