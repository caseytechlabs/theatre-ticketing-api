package com.theater.ticketing.service;

import com.theater.ticketing.dto.request.ConfirmPaymentRequest;
import com.theater.ticketing.dto.request.InitiateBookingRequest;
import com.theater.ticketing.dto.response.BookingResponse;
import com.theater.ticketing.exception.BookingNotFoundException;
import com.theater.ticketing.exception.VoucherNotFoundException;
import com.theater.ticketing.exception.VoucherNotAvailableException;
import com.theater.ticketing.model.Booking;
import com.theater.ticketing.model.BookingStatus;
import com.theater.ticketing.model.Voucher;
import com.theater.ticketing.model.VoucherStatus;
import com.theater.ticketing.repository.BookingRepository;
import com.theater.ticketing.repository.VoucherRepository;
import com.theater.ticketing.service.BookingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private VoucherRepository voucherRepository;

    @Mock
    private BookingRepository bookingRepository;

    @InjectMocks
    private BookingService bookingService;

    private Voucher voucher(String id, VoucherStatus status) {
        return Voucher.builder().id(id).status(status).expiresAt(Instant.now().plusSeconds(3600)).build();
    }

    // ── initiateBooking ───────────────────────────────────────────────────────

    @Test
    void initiateBooking_availableVoucher_returnsPendingBooking() {
        InitiateBookingRequest request = new InitiateBookingRequest("user-1", "voucher-1");
        when(voucherRepository.findById("voucher-1")).thenReturn(Optional.of(voucher("voucher-1", VoucherStatus.AVAILABLE)));
        when(voucherRepository.claimVoucher(anyString(), anyString(), any(), any())).thenReturn(1L);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingResponse response = bookingService.initiateBooking(request);

        assertThat(response.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(response.getVoucherId()).isEqualTo("voucher-1");
        verify(bookingRepository).save(any(Booking.class));
    }

    @Test
    void initiateBooking_voucherNotFound_throwsVoucherNotFoundException() {
        InitiateBookingRequest request = new InitiateBookingRequest("user-1", "ghost-voucher");
        when(voucherRepository.findById("ghost-voucher")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.initiateBooking(request))
                .isInstanceOf(VoucherNotFoundException.class);
    }

    @Test
    void initiateBooking_voucherPendingClaim_throwsVoucherNotAvailableException() {
        InitiateBookingRequest request = new InitiateBookingRequest("user-1", "pending-voucher");
        when(voucherRepository.findById("pending-voucher")).thenReturn(Optional.of(voucher("pending-voucher", VoucherStatus.PENDING_CLAIM)));
        when(voucherRepository.claimVoucher(anyString(), anyString(), any(), any())).thenReturn(-2L);

        assertThatThrownBy(() -> bookingService.initiateBooking(request))
                .isInstanceOf(VoucherNotAvailableException.class)
                .hasMessageContaining("PENDING_CLAIM");
    }

    @Test
    void initiateBooking_voucherAlreadyClaimed_throwsVoucherNotAvailableException() {
        InitiateBookingRequest request = new InitiateBookingRequest("user-1", "claimed-voucher");
        when(voucherRepository.findById("claimed-voucher")).thenReturn(Optional.of(voucher("claimed-voucher", VoucherStatus.CLAIMED)));
        when(voucherRepository.claimVoucher(anyString(), anyString(), any(), any())).thenReturn(-3L);

        assertThatThrownBy(() -> bookingService.initiateBooking(request))
                .isInstanceOf(VoucherNotAvailableException.class)
                .hasMessageContaining("CLAIMED");
    }

    @Test
    void initiateBooking_expiredVoucher_throwsVoucherNotFoundException() {
        // Voucher exists in Redis but the Lua script finds it expired, deletes it, and returns -4
        InitiateBookingRequest request = new InitiateBookingRequest("user-1", "expired-voucher");
        when(voucherRepository.findById("expired-voucher")).thenReturn(Optional.of(voucher("expired-voucher", VoucherStatus.AVAILABLE)));
        when(voucherRepository.claimVoucher(anyString(), anyString(), any(), any())).thenReturn(-4L);

        assertThatThrownBy(() -> bookingService.initiateBooking(request))
                .isInstanceOf(VoucherNotFoundException.class);
    }

    // ── confirmBooking ────────────────────────────────────────────────────────

    @Test
    void confirmBooking_paymentSuccess_returnsConfirmedBooking() {
        ConfirmPaymentRequest request = new ConfirmPaymentRequest("booking-1", true);
        Booking booking = Booking.builder().id("booking-1").voucherId("voucher-1").userId("user-1")
                .status(BookingStatus.PENDING).createdAt(Instant.now()).updatedAt(Instant.now()).build();

        when(bookingRepository.findById("booking-1")).thenReturn(Optional.of(booking));
        when(voucherRepository.confirmVoucher(anyString(), anyBoolean(), any())).thenReturn(1L);

        BookingResponse response = bookingService.confirmBooking(request);

        assertThat(response.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(bookingRepository).updateStatus(anyString(), any(BookingStatus.class), any());
    }

    @Test
    void confirmBooking_paymentFailed_returnsCancelledBooking() {
        ConfirmPaymentRequest request = new ConfirmPaymentRequest("booking-2", false);
        Booking booking = Booking.builder().id("booking-2").voucherId("voucher-2").userId("user-2")
                .status(BookingStatus.PENDING).createdAt(Instant.now()).updatedAt(Instant.now()).build();

        when(bookingRepository.findById("booking-2")).thenReturn(Optional.of(booking));
        when(voucherRepository.confirmVoucher(anyString(), anyBoolean(), any())).thenReturn(1L);

        assertThat(bookingService.confirmBooking(request).getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void confirmBooking_bookingNotFound_throwsBookingNotFoundException() {
        when(bookingRepository.findById("no-booking")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.confirmBooking(new ConfirmPaymentRequest("no-booking", true)))
                .isInstanceOf(BookingNotFoundException.class)
                .hasMessageContaining("no-booking");
    }

    @Test
    void confirmBooking_voucherExpiredOrWrongState_returnsCancelledBookingGracefully() {
        // Lua script returns < 0 (voucher TTL elapsed) — booking is cancelled gracefully, no exception
        ConfirmPaymentRequest request = new ConfirmPaymentRequest("booking-1", true);
        Booking booking = Booking.builder().id("booking-1").voucherId("voucher-1").userId("user-1")
                .status(BookingStatus.PENDING).createdAt(Instant.now()).updatedAt(Instant.now()).build();

        when(bookingRepository.findById("booking-1")).thenReturn(Optional.of(booking));
        when(voucherRepository.confirmVoucher(anyString(), anyBoolean(), any())).thenReturn(-2L);

        BookingResponse response = bookingService.confirmBooking(request);

        assertThat(response.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        verify(bookingRepository).updateStatus(eq("booking-1"), eq(BookingStatus.CANCELLED), any());
    }

    @Test
    void confirmBooking_alreadyConfirmed_returnsCurrentStateIdempotently() {
        Booking booking = Booking.builder().id("booking-1").voucherId("voucher-1").userId("user-1")
                .status(BookingStatus.CONFIRMED).createdAt(Instant.now()).updatedAt(Instant.now()).build();

        when(bookingRepository.findById("booking-1")).thenReturn(Optional.of(booking));

        assertThat(bookingService.confirmBooking(new ConfirmPaymentRequest("booking-1", true)).getStatus())
                .isEqualTo(BookingStatus.CONFIRMED);
    }

    // ── concurrency ───────────────────────────────────────────────────────────

    @Test
    void initiateBooking_concurrentRequests_onlyOneSucceeds() throws InterruptedException {
        // Simulates Redis Lua atomicity: claimVoucher returns 1 exactly once, -2 for all others.
        // Verifies the service layer correctly surfaces exactly 1 success and N-1 conflicts.
        int threads = 10;
        AtomicInteger callCount = new AtomicInteger(0);

        when(voucherRepository.findById(anyString())).thenReturn(Optional.of(voucher("voucher-1", VoucherStatus.AVAILABLE)));
        when(voucherRepository.claimVoucher(anyString(), anyString(), any(), any()))
                .thenAnswer(inv -> callCount.getAndIncrement() == 0 ? 1L : -2L);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch allDone = new CountDownLatch(threads);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger conflicts = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            final String userId = "user-" + i;
            new Thread(() -> {
                try {
                    startGun.await();
                    bookingService.initiateBooking(new InitiateBookingRequest(userId, "voucher-1"));
                    successes.incrementAndGet();
                } catch (VoucherNotAvailableException e) {
                    conflicts.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    allDone.countDown();
                }
            }).start();
        }

        startGun.countDown();
        allDone.await();

        assertThat(successes.get()).isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(threads - 1);
    }

    // ── getBooking ────────────────────────────────────────────────────────────

    @Test
    void getBooking_existingId_returnsBookingResponse() {
        Booking booking = Booking.builder().id("b1").voucherId("v1").userId("u1")
                .status(BookingStatus.CONFIRMED).createdAt(Instant.now()).updatedAt(Instant.now()).build();

        when(bookingRepository.findById("b1")).thenReturn(Optional.of(booking));

        BookingResponse response = bookingService.getBooking("b1");

        assertThat(response.getId()).isEqualTo("b1");
        assertThat(response.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }
}