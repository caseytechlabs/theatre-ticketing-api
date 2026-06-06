package com.theater.ticketing.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theater.ticketing.dto.request.ConfirmPaymentRequest;
import com.theater.ticketing.dto.request.InitiateBookingRequest;
import com.theater.ticketing.dto.response.BookingResponse;
import com.theater.ticketing.exception.BookingNotFoundException;
import com.theater.ticketing.exception.GlobalExceptionHandler;
import com.theater.ticketing.exception.VoucherNotAvailableException;
import com.theater.ticketing.model.BookingStatus;
import com.theater.ticketing.model.VoucherStatus;
import com.theater.ticketing.service.BookingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.theater.ticketing.security.UserDetailsServiceImpl;
import com.theater.ticketing.service.JwtService;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        value = BookingController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@Import(GlobalExceptionHandler.class)
class BookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private JwtService jwtService;
    @MockBean private UserDetailsServiceImpl userDetailsService;
    @MockBean private BookingService bookingService;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void initiateBooking_validRequest_returns201() throws Exception {
        InitiateBookingRequest req = new InitiateBookingRequest("user-1", "voucher-1");
        BookingResponse resp = BookingResponse.builder()
                .id("booking-1")
                .voucherId("voucher-1")
                .userId("user-1")
                .status(BookingStatus.PENDING)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(bookingService.initiateBooking(any())).thenReturn(resp);

        mockMvc.perform(post("/api/v1/bookings/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void initiateBooking_voucherNotAvailable_returns409() throws Exception {
        InitiateBookingRequest req = new InitiateBookingRequest("user-1", "claimed-voucher");
        when(bookingService.initiateBooking(any()))
                .thenThrow(new VoucherNotAvailableException("claimed-voucher", VoucherStatus.CLAIMED));

        mockMvc.perform(post("/api/v1/bookings/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void confirmBooking_paymentSuccess_returns200WithConfirmed() throws Exception {
        ConfirmPaymentRequest req = new ConfirmPaymentRequest("booking-1", true);
        BookingResponse resp = BookingResponse.builder()
                .id("booking-1")
                .voucherId("voucher-1")
                .userId("user-1")
                .status(BookingStatus.CONFIRMED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(bookingService.confirmBooking(any())).thenReturn(resp);

        mockMvc.perform(post("/api/v1/bookings/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
    }

    @Test
    void confirmBooking_paymentFailed_returns200WithCancelled() throws Exception {
        ConfirmPaymentRequest req = new ConfirmPaymentRequest("booking-1", false);
        BookingResponse resp = BookingResponse.builder()
                .id("booking-1")
                .voucherId("voucher-1")
                .userId("user-1")
                .status(BookingStatus.CANCELLED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(bookingService.confirmBooking(any())).thenReturn(resp);

        mockMvc.perform(post("/api/v1/bookings/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    void getBooking_unknownId_returns404() throws Exception {
        when(bookingService.getBooking("ghost")).thenThrow(new BookingNotFoundException("ghost"));

        mockMvc.perform(get("/api/v1/bookings/ghost"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }
}
