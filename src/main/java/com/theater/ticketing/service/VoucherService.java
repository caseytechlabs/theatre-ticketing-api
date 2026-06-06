package com.theater.ticketing.service;

import com.theater.ticketing.dto.request.CreateVoucherRequest;
import com.theater.ticketing.dto.response.VoucherResponse;

import java.util.List;

public interface VoucherService {
    VoucherResponse createVoucher(CreateVoucherRequest request);
    VoucherResponse getVoucher(String voucherId);
    List<VoucherResponse> getAllVouchers();
    List<VoucherResponse> getAvailableVouchersByCustomerId(String customerId);
    void deleteVoucher(String voucherId);
}
