package com.theater.ticketing.service;

import com.theater.ticketing.dto.request.ConfirmPaymentRequest;
import com.theater.ticketing.dto.request.InitiateBookingRequest;
import com.theater.ticketing.dto.response.BookingResponse;
import com.theater.ticketing.exception.BookingNotFoundException;
import com.theater.ticketing.exception.VoucherNotFoundException;
import com.theater.ticketing.exception.VoucherNotAvailableException;
import com.theater.ticketing.model.Booking;
import com.theater.ticketing.model.BookingStatus;
import com.theater.ticketing.repository.BookingRepository;
import com.theater.ticketing.repository.VoucherRepository;
import com.theater.ticketing.service.impl.BookingServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private VoucherRepository voucherRepository;

    @Mock
    private BookingRepository bookingRepository;

    @InjectMocks
    private BookingServiceImpl bookingService;

    @Test
    void initiateBooking_availableVoucher_returnsPendingBooking() {
        InitiateBookingRequest request = new InitiateBookingRequest("user-1", "voucher-1");
        when(voucherRepository.claimVoucher(anyString(), anyString(), any(), any())).thenReturn(1L);

        Booking saved = Booking.builder()
                .id("booking-1")
                .voucherId("voucher-1")
                .userId("user-1")
                .status(BookingStatus.PENDING)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        when(bookingRepository.save(any(Booking.class))).thenReturn(saved);

        BookingResponse response = bookingService.initiateBooking(request);

        assertThat(response.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(response.getVoucherId()).isEqualTo("voucher-1");
        verify(bookingRepository).save(any(Booking.class));
    }

    @Test
    void initiateBooking_voucherNotFound_throwsVoucherNotFoundException() {
        InitiateBookingRequest request = new InitiateBookingRequest("user-1", "ghost-voucher");
        when(voucherRepository.claimVoucher(anyString(), anyString(), any(), any())).thenReturn(-1L);

        assertThatThrownBy(() -> bookingService.initiateBooking(request))
                .isInstanceOf(VoucherNotFoundException.class);
    }

    @Test
    void initiateBooking_voucherPendingClaim_throwsVoucherNotAvailableException() {
        InitiateBookingRequest request = new InitiateBookingRequest("user-1", "pending-voucher");
        when(voucherRepository.claimVoucher(anyString(), anyString(), any(), any())).thenReturn(-2L);

        assertThatThrownBy(() -> bookingService.initiateBooking(request))
                .isInstanceOf(VoucherNotAvailableException.class)
                .hasMessageContaining("PENDING_CLAIM");
    }

    @Test
    void initiateBooking_voucherAlreadyClaimed_throwsVoucherNotAvailableException() {
        InitiateBookingRequest request = new InitiateBookingRequest("user-1", "claimed-voucher");
        when(voucherRepository.claimVoucher(anyString(), anyString(), any(), any())).thenReturn(-3L);

        assertThatThrownBy(() -> bookingService.initiateBooking(request))
                .isInstanceOf(VoucherNotAvailableException.class)
                .hasMessageContaining("CLAIMED");
    }

    @Test
    void initiateBooking_expiredVoucher_throwsVoucherNotFoundException() {
        InitiateBookingRequest request = new InitiateBookingRequest("user-1", "expired-voucher");
        when(voucherRepository.claimVoucher(anyString(), anyString(), any(), any())).thenReturn(-4L);

        assertThatThrownBy(() -> bookingService.initiateBooking(request))
                .isInstanceOf(VoucherNotFoundException.class);
    }

    @Test
    void confirmBooking_paymentSuccess_returnsConfirmedBooking() {
        ConfirmPaymentRequest request = new ConfirmPaymentRequest("booking-1", true);
        Booking booking = Booking.builder()
                .id("booking-1")
                .voucherId("voucher-1")
                .userId("user-1")
                .status(BookingStatus.PENDING)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(bookingRepository.findById("booking-1")).thenReturn(Optional.of(booking));
        when(voucherRepository.confirmVoucher(anyString(), anyBoolean(), any())).thenReturn(1L);

        BookingResponse response = bookingService.confirmBooking(request);

        assertThat(response.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(bookingRepository).updateStatus(anyString(), any(BookingStatus.class), any());
    }

    @Test
    void confirmBooking_paymentFailed_returnsCancelledBooking() {
        ConfirmPaymentRequest request = new ConfirmPaymentRequest("booking-2", false);
        Booking booking = Booking.builder()
                .id("booking-2")
                .voucherId("voucher-2")
                .userId("user-2")
                .status(BookingStatus.PENDING)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(bookingRepository.findById("booking-2")).thenReturn(Optional.of(booking));
        when(voucherRepository.confirmVoucher(anyString(), anyBoolean(), any())).thenReturn(1L);

        BookingResponse response = bookingService.confirmBooking(request);

        assertThat(response.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void confirmBooking_bookingNotFound_throwsBookingNotFoundException() {
        ConfirmPaymentRequest request = new ConfirmPaymentRequest("no-booking", true);
        when(bookingRepository.findById("no-booking")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.confirmBooking(request))
                .isInstanceOf(BookingNotFoundException.class)
                .hasMessageContaining("no-booking");
    }

    @Test
    void getBooking_existingId_returnsBookingResponse() {
        Booking booking = Booking.builder()
                .id("b1")
                .voucherId("v1")
                .userId("u1")
                .status(BookingStatus.CONFIRMED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(bookingRepository.findById("b1")).thenReturn(Optional.of(booking));

        BookingResponse response = bookingService.getBooking("b1");

        assertThat(response.getId()).isEqualTo("b1");
        assertThat(response.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }
}
