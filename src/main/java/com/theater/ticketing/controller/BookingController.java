package com.theater.ticketing.controller;

import com.theater.ticketing.dto.request.ConfirmPaymentRequest;
import com.theater.ticketing.dto.request.InitiateBookingRequest;
import com.theater.ticketing.dto.response.ApiResponse;
import com.theater.ticketing.dto.response.BookingResponse;
import com.theater.ticketing.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
@Tag(name = "Booking Management", description = "Two-step theater ticket booking flow")
public class BookingController {

    private final BookingService bookingService;

    @GetMapping
    @Operation(summary = "List all bookings", description = "Returns all bookings (admin)")
    public ResponseEntity<ApiResponse<List<BookingResponse>>> getAllBookings() {
        return ResponseEntity.ok(ApiResponse.success(bookingService.getAllBookings(), "Bookings retrieved"));
    }

    @GetMapping("/my")
    @Operation(summary = "Get my bookings", description = "Returns all bookings for the authenticated client")
    public ResponseEntity<ApiResponse<List<BookingResponse>>> getMyBookings() {
        return ResponseEntity.ok(ApiResponse.success(bookingService.getMyBookings(), "Bookings retrieved"));
    }

    @PostMapping("/initiate")
    @Operation(summary = "Initiate a booking", description = "Reserves a voucher (AVAILABLE → PENDING_CLAIM) and creates a pending booking")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Booking initiated, voucher reserved"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Voucher not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Voucher already claimed or pending")
    })
    public ResponseEntity<ApiResponse<BookingResponse>> initiateBooking(@Valid @RequestBody InitiateBookingRequest request) {
        BookingResponse response = bookingService.initiateBooking(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Booking initiated successfully"));
    }

    @PostMapping("/confirm")
    @Operation(summary = "Confirm payment", description = "Transitions PENDING_CLAIM → CLAIMED (success) or AVAILABLE (failure)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Booking confirmed or cancelled"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Booking not found")
    })
    public ResponseEntity<ApiResponse<BookingResponse>> confirmBooking(@Valid @RequestBody ConfirmPaymentRequest request) {
        BookingResponse response = bookingService.confirmBooking(request);
        String msg = request.isPaymentSuccess() ? "Booking confirmed" : "Booking cancelled, voucher released";
        return ResponseEntity.ok(ApiResponse.success(response, msg));
    }

    @DeleteMapping("/{bookingId}")
    @Operation(summary = "Delete a booking", description = "Removes a booking record. If PENDING, the voucher is released first.")
    public ResponseEntity<ApiResponse<Void>> deleteBooking(@PathVariable String bookingId) {
        bookingService.deleteBooking(bookingId);
        return ResponseEntity.ok(ApiResponse.success(null, "Booking deleted"));
    }

    @GetMapping("/{bookingId}")
    @Operation(summary = "Get a booking", description = "Retrieves the current status of a booking")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Booking retrieved"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Booking not found")
    })
    public ResponseEntity<ApiResponse<BookingResponse>> getBooking(@PathVariable String bookingId) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.getBooking(bookingId), "Booking retrieved"));
    }
}
