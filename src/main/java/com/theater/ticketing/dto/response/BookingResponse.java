package com.theater.ticketing.dto.response;

import com.theater.ticketing.model.BookingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponse {

    private String id;
    private String voucherId;
    private String userId;
    private BookingStatus status;
    private Instant createdAt;
    private Instant updatedAt;
}
