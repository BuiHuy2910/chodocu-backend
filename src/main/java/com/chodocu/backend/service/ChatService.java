package com.chodocu.backend.service;

import com.chodocu.backend.dto.chat.ChatMessageRequest;
import com.chodocu.backend.dto.chat.ChatMessageResponse;
import com.chodocu.backend.dto.chat.ConversationResponse;
import com.chodocu.backend.entity.ChatMessage;
import com.chodocu.backend.entity.Product;
import com.chodocu.backend.entity.User;
import com.chodocu.backend.exception.ResourceNotFoundException;
import com.chodocu.backend.repository.ChatMessageRepository;
import com.chodocu.backend.repository.ProductRepository;
import com.chodocu.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final DtoMapper dtoMapper;

    @Transactional
    public ChatMessageResponse sendMessage(ChatMessageRequest request, String senderEmail) {
        User sender = userRepository.findByEmail(senderEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người gửi"));
        User receiver = userRepository.findById(request.getReceiverId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người nhận"));

        Product product = null;
        if (request.getProductId() != null) {
            product = productRepository.findById(request.getProductId()).orElse(null);
        }

        ChatMessage message = ChatMessage.builder()
                .sender(sender)
                .receiver(receiver)
                .product(product)
                .content(request.getContent().trim())
                .messageType(request.getMessageType() != null ? request.getMessageType() : "TEXT")
                .offeredPrice(request.getOfferedPrice())
                .meetupLocation(request.getMeetupLocation())
                .meetupTime(request.getMeetupTime())
                .isRead(false)
                .build();

        ChatMessage saved = chatMessageRepository.save(message);
        ChatMessageResponse response = dtoMapper.toChatMessageResponse(saved);

        // Broadcast qua WebSocket cho cả người nhận và người gửi
        messagingTemplate.convertAndSend("/topic/messages/" + receiver.getId(), response);
        messagingTemplate.convertAndSend("/topic/messages/" + sender.getId(), response);

        return response;
    }

    public List<ChatMessageResponse> getConversation(String currentUserEmail, Long partnerId) {
        User currentUser = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
        return chatMessageRepository.findConversation(currentUser.getId(), partnerId).stream()
                .map(dtoMapper::toChatMessageResponse)
                .collect(Collectors.toList());
    }

    public List<ConversationResponse> getConversationsList(String currentUserEmail) {
        User currentUser = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));

        List<ChatMessage> allMessages = chatMessageRepository.findAllMessagesForUser(currentUser.getId());
        Map<Long, ConversationResponse> conversationMap = new LinkedHashMap<>();

        for (ChatMessage msg : allMessages) {
            User partner = msg.getSender().getId().equals(currentUser.getId()) ? msg.getReceiver() : msg.getSender();
            if (!conversationMap.containsKey(partner.getId())) {
                Product prod = msg.getProduct();
                String firstImg = (prod != null && prod.getImageUrls() != null && !prod.getImageUrls().isEmpty())
                        ? prod.getImageUrls().get(0) : null;
                conversationMap.put(partner.getId(), ConversationResponse.builder()
                        .partner(dtoMapper.toUserSummary(partner))
                        .productId(prod != null ? prod.getId() : null)
                        .productTitle(prod != null ? prod.getTitle() : "Trao đổi đồ cũ KTX")
                        .productImage(firstImg)
                        .productPrice(prod != null ? prod.getPrice() : null)
                        .lastMessage(msg.getContent())
                        .lastMessageTime(msg.getCreatedAt() != null
                                ? msg.getCreatedAt().format(DateTimeFormatter.ofPattern("HH:mm dd/MM")) : "")
                        .unreadCount(0)
                        .build());
            }
        }
        return new ArrayList<>(conversationMap.values());
    }
}
