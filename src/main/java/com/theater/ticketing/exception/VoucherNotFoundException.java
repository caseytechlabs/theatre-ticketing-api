package com.theater.ticketing.exception;

public class VoucherNotFoundException extends RuntimeException {

    public VoucherNotFoundException(String voucherId) {
        super("Voucher not found with id: " + voucherId);
    }
}
