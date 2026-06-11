package com.theater.ticketing.service;

import com.theater.ticketing.dto.request.ConfirmPaymentRequest;
import com.theater.ticketing.dto.request.InitiateBookingRequest;
import com.theater.ticketing.dto.response.BookingResponse;
import com.theater.ticketing.exception.BookingNotFoundException;
import com.theater.ticketing.exception.UnauthorizedAccessException;
import com.theater.ticketing.exception.VoucherNotFoundException;
import com.theater.ticketing.exception.VoucherNotAvailableException;
import com.theater.ticketing.model.Booking;
import com.theater.ticketing.model.BookingStatus;
import com.theater.ticketing.model.Voucher;
import com.theater.ticketing.model.VoucherStatus;
import com.theater.ticketing.repository.BookingRepository;
import com.theater.ticketing.repository.VoucherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingService {

    // How long a client has to complete payment before the voucher is released.
    // Passed to claim_voucher.lua so it can auto-revert expired PENDING_CLAIM
    // vouchers inline — no background scheduler needed.
    private static final long PENDING_TTL_SECONDS = 600L;
    private static final long PENDING_TTL_MS = PENDING_TTL_SECONDS * 1_000L;

    private final VoucherRepository voucherRepository;
    private final BookingRepository bookingRepository;

    public BookingResponse initiateBooking(InitiateBookingRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isClient = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CLIENT"));

        String effectiveUserId = isClient ? auth.getName() : request.getUserId();

        Voucher voucher = voucherRepository.findById(request.getVoucherId())
                .orElseThrow(() -> new VoucherNotFoundException(request.getVoucherId()));

        if (isClient) {
            String ownerId = voucher.getCustomerId();
            // Universal vouchers have no customerId (null/blank) and are open to any client.
            // User-specific vouchers must match the authenticated client's username.
            boolean isUniversal = ownerId == null || ownerId.isBlank();
            if (!isUniversal && !auth.getName().equals(ownerId)) {
                throw new UnauthorizedAccessException("This voucher is not assigned to your account");
            }
        }

        Instant now = Instant.now();
        long result = voucherRepository.claimVoucher(request.getVoucherId(), effectiveUserId, now, now, PENDING_TTL_MS);

        switch ((int) result) {
            case -1 -> throw new VoucherNotFoundException(request.getVoucherId());
            case -2 -> throw new VoucherNotAvailableException(request.getVoucherId(), VoucherStatus.PENDING_CLAIM);
            case -3 -> throw new VoucherNotAvailableException(request.getVoucherId(), VoucherStatus.CLAIMED);
            case -4 -> throw new VoucherNotFoundException(request.getVoucherId() + " (expired)");
        }

        Booking booking = Booking.builder()
                .id(UUID.randomUUID().toString())
                .voucherId(request.getVoucherId())
                .userId(effectiveUserId)
                .customerId(voucher.getCustomerId())
                .status(BookingStatus.PENDING)
                .createdAt(now)
                .updatedAt(now)
                .voucherExpiresAt(voucher.getExpiresAt())
                .build();

        bookingRepository.save(booking);
        return toResponse(booking);
    }

    public BookingResponse confirmBooking(ConfirmPaymentRequest request) {
        Booking booking = bookingRepository.findById(request.getBookingId())
                .orElseThrow(() -> new BookingNotFoundException(request.getBookingId()));

        if (booking.getStatus() != BookingStatus.PENDING) {
            return toResponse(booking);
        }

        Instant now = Instant.now();
        long result = voucherRepository.confirmVoucher(booking.getVoucherId(), request.isPaymentSuccess(), now);

        if ((int) result < 0) {
            // Voucher already reverted (TTL expired and another caller re-claimed it)
            bookingRepository.updateStatus(booking.getId(), BookingStatus.CANCELLED, now);
            booking.setStatus(BookingStatus.CANCELLED);
            booking.setUpdatedAt(now);
            return toResponse(booking);
        }

        BookingStatus newStatus = request.isPaymentSuccess() ? BookingStatus.CONFIRMED : BookingStatus.CANCELLED;
        bookingRepository.updateStatus(booking.getId(), newStatus, now);
        booking.setStatus(newStatus);
        booking.setUpdatedAt(now);
        return toResponse(booking);
    }

    public List<BookingResponse> getAllBookings() {
        return bookingRepository.findAll().stream()
                .map(this::cancelIfExpired)
                .map(this::toResponse)
                .toList();
    }

    public List<BookingResponse> getMyBookings() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String userId = auth.getName();
        return bookingRepository.findAll().stream()
                .filter(b -> userId.equals(b.getUserId()))
                .map(this::cancelIfExpired)
                .map(this::toResponse)
                .toList();
    }

    public void deleteBooking(String bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        if (booking.getStatus() == BookingStatus.PENDING) {
            // Only release the voucher if this booking is still the active claim holder.
            // A re-claimed voucher (different pendingUserId) must not be released.
            voucherRepository.findById(booking.getVoucherId()).ifPresent(voucher -> {
                if (voucher.getStatus() == VoucherStatus.PENDING_CLAIM
                        && Objects.equals(booking.getUserId(), voucher.getPendingUserId())) {
                    voucherRepository.confirmVoucher(booking.getVoucherId(), false, Instant.now());
                }
            });
        }
        bookingRepository.delete(bookingId);
    }

    public BookingResponse getBooking(String bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        return toResponse(cancelIfExpired(booking));
    }

    // Lazy expiry: if a PENDING booking's TTL has passed, cancel it and release
    // the voucher. This runs on every read, replacing the background scheduler.
    private Booking cancelIfExpired(Booking booking) {
        if (booking.getStatus() != BookingStatus.PENDING) return booking;
        Instant pendingExpiresAt = booking.getCreatedAt().plusSeconds(PENDING_TTL_SECONDS);
        if (Instant.now().isBefore(pendingExpiresAt)) return booking;

        Instant now = Instant.now();
        voucherRepository.confirmVoucher(booking.getVoucherId(), false, now);
        bookingRepository.updateStatus(booking.getId(), BookingStatus.CANCELLED, now);
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setUpdatedAt(now);
        return booking;
    }

    private BookingResponse toResponse(Booking booking) {
        String customerId = booking.getCustomerId();
        String voucherType = (customerId == null || customerId.isBlank()) ? "Universal" : "Assigned";

        // Only set pendingExpiresAt for PENDING bookings so the frontend
        // knows exactly when the countdown should hit zero.
        Instant pendingExpiresAt = booking.getStatus() == BookingStatus.PENDING
                ? booking.getCreatedAt().plusSeconds(PENDING_TTL_SECONDS)
                : null;

        return BookingResponse.builder()
                .id(booking.getId())
                .voucherId(booking.getVoucherId())
                .userId(booking.getUserId())
                .status(booking.getStatus())
                .createdAt(booking.getCreatedAt())
                .updatedAt(booking.getUpdatedAt())
                .voucherExpiresAt(booking.getVoucherExpiresAt())
                .pendingExpiresAt(pendingExpiresAt)
                .voucherType(voucherType)
                .build();
    }
}
