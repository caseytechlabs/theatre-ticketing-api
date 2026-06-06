package com.theater.ticketing.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InitiateBookingRequest {

    // Optional for CLIENT role — system uses the authenticated username instead
    private String userId;

    @NotBlank(message = "Voucher ID must not be blank")
    private String voucherId;
}
