package com.theater.ticketing.repository;

import com.theater.ticketing.model.Voucher;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface VoucherRepository {
    Voucher save(Voucher voucher, long ttlSeconds);
    Optional<Voucher> findById(String voucherId);
    List<Voucher> findAll();
    void delete(String voucherId);
    long claimVoucher(String voucherId, String userId, Instant pendingAt, Instant now);
    long confirmVoucher(String voucherId, boolean paymentSuccess, Instant timestamp);
}
