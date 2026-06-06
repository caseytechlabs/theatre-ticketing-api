package com.theater.ticketing.exception;

public class BookingNotFoundException extends RuntimeException {

    public BookingNotFoundException(String bookingId) {
        super("Booking not found with id: " + bookingId);
    }
}
