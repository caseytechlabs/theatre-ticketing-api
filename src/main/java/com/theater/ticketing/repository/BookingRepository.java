package com.theater.ticketing.repository;

import com.theater.ticketing.model.Booking;
import com.theater.ticketing.model.BookingStatus;

import java.time.Instant;
import java.util.Optional;

public interface BookingRepository {
    Booking save(Booking booking);
    Optional<Booking> findById(String bookingId);
    void updateStatus(String bookingId, BookingStatus status, Instant updatedAt);
}
