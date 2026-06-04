package com.wpanther.pisp.fee.engine.infrastructure.validation;

import com.wpanther.pisp.fee.engine.adapter.in.rest.admin.dto.FeeRuleRequest;
import com.wpanther.pisp.fee.engine.adapter.in.rest.admin.dto.TierDto;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.math.BigDecimal;
import java.util.Set;

public class FeeRuleRequestValidator implements ConstraintValidator<ValidFeeRule, FeeRuleRequest> {

    private static final Set<String> ALLOWED_CHARGE_BEARERS = Set.of("BorneByDebtor", "BorneByCreditor");
    private static final Set<String> INTERNATIONAL_PAYMENT_TYPES = Set.of(
            "INTERNATIONAL", "INTERNATIONAL_SCHEDULED", "INTERNATIONAL_STANDING_ORDER");

    @Override
    public boolean isValid(FeeRuleRequest req, ConstraintValidatorContext context) {
        if (req == null || req.feeType() == null) return false;

        if (req.chargeBearer() == null || !ALLOWED_CHARGE_BEARERS.contains(req.chargeBearer())) {
            return false;
        }

        if (!validateDestinationCountry(req)) return false;

        return switch (req.feeType()) {
            case "FLAT" -> validateFlat(req) && capsAbsent(req);
            case "PERCENTAGE" -> validatePercentage(req) && validateCaps(req);
            case "TIERED" -> validateTiered(req) && capsAbsent(req);
            case "FREE" -> validateFree(req) && capsAbsent(req);
            default -> false;
        };
    }

    private boolean validateDestinationCountry(FeeRuleRequest req) {
        if (req.destinationCountry() == null) return true;
        if (!req.destinationCountry().matches("^[A-Z]{2}$")) return false;
        return INTERNATIONAL_PAYMENT_TYPES.contains(req.paymentType());
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

    private boolean capsAbsent(FeeRuleRequest req) {
        return req.minFee() == null && req.maxFee() == null;
    }

    private boolean validateCaps(FeeRuleRequest req) {
        if (req.minFee() != null && req.minFee().compareTo(BigDecimal.ZERO) <= 0) return false;
        if (req.maxFee() != null && req.maxFee().compareTo(BigDecimal.ZERO) <= 0) return false;
        if (req.minFee() != null && req.maxFee() != null
                && req.minFee().compareTo(req.maxFee()) > 0) return false;
        return true;
    }
}
