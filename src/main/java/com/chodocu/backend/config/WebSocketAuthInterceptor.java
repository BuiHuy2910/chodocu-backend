package com.chodocu.backend.config;

import com.chodocu.backend.security.CustomUserDetailsService;
import com.chodocu.backend.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/**
 * Xác thực kết nối WebSocket.
 *
 * App gửi header "Authorization: Bearer <token>" trong frame STOMP CONNECT.
 * Token hợp lệ -> gắn người dùng vào phiên WebSocket (tên người dùng = email),
 * nhờ đó server gửi tin riêng cho từng người bằng convertAndSendToUser(email, ...).
 * Token sai / thiếu -> từ chối kết nối.
 */
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;
    private final CustomUserDetailsService userDetailsService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            accessor.setUser(authenticate(readAuthorizationHeader(accessor)));
        }
        return message;
    }

    private String readAuthorizationHeader(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        return header != null ? header : accessor.getFirstNativeHeader("authorization");
    }

    private Authentication authenticate(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw new AccessDeniedException("Thiếu token đăng nhập");
        }
        String token = authorizationHeader.substring(BEARER_PREFIX.length());
        if (!tokenProvider.validateToken(token)) {
            throw new AccessDeniedException("Phiên đăng nhập không hợp lệ hoặc đã hết hạn");
        }

        UserDetails user = userDetailsService.loadUserByUsername(tokenProvider.getEmailFromToken(token));
        if (!user.isEnabled()) {
            throw new AccessDeniedException("Tài khoản đã bị khóa");
        }
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }
}
