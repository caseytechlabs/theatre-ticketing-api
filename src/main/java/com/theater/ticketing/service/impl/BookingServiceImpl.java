package com.theater.ticketing.service.impl;

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
import com.theater.ticketing.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private final VoucherRepository voucherRepository;
    private final BookingRepository bookingRepository;

    @Override
    public BookingResponse initiateBooking(InitiateBookingRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isClient = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CLIENT"));

        // For CLIENT: override userId with their own username and verify voucher ownership
        String effectiveUserId = (isClient && auth != null)
                ? auth.getName()
                : request.getUserId();

        if (isClient) {
            Voucher voucher = voucherRepository.findById(request.getVoucherId())
                    .orElseThrow(() -> new VoucherNotFoundException(request.getVoucherId()));
            if (!auth.getName().equals(voucher.getCustomerId())) {
                throw new UnauthorizedAccessException("This voucher is not assigned to your account");
            }
        }

        Instant now = Instant.now();
        long result = voucherRepository.claimVoucher(request.getVoucherId(), effectiveUserId, now, now);

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
                .status(BookingStatus.PENDING)
                .createdAt(now)
                .updatedAt(now)
                .build();

        bookingRepository.save(booking);
        return toResponse(booking);
    }

    @Override
    public BookingResponse confirmBooking(ConfirmPaymentRequest request) {
        Booking booking = bookingRepository.findById(request.getBookingId())
                .orElseThrow(() -> new BookingNotFoundException(request.getBookingId()));

        Instant now = Instant.now();
        long result = voucherRepository.confirmVoucher(booking.getVoucherId(), request.isPaymentSuccess(), now);

        if (result == -1) {
            throw new VoucherNotFoundException(booking.getVoucherId());
        }

        BookingStatus newStatus = request.isPaymentSuccess() ? BookingStatus.CONFIRMED : BookingStatus.CANCELLED;
        bookingRepository.updateStatus(booking.getId(), newStatus, now);
        booking.setStatus(newStatus);
        booking.setUpdatedAt(now);
        return toResponse(booking);
    }

    @Override
    public BookingResponse getBooking(String bookingId) {
        return bookingRepository.findById(bookingId)
                .map(this::toResponse)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
    }

    private BookingResponse toResponse(Booking booking) {
        return BookingResponse.builder()
                .id(booking.getId())
                .voucherId(booking.getVoucherId())
                .userId(booking.getUserId())
                .status(booking.getStatus())
                .createdAt(booking.getCreatedAt())
                .updatedAt(booking.getUpdatedAt())
                .build();
    }
}
