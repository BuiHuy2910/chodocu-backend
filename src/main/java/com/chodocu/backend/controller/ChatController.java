package com.chodocu.backend.controller;

import com.chodocu.backend.dto.ApiResponse;
import com.chodocu.backend.dto.chat.ChatMessageRequest;
import com.chodocu.backend.dto.chat.ChatMessageResponse;
import com.chodocu.backend.dto.chat.ConversationResponse;
import com.chodocu.backend.security.CustomUserDetails;
import com.chodocu.backend.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

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

    @MessageMapping("/chat.send")
    public void handleWebSocketMessage(@Payload ChatMessageRequest request, Principal principal) {
        if (principal != null) {
            chatService.sendMessage(request, principal.getName());
        }
    }
}
