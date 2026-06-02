package com.wpanther.pisp.fee.engine.domain.exception;

import java.util.UUID;

public class FeeRuleNotFoundException extends RuntimeException {
    public FeeRuleNotFoundException(UUID id) {
        super("FeeRule not found: " + id);
    }
}
