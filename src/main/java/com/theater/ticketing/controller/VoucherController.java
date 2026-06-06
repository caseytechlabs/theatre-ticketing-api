package com.theater.ticketing.controller;

import com.theater.ticketing.dto.request.CreateVoucherRequest;
import com.theater.ticketing.dto.response.ApiResponse;
import com.theater.ticketing.dto.response.VoucherResponse;
import com.theater.ticketing.service.VoucherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/vouchers")
@RequiredArgsConstructor
@Tag(name = "Voucher Management", description = "APIs for managing promotional vouchers")
@SecurityRequirement(name = "bearerAuth")
public class VoucherController {

    private final VoucherService voucherService;

    @GetMapping("/my")
    @Operation(summary = "My vouchers (CLIENT)", description = "Returns AVAILABLE vouchers assigned to the authenticated client")
    public ResponseEntity<ApiResponse<List<VoucherResponse>>> getMyVouchers(
            @AuthenticationPrincipal UserDetails userDetails) {
        List<VoucherResponse> vouchers = voucherService.getAvailableVouchersByCustomerId(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success(vouchers, "Your vouchers retrieved"));
    }

    @PostMapping
    @Operation(summary = "Create a voucher", description = "Creates a new promotional voucher with AVAILABLE status")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Voucher created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request body")
    })
    public ResponseEntity<ApiResponse<VoucherResponse>> createVoucher(@Valid @RequestBody CreateVoucherRequest request) {
        VoucherResponse response = voucherService.createVoucher(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Voucher created successfully"));
    }

    @GetMapping("/{voucherId}")
    @Operation(summary = "Get a voucher", description = "Retrieves the current status and details of a voucher")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Voucher retrieved"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Voucher not found")
    })
    public ResponseEntity<ApiResponse<VoucherResponse>> getVoucher(@PathVariable String voucherId) {
        return ResponseEntity.ok(ApiResponse.success(voucherService.getVoucher(voucherId), "Voucher retrieved"));
    }

    @GetMapping
    @Operation(summary = "List all vouchers", description = "Returns all vouchers currently in Redis")
    public ResponseEntity<ApiResponse<List<VoucherResponse>>> getAllVouchers() {
        return ResponseEntity.ok(ApiResponse.success(voucherService.getAllVouchers(), "Vouchers retrieved"));
    }

    @DeleteMapping("/{voucherId}")
    @Operation(summary = "Delete a voucher", description = "Removes a voucher from the system")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Voucher deleted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Voucher not found")
    })
    public ResponseEntity<Void> deleteVoucher(@PathVariable String voucherId) {
        voucherService.deleteVoucher(voucherId);
        return ResponseEntity.noContent().build();
    }
}
