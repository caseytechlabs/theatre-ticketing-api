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
public class Booking {

    private String id;
    private String voucherId;
    private String userId;
    private String customerId;
    private BookingStatus status;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant voucherExpiresAt;
}
