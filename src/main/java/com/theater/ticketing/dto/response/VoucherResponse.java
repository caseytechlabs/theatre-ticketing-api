package com.theater.ticketing.dto.response;

import com.theater.ticketing.model.VoucherStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoucherResponse {

    private String id;
    private VoucherStatus status;
    private String customerId;
    private String pendingUserId;
    private Instant createdAt;
    private Instant expiresAt;
    private Instant claimedAt;
    private Instant pendingAt;
}
