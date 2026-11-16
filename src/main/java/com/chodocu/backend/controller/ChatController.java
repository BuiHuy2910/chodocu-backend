package com.chodocu.backend.controller;

import com.chodocu.backend.dto.ApiResponse;
import com.chodocu.backend.dto.chat.ChatMessageRequest;
import com.chodocu.backend.dto.chat.ChatMessageResponse;
import com.chodocu.backend.dto.chat.ConversationResponse;
import com.chodocu.backend.exception.BadRequestException;
import com.chodocu.backend.exception.ResourceNotFoundException;
import com.chodocu.backend.security.CustomUserDetails;
import com.chodocu.backend.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/send")
    public ResponseEntity<ApiResponse<ChatMessageResponse>> sendMessage(
            @Valid @RequestBody ChatMessageRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        ChatMessageResponse sent = chatService.sendMessage(request, currentUser.getEmail());
        return ResponseEntity.ok(ApiResponse.ok("Đã gửi tin nhắn", sent));
    }

    /** Hộp thư: danh sách hội thoại kèm số tin chưa đọc. */
    @GetMapping("/conversations")
    public ResponseEntity<ApiResponse<List<ConversationResponse>>> getConversations(
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Danh sách hội thoại", chatService.getConversationsList(currentUser.getEmail())));
    }

    @GetMapping("/messages/{partnerId}")
    public ResponseEntity<ApiResponse<List<ChatMessageResponse>>> getMessages(
            @PathVariable Long partnerId,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Lịch sử trò chuyện", chatService.getConversation(currentUser.getEmail(), partnerId)));
    }

    /** Mở hội thoại -> đánh dấu đã đọc tin của người kia. */
    @PutMapping("/conversations/{partnerId}/read")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> markConversationRead(
            @PathVariable Long partnerId,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        int updated = chatService.markConversationRead(currentUser.getEmail(), partnerId);
        return ResponseEntity.ok(ApiResponse.ok("Đã đánh dấu đã đọc", Map.of("updated", updated)));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getUnreadCount(
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        long count = chatService.getUnreadCount(currentUser.getEmail());
        return ResponseEntity.ok(ApiResponse.ok("Số tin nhắn chưa đọc", Map.of("count", count)));
    }

    // ---------------- WebSocket (STOMP) ----------------

    /** App gửi tin qua WebSocket tới "/app/chat.send" (người gửi lấy từ token lúc CONNECT). */
    @MessageMapping("/chat.send")
    public void handleWebSocketMessage(@Payload ChatMessageRequest request, Principal principal) {
        chatService.sendMessage(request, principal.getName());
    }

    /** Gửi tin qua WebSocket bị lỗi -> báo riêng cho người gửi tại "/user/queue/errors". */
    @MessageExceptionHandler
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public Map<String, String> handleWebSocketError(Exception ex) {
        boolean isExpected = ex instanceof BadRequestException || ex instanceof ResourceNotFoundException;
        return Map.of("message", isExpected ? ex.getMessage() : "Không gửi được tin nhắn, vui lòng thử lại");
    }
}
