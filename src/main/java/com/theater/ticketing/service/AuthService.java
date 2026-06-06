package com.theater.ticketing.service;

import com.theater.ticketing.dto.request.LoginRequest;
import com.theater.ticketing.dto.response.LoginResponse;

public interface AuthService {
    LoginResponse login(LoginRequest request);
}
