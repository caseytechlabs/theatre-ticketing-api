package com.theater.ticketing.service.impl;

import com.theater.ticketing.dto.request.CreateVoucherRequest;
import com.theater.ticketing.dto.response.VoucherResponse;
import com.theater.ticketing.exception.VoucherNotFoundException;
import com.theater.ticketing.model.Voucher;
import com.theater.ticketing.model.VoucherStatus;
import com.theater.ticketing.repository.VoucherRepository;
import com.theater.ticketing.service.VoucherService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VoucherServiceImpl implements VoucherService {

    private final VoucherRepository voucherRepository;

    @Value("${theater.voucher.pending-claim-ttl-seconds}")
    private long pendingClaimTtlSeconds;

    @Override
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
            ttlSeconds = pendingClaimTtlSeconds;
        }

        voucherRepository.save(voucher, ttlSeconds);
        return toResponse(voucher);
    }

    @Override
    public VoucherResponse getVoucher(String voucherId) {
        return voucherRepository.findById(voucherId)
                .map(this::toResponse)
                .orElseThrow(() -> new VoucherNotFoundException(voucherId));
    }

    @Override
    public List<VoucherResponse> getAllVouchers() {
        return voucherRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<VoucherResponse> getAvailableVouchersByCustomerId(String customerId) {
        Instant now = Instant.now();
        return voucherRepository.findAll().stream()
                .filter(v -> customerId.equals(v.getCustomerId()))
                .filter(v -> v.getStatus() == VoucherStatus.AVAILABLE)
                .filter(v -> v.getExpiresAt() != null && v.getExpiresAt().isAfter(now))
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
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
