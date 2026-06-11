package com.theater.ticketing.service;

import com.theater.ticketing.dto.request.CreateVoucherRequest;
import com.theater.ticketing.dto.response.VoucherResponse;
import com.theater.ticketing.exception.VoucherNotFoundException;
import com.theater.ticketing.model.Voucher;
import com.theater.ticketing.model.VoucherStatus;
import com.theater.ticketing.repository.VoucherRepository;
import com.theater.ticketing.service.VoucherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoucherServiceTest {

    @Mock
    private VoucherRepository voucherRepository;

    @InjectMocks
    private VoucherService voucherService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(voucherService, "pendingClaimTtlSeconds", 300L);
    }

    @Test
    void createVoucher_validRequest_returnsAvailableVoucher() {
        Instant expiresAt = Instant.now().plus(24, ChronoUnit.HOURS);
        CreateVoucherRequest request = new CreateVoucherRequest("customer-123", expiresAt);

        Voucher saved = Voucher.builder()
                .id("v-uuid")
                .status(VoucherStatus.AVAILABLE)
                .customerId("customer-123")
                .createdAt(Instant.now())
                .expiresAt(expiresAt)
                .build();

        when(voucherRepository.save(any(Voucher.class), anyLong())).thenReturn(saved);

        VoucherResponse response = voucherService.createVoucher(request);

        assertThat(response).isNotNull();
        assertThat(response.getCustomerId()).isEqualTo("customer-123");
        assertThat(response.getStatus()).isEqualTo(VoucherStatus.AVAILABLE);
        verify(voucherRepository).save(any(Voucher.class), anyLong());
    }

    @Test
    void getVoucher_existingId_returnsVoucherResponse() {
        Voucher voucher = Voucher.builder()
                .id("v1")
                .status(VoucherStatus.AVAILABLE)
                .customerId("cust-1")
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();

        when(voucherRepository.findById("v1")).thenReturn(Optional.of(voucher));

        VoucherResponse response = voucherService.getVoucher("v1");

        assertThat(response.getId()).isEqualTo("v1");
        assertThat(response.getStatus()).isEqualTo(VoucherStatus.AVAILABLE);
    }

    @Test
    void getVoucher_missingId_throwsVoucherNotFoundException() {
        when(voucherRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> voucherService.getVoucher("missing"))
                .isInstanceOf(VoucherNotFoundException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void getAllVouchers_returnsMappedList() {
        Voucher v1 = Voucher.builder().id("v1").status(VoucherStatus.AVAILABLE).build();
        Voucher v2 = Voucher.builder().id("v2").status(VoucherStatus.CLAIMED).build();

        when(voucherRepository.findAll()).thenReturn(List.of(v1, v2));

        List<VoucherResponse> results = voucherService.getAllVouchers();

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getId()).isEqualTo("v1");
        assertThat(results.get(1).getId()).isEqualTo("v2");
    }

    @Test
    void deleteVoucher_existingId_deletesSuccessfully() {
        Voucher voucher = Voucher.builder().id("v1").status(VoucherStatus.AVAILABLE).build();
        when(voucherRepository.findById("v1")).thenReturn(Optional.of(voucher));

        voucherService.deleteVoucher("v1");

        verify(voucherRepository).delete("v1");
    }

    @Test
    void deleteVoucher_missingId_throwsVoucherNotFoundException() {
        when(voucherRepository.findById("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> voucherService.deleteVoucher("ghost"))
                .isInstanceOf(VoucherNotFoundException.class);
    }
}
