package com.theater.ticketing.service;

import com.theater.ticketing.dto.request.ConfirmPaymentRequest;
import com.theater.ticketing.dto.request.InitiateBookingRequest;
import com.theater.ticketing.dto.response.BookingResponse;

public interface BookingService {
    BookingResponse initiateBooking(InitiateBookingRequest request);
    BookingResponse confirmBooking(ConfirmPaymentRequest request);
    BookingResponse getBooking(String bookingId);
}
