package com.chodocu.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Cấu hình chat realtime (STOMP over WebSocket).
 *
 * - App kết nối "/ws-raw" (WebSocket thuần), web dùng "/ws" (SockJS).
 * - Frame CONNECT phải kèm header "Authorization: Bearer <token>" (xem WebSocketAuthInterceptor).
 * - App đăng ký "/user/queue/messages" và "/user/queue/read" để nhận tin riêng của mình.
 * - App gửi tin tới "/app/chat.send".
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor authInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
        registry.addEndpoint("/ws-raw")
                .setAllowedOriginPatterns("*");
    }

    /** Kiểm tra token ở mọi frame client gửi lên (thực tế chỉ xử lý frame CONNECT). */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }
}
