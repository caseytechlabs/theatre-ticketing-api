package com.theater.ticketing.exception;

import com.theater.ticketing.model.VoucherStatus;

public class VoucherNotAvailableException extends RuntimeException {

    public VoucherNotAvailableException(String voucherId, VoucherStatus currentStatus) {
        super("Voucher " + voucherId + " is not available. Current status: " + currentStatus);
    }
}
