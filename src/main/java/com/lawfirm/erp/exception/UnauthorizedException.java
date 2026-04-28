package com.lawfirm.erp.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class UnauthorizedException extends RuntimeException {
    private final HttpStatus status;

    public UnauthorizedException(String message) {
        super(message);
        this.status = HttpStatus.UNAUTHORIZED;
    }

    public UnauthorizedException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }
}
