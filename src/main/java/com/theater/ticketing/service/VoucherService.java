package com.theater.ticketing.service;

import com.theater.ticketing.dto.request.CreateVoucherRequest;
import com.theater.ticketing.dto.response.VoucherResponse;
import com.theater.ticketing.exception.VoucherNotFoundException;
import com.theater.ticketing.model.Voucher;
import com.theater.ticketing.model.VoucherStatus;
import com.theater.ticketing.repository.VoucherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VoucherService {

    private final VoucherRepository voucherRepository;

    public VoucherResponse createVoucher(CreateVoucherRequest request) {
        Instant now = Instant.now();
        Voucher voucher = Voucher.builder()
                .id(UUID.randomUUID().toString())
                .status(VoucherStatus.AVAILABLE)
                .customerId(request.getCustomerId())
                .createdAt(now)
                .expiresAt(request.getExpiresAt())
                .build();

        long ttlSeconds = request.getExpiresAt().getEpochSecond() - now.getEpochSecond();
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("Voucher expiry must be in the future");
        }

        voucherRepository.save(voucher, ttlSeconds);
        return toResponse(voucher);
    }

    public VoucherResponse getVoucher(String voucherId) {
        return voucherRepository.findById(voucherId)
                .map(this::toResponse)
                .orElseThrow(() -> new VoucherNotFoundException(voucherId));
    }

    public List<VoucherResponse> getAllVouchers() {
        Instant now = Instant.now();
        return voucherRepository.findAll().stream()
                .filter(v -> v.getStatus() == VoucherStatus.CLAIMED
                        || v.getExpiresAt() == null
                        || v.getExpiresAt().isAfter(now))
                .map(this::toResponse)
                .toList();
    }

    public List<VoucherResponse> getAvailableVouchersByCustomerId(String customerId) {
        Instant now = Instant.now();
        return voucherRepository.findAll().stream()
                .filter(v -> customerId.equals(v.getCustomerId()))
                .filter(v -> v.getStatus() == VoucherStatus.AVAILABLE)
                .filter(v -> v.getExpiresAt() != null && v.getExpiresAt().isAfter(now))
                .map(this::toResponse)
                .toList();
    }

    public void deleteVoucher(String voucherId) {
        voucherRepository.findById(voucherId)
                .orElseThrow(() -> new VoucherNotFoundException(voucherId));
        voucherRepository.delete(voucherId);
    }

    private VoucherResponse toResponse(Voucher voucher) {
        return VoucherResponse.builder()
                .id(voucher.getId())
                .status(voucher.getStatus())
                .customerId(voucher.getCustomerId())
                .pendingUserId(voucher.getPendingUserId())
                .createdAt(voucher.getCreatedAt())
                .expiresAt(voucher.getExpiresAt())
                .claimedAt(voucher.getClaimedAt())
                .pendingAt(voucher.getPendingAt())
                .build();
    }
}
