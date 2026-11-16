package com.chodocu.backend.entity;

import com.chodocu.backend.enums.MessageType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(nullable = false, length = 1000)
    private String content;

    /** Loại tin: TEXT, IMAGE, OFFER, MEETUP, SYSTEM (xem enum MessageType). */
    @Builder.Default
    private String messageType = MessageType.TEXT.name();

    /** Ảnh đính kèm (chỉ có với tin IMAGE). */
    @Column(length = 1000)
    private String imageUrl;

    @Column(precision = 15, scale = 0)
    private BigDecimal offeredPrice;

    private String meetupLocation;

    private String meetupTime;

    @Builder.Default
    private Boolean isRead = false;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
