package com.chodocu.backend.service;

import com.chodocu.backend.dto.chat.ChatMessageRequest;
import com.chodocu.backend.dto.chat.ChatMessageResponse;
import com.chodocu.backend.dto.chat.ConversationResponse;
import com.chodocu.backend.entity.ChatMessage;
import com.chodocu.backend.entity.Product;
import com.chodocu.backend.entity.User;
import com.chodocu.backend.enums.MessageType;
import com.chodocu.backend.exception.BadRequestException;
import com.chodocu.backend.exception.ResourceNotFoundException;
import com.chodocu.backend.repository.ChatMessageRepository;
import com.chodocu.backend.repository.ProductRepository;
import com.chodocu.backend.repository.UserRepository;
import com.chodocu.backend.util.DisplayFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Chat giữa người mua và người bán.
 *
 * Tin nhắn được lưu DB rồi đẩy realtime qua WebSocket tới đúng người:
 * - "/user/queue/messages": tin nhắn mới (gửi cho cả người nhận và người gửi).
 * - "/user/queue/read": thông báo người kia đã xem tin.
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final String MESSAGES_QUEUE = "/queue/messages";
    private static final String READ_RECEIPTS_QUEUE = "/queue/read";
    private static final String IMAGE_PREVIEW = "📷 Hình ảnh";
    private static final int MAX_CONTENT_LENGTH = 1000; // khớp độ dài cột content
    private static final int MAX_URL_LENGTH = 1000;
    private static final BigDecimal MAX_PRICE = new BigDecimal("1000000000"); // 1 tỷ, giống app và SQL

    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final DtoMapper dtoMapper;

    // ----------------------------------------------------------------
    // Gửi tin
    // ----------------------------------------------------------------

    /** Người dùng gửi tin (qua REST hoặc WebSocket). */
    @Transactional
    public ChatMessageResponse sendMessage(ChatMessageRequest request, String senderEmail) {
        User sender = findUserByEmail(senderEmail);
        User receiver = findReceiver(request.getReceiverId(), sender);

        MessageType type = MessageType.fromString(request.getMessageType());
        if (type == null) {
            throw new BadRequestException("Loại tin nhắn không hợp lệ");
        }
        Product product = findConversationProduct(request.getProductId(), sender, receiver);
        if (product == null && (type == MessageType.OFFER || type == MessageType.MEETUP)) {
            throw new BadRequestException("Vui lòng chọn món đồ cần trả giá hoặc hẹn gặp.");
        }

        ChatMessage message = ChatMessage.builder()
                .sender(sender)
                .receiver(receiver)
                .product(product)
                .messageType(type.name())
                .isRead(false)
                .build();
        fillByType(message, type, request, product);

        return saveAndPush(message);
    }

    /** Tin nhắn tự động do nghiệp vụ khác tạo sẵn (ví dụ tin hẹn gặp khi đặt mua). */
    @Transactional
    public ChatMessageResponse sendAutomaticMessage(ChatMessage message) {
        return saveAndPush(message);
    }

    /** Tin nhắn hệ thống: báo trạng thái đơn hàng (đã chấp nhận, đã hủy, đã hoàn tất...). */
    @Transactional
    public void sendSystemMessage(User from, User to, Product product, String content) {
        ChatMessage message = ChatMessage.builder()
                .sender(from)
                .receiver(to)
                .product(product)
                .messageType(MessageType.SYSTEM.name())
                .content(content)
                .isRead(false)
                .build();
        saveAndPush(message);
    }

    // ----------------------------------------------------------------
    // Đọc tin
    // ----------------------------------------------------------------

    /** Lịch sử chat với một người, tin cũ nhất trước. */
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getConversation(String currentUserEmail, Long partnerId) {
        User me = findUserByEmail(currentUserEmail);
        return chatMessageRepository.findConversation(me.getId(), partnerId).stream()
                .map(dtoMapper::toChatMessageResponse)
                .toList();
    }

    /** Hộp thư: mỗi người đối thoại một dòng, kèm tin cuối và số tin chưa đọc. */
    @Transactional(readOnly = true)
    public List<ConversationResponse> getConversationsList(String currentUserEmail) {
        User me = findUserByEmail(currentUserEmail);
        List<ChatMessage> messages = chatMessageRepository.findAllMessagesForUser(me.getId()); // mới nhất trước

        Map<Long, ChatMessage> latestByPartner = new LinkedHashMap<>();
        Map<Long, Integer> unreadByPartner = new HashMap<>();
        for (ChatMessage msg : messages) {
            Long partnerId = partnerOf(msg, me).getId();
            latestByPartner.putIfAbsent(partnerId, msg);
            if (isUnreadFor(msg, me)) {
                unreadByPartner.merge(partnerId, 1, Integer::sum);
            }
        }

        return latestByPartner.entrySet().stream()
                .map(entry -> toConversation(entry.getValue(), me, unreadByPartner.getOrDefault(entry.getKey(), 0)))
                .toList();
    }

    /** Đánh dấu đã đọc mọi tin người kia gửi cho mình, rồi báo cho họ biết ("Đã xem"). */
    @Transactional
    public int markConversationRead(String currentUserEmail, Long partnerId) {
        User me = findUserByEmail(currentUserEmail);
        User partner = userRepository.findById(partnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));

        int updated = chatMessageRepository.markConversationRead(me.getId(), partner.getId());
        if (updated > 0) {
            Map<String, Object> receipt = Map.of(
                    "readerId", me.getId(),
                    "partnerId", partner.getId(),
                    "readAt", DisplayFormat.iso(DisplayFormat.nowUtc())
            );
            String partnerEmail = partner.getEmail();
            runAfterCommit(() -> messagingTemplate.convertAndSendToUser(partnerEmail, READ_RECEIPTS_QUEUE, receipt));
        }
        return updated;
    }

    /** Tổng số tin chưa đọc (hiện badge trên tab Tin nhắn). */
    @Transactional(readOnly = true)
    public long getUnreadCount(String currentUserEmail) {
        User me = findUserByEmail(currentUserEmail);
        return chatMessageRepository.countUnreadForUser(me.getId());
    }

    // ----------------------------------------------------------------
    // Hàm hỗ trợ
    // ----------------------------------------------------------------

    /**
     * Kiểm tra dữ liệu theo từng loại tin và điền nội dung phù hợp (giống hàm api_send_message trong SQL).
     * Tin trả giá / hẹn gặp không ghi chú thì tự điền một câu tóm tắt để hộp thư và thông báo dễ đọc.
     */
    private void fillByType(ChatMessage message, MessageType type, ChatMessageRequest request, Product product) {
        String text = request.getContent() != null ? request.getContent().trim() : "";
        if (text.length() > MAX_CONTENT_LENGTH) {
            // Tin gửi qua WebSocket không đi qua @Valid nên kiểm tra lại ở đây
            throw new BadRequestException("Tin nhắn tối đa " + MAX_CONTENT_LENGTH + " ký tự");
        }

        switch (type) {
            case TEXT -> {
                if (text.isEmpty()) {
                    throw new BadRequestException("Vui lòng nhập nội dung tin nhắn.");
                }
                message.setContent(text);
            }
            case IMAGE -> {
                String imageUrl = request.getImageUrl() != null ? request.getImageUrl().trim() : "";
                if (!isWebUrl(imageUrl)) {
                    throw new BadRequestException("Ảnh gửi kèm không hợp lệ, vui lòng chọn lại ảnh.");
                }
                message.setImageUrl(imageUrl);
                message.setContent(text); // chú thích ảnh, có thể để trống
            }
            case OFFER -> {
                BigDecimal price = request.getOfferedPrice();
                if (price == null || price.signum() <= 0 || price.compareTo(MAX_PRICE) > 0) {
                    throw new BadRequestException("Vui lòng nhập giá đề nghị hợp lệ.");
                }
                message.setOfferedPrice(price);
                message.setContent(text.isEmpty() ? "Mình đề nghị giá " + DisplayFormat.money(price) + "." : text);
            }
            case MEETUP -> {
                String location = MeetupRules.requireSpot(product.getUniversity(), request.getMeetupLocation());
                String time = MeetupRules.requireTime(request.getMeetupTime());
                BigDecimal price = request.getOfferedPrice();
                message.setMeetupLocation(location);
                message.setMeetupTime(time);
                message.setOfferedPrice(price != null && price.signum() >= 0 ? price : null);
                message.setContent(text.isEmpty()
                        ? "Mình đề xuất hẹn gặp tại " + location + " lúc " + DisplayFormat.meetupTime(time) + "."
                        : text);
            }
            case SYSTEM -> throw new BadRequestException("Không được gửi tin nhắn hệ thống");
        }
    }

    /** Link ảnh phải là http(s) do máy chủ upload trả về. */
    private boolean isWebUrl(String url) {
        String lower = url.toLowerCase();
        return (lower.startsWith("http://") || lower.startsWith("https://")) && url.length() <= MAX_URL_LENGTH;
    }

    /** Lưu tin, đợi DB commit xong mới đẩy realtime cho người nhận và người gửi. */
    private ChatMessageResponse saveAndPush(ChatMessage message) {
        ChatMessageResponse response = dtoMapper.toChatMessageResponse(chatMessageRepository.save(message));
        String receiverEmail = message.getReceiver().getEmail();
        String senderEmail = message.getSender().getEmail();

        runAfterCommit(() -> {
            messagingTemplate.convertAndSendToUser(receiverEmail, MESSAGES_QUEUE, response);
            messagingTemplate.convertAndSendToUser(senderEmail, MESSAGES_QUEUE, response);
        });
        return response;
    }

    /**
     * Chạy việc sau khi transaction commit thành công.
     * Tránh trường hợp app nhận tin realtime trong khi DB chưa lưu xong (hoặc bị rollback).
     */
    private void runAfterCommit(Runnable task) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            task.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                task.run();
            }
        });
    }

    private ConversationResponse toConversation(ChatMessage last, User me, int unreadCount) {
        Product product = last.getProduct();
        return ConversationResponse.builder()
                .partner(dtoMapper.toPublicUser(partnerOf(last, me)))
                .productId(product != null ? product.getId() : null)
                .productTitle(product != null ? product.getTitle() : null)
                .productImage(dtoMapper.firstImageOf(product))
                .productPrice(product != null ? product.getPrice() : null)
                .lastMessage(previewOf(last))
                .lastMessageType(last.getMessageType())
                .lastMessageMine(last.getSender().getId().equals(me.getId()))
                .lastMessageTime(DisplayFormat.shortTime(last.getCreatedAt()))
                .lastMessageAtIso(DisplayFormat.iso(last.getCreatedAt()))
                .unreadCount(unreadCount)
                .build();
    }

    /** Nội dung xem trước trong hộp thư. */
    private String previewOf(ChatMessage message) {
        MessageType type = MessageType.fromString(message.getMessageType());
        if (type == MessageType.IMAGE) {
            return IMAGE_PREVIEW;
        }
        if (type == MessageType.OFFER && message.getOfferedPrice() != null) {
            return "Trả giá: " + DisplayFormat.money(message.getOfferedPrice());
        }
        return message.getContent();
    }

    private User partnerOf(ChatMessage message, User me) {
        return message.getSender().getId().equals(me.getId()) ? message.getReceiver() : message.getSender();
    }

    private boolean isUnreadFor(ChatMessage message, User me) {
        return message.getReceiver().getId().equals(me.getId()) && !Boolean.TRUE.equals(message.getIsRead());
    }

    private User findReceiver(Long receiverId, User sender) {
        if (receiverId == null) {
            throw new BadRequestException("Người nhận không được để trống");
        }
        if (receiverId.equals(sender.getId())) {
            throw new BadRequestException("Không thể nhắn tin cho chính mình");
        }
        return userRepository.findById(receiverId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người nhận"));
    }

    /** Món đồ gắn kèm tin nhắn (nếu có) phải là đồ của một trong hai người đang trò chuyện. */
    private Product findConversationProduct(Long productId, User sender, User receiver) {
        if (productId == null) {
            return null;
        }
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tin đăng."));
        Long sellerId = product.getSeller().getId();
        if (!sellerId.equals(sender.getId()) && !sellerId.equals(receiver.getId())) {
            throw new BadRequestException("Món đồ này không thuộc cuộc trò chuyện.");
        }
        return product;
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
    }
}
