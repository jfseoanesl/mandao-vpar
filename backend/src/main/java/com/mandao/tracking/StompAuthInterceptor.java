package com.mandao.tracking;

import com.mandao.orders.ErrandAccess;
import com.mandao.shared.ApiException;
import com.mandao.shared.AuthUser;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import com.mandao.identity.TermsAcceptanceFilter;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Autentica el CONNECT con el JWT y autoriza cada SUBSCRIBE (AC-006-03).
 * El nombre del Principal es el id del usuario, lo que permite /user/{id}/queue/...
 */
@Component
public class StompAuthInterceptor implements ChannelInterceptor {

    private static final Pattern ERRAND_TOPIC = Pattern.compile("^/topic/errands/([0-9a-fA-F-]{36})$");

    private final JwtDecoder jwtDecoder;
    private final ErrandAccess access;

    public StompAuthInterceptor(JwtDecoder jwtDecoder, @Lazy ErrandAccess access) {
        this.jwtDecoder = jwtDecoder;
        this.access = access;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        switch (accessor.getCommand()) {
            case CONNECT -> accessor.setUser(authenticate(accessor));
            case SUBSCRIBE -> authorizeSubscription(accessor);
            case SEND -> throw new MessageDeliveryException("Envío por WebSocket no permitido; usa la API REST");
            default -> {
            }
        }
        return message;
    }

    private JwtAuthenticationToken authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new MessageDeliveryException("Token requerido");
        }
        try {
            Jwt jwt = jwtDecoder.decode(header.substring(7));
            if (TermsAcceptanceFilter.isPending(jwt)) {
                throw new MessageDeliveryException("Debes aceptar los términos vigentes");
            }
            return new JwtAuthenticationToken(jwt,
                    List.of(new SimpleGrantedAuthority("ROLE_" + jwt.getClaimAsString("role"))), jwt.getSubject());
        } catch (JwtException e) {
            throw new MessageDeliveryException("Token inválido");
        }
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        if (!(accessor.getUser() instanceof JwtAuthenticationToken token)) {
            throw new MessageDeliveryException("No autenticado");
        }
        AuthUser user = AuthUser.fromJwt(token.getToken());
        String destination = accessor.getDestination();
        if (destination == null) {
            throw new MessageDeliveryException("Destino requerido");
        }
        if (destination.equals("/topic/admin")) {
            if (!user.isAdmin()) {
                throw new MessageDeliveryException("No autorizado");
            }
            return;
        }
        if (destination.startsWith("/user/queue/")) {
            return;
        }
        Matcher m = ERRAND_TOPIC.matcher(destination);
        if (m.matches()) {
            try {
                access.resolve(UUID.fromString(m.group(1)), user, true);
                return;
            } catch (ApiException e) {
                throw new MessageDeliveryException("No autorizado");
            }
        }
        throw new MessageDeliveryException("Destino no permitido");
    }
}
