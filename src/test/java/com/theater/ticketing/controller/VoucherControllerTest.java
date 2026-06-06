package com.theater.ticketing.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.theater.ticketing.dto.request.CreateVoucherRequest;
import com.theater.ticketing.dto.response.VoucherResponse;
import com.theater.ticketing.exception.GlobalExceptionHandler;
import com.theater.ticketing.exception.VoucherNotFoundException;
import com.theater.ticketing.model.VoucherStatus;
import com.theater.ticketing.service.VoucherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.theater.ticketing.security.UserDetailsServiceImpl;
import com.theater.ticketing.service.JwtService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        value = VoucherController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@Import(GlobalExceptionHandler.class)
class VoucherControllerTest {

    @MockBean private JwtService jwtService;
    @MockBean private UserDetailsServiceImpl userDetailsService;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private VoucherService voucherService;

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Test
    void createVoucher_validRequest_returns201WithBody() throws Exception {
        Instant expiresAt = Instant.now().plus(24, ChronoUnit.HOURS);
        CreateVoucherRequest req = new CreateVoucherRequest("cust-1", expiresAt);
        VoucherResponse resp = VoucherResponse.builder()
                .id("v-001")
                .status(VoucherStatus.AVAILABLE)
                .customerId("cust-1")
                .createdAt(Instant.now())
                .expiresAt(expiresAt)
                .build();

        when(voucherService.createVoucher(any())).thenReturn(resp);

        mockMvc.perform(post("/api/v1/vouchers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value("v-001"))
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"));
    }

    @Test
    void createVoucher_missingCustomerId_returns400() throws Exception {
        String body = """
                {"expiresAt": "2099-12-31T00:00:00Z"}
                """;

        mockMvc.perform(post("/api/v1/vouchers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void getVoucher_existingId_returns200() throws Exception {
        VoucherResponse resp = VoucherResponse.builder()
                .id("v-001")
                .status(VoucherStatus.AVAILABLE)
                .customerId("cust-1")
                .build();

        when(voucherService.getVoucher("v-001")).thenReturn(resp);

        mockMvc.perform(get("/api/v1/vouchers/v-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("v-001"))
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getVoucher_unknownId_returns404() throws Exception {
        when(voucherService.getVoucher("ghost")).thenThrow(new VoucherNotFoundException("ghost"));

        mockMvc.perform(get("/api/v1/vouchers/ghost"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void deleteVoucher_existingId_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/vouchers/v-001"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteVoucher_unknownId_returns404() throws Exception {
        doThrow(new VoucherNotFoundException("ghost")).when(voucherService).deleteVoucher("ghost");

        mockMvc.perform(delete("/api/v1/vouchers/ghost"))
                .andExpect(status().isNotFound());
    }
}
