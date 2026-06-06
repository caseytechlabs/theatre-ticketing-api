package com.theater.ticketing.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Voucher {

    private String id;
    private VoucherStatus status;
    private String customerId;
    private String pendingUserId;
    private Instant createdAt;
    private Instant expiresAt;
    private Instant claimedAt;
    private Instant pendingAt;
}
