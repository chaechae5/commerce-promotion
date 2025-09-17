package com.chae.promo.order.entity;

import com.chae.promo.common.entity.BaseTime;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "shipping_info")
@Getter
@Setter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ShippingInfo extends BaseTime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", unique = true)
    private Order order;

    @Column(name = "recipient_name", nullable = false)
    private String recipientName;

    @Column(name = "address", nullable = false)
    private String address;

    @Column(name = "zipcode", nullable = false, length = 5)
    private String zipcode;

    @Column(name = "phone_number", nullable = false, length = 20)
    private String phoneNumber;

    @Column(name = "memo", columnDefinition = "TEXT")
    private String memo;
}