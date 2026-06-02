package com.wpanther.pisp.fee.engine.infrastructure.validation;

import com.wpanther.pisp.fee.engine.adapter.in.rest.admin.dto.FeeRuleRequest;
import com.wpanther.pisp.fee.engine.adapter.in.rest.admin.dto.TierDto;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.math.BigDecimal;
import java.util.Set;

public class FeeRuleRequestValidator implements ConstraintValidator<ValidFeeRule, FeeRuleRequest> {

    private static final Set<String> ALLOWED_CHARGE_BEARERS = Set.of("BorneByDebtor", "BorneByCreditor");

    @Override
    public boolean isValid(FeeRuleRequest req, ConstraintValidatorContext context) {
        if (req == null || req.feeType() == null) return false;

        if (req.chargeBearer() == null || !ALLOWED_CHARGE_BEARERS.contains(req.chargeBearer())) {
            return false;
        }

        return switch (req.feeType()) {
            case "FLAT" -> validateFlat(req);
            case "PERCENTAGE" -> validatePercentage(req);
            case "TIERED" -> validateTiered(req);
            case "FREE" -> validateFree(req);
            default -> false;
        };
    }

    private boolean validateFlat(FeeRuleRequest req) {
        return req.flatAmount() != null && req.flatAmount().compareTo(BigDecimal.ZERO) > 0;
    }

    private boolean validatePercentage(FeeRuleRequest req) {
        return req.percentage() != null
                && req.percentage().compareTo(BigDecimal.ZERO) > 0
                && req.percentage().compareTo(BigDecimal.ONE) <= 0;
    }

    private boolean validateTiered(FeeRuleRequest req) {
        if (req.tiers() == null || req.tiers().isEmpty()) return false;
        for (TierDto t : req.tiers()) {
            if (t.min().compareTo(t.max()) >= 0) return false;
            if (t.amount().compareTo(BigDecimal.ZERO) <= 0) return false;
        }
        return true;
    }

    private boolean validateFree(FeeRuleRequest req) {
        return req.flatAmount() == null && req.percentage() == null
                && (req.tiers() == null || req.tiers().isEmpty());
    }
}
