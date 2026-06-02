package com.wpanther.pisp.fee.engine.infrastructure.error;

import com.wpanther.pisp.fee.engine.domain.exception.FeeRuleNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        var detail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        detail.setDetail("Request validation failed");
        return detail;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        var detail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        detail.setDetail("Invalid request parameter value");
        return detail;
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegalState(IllegalStateException ex) {
        var detail = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        detail.setDetail("Fee calculation failed due to invalid rule configuration");
        return detail;
    }

    @ExceptionHandler(FeeRuleNotFoundException.class)
    public ProblemDetail handleNotFound(FeeRuleNotFoundException ex) {
        var detail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        detail.setDetail(ex.getMessage());
        return detail;
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        var detail = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        detail.setDetail("Concurrent update detected. Refresh and retry.");
        return detail;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        String message = ex.getMessage();
        if (message != null && message.contains("uniq_active_fee_rules")) {
            var detail = ProblemDetail.forStatus(HttpStatus.CONFLICT);
            detail.setDetail("Active fee rule already exists for this combination");
            return detail;
        }
        var detail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        String causeMsg = ex.getMostSpecificCause().getMessage();
        detail.setDetail(causeMsg != null ? causeMsg : "Data integrity violation");
        return detail;
    }
}
