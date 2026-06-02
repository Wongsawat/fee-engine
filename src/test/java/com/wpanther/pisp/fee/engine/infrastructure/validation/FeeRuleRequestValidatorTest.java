package com.wpanther.pisp.fee.engine.infrastructure.validation;

import com.wpanther.pisp.fee.engine.adapter.in.rest.admin.dto.*;
import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class FeeRuleRequestValidatorTest {

    private final FeeRuleRequestValidator validator = new FeeRuleRequestValidator();
    private final ConstraintValidatorContext context = mock(ConstraintValidatorContext.class);

    @Test
    void validFlatRule() {
        var request = new CreateFeeRuleRequest("DOMESTIC", "FPS", "BorneByDebtor",
                null, "CHARGEType001", "FLAT", new BigDecimal("1.50"), null, null, "GBP");
        assertThat(validator.isValid(request, context)).isTrue();
    }

    @Test
    void flatRuleWithoutAmountIsInvalid() {
        var request = new CreateFeeRuleRequest("DOMESTIC", "FPS", "BorneByDebtor",
                null, "CHARGEType001", "FLAT", null, null, null, "GBP");
        assertThat(validator.isValid(request, context)).isFalse();
    }

    @Test
    void flatRuleWithZeroAmountIsInvalid() {
        var request = new CreateFeeRuleRequest("DOMESTIC", "FPS", "BorneByDebtor",
                null, "CHARGEType001", "FLAT", BigDecimal.ZERO, null, null, "GBP");
        assertThat(validator.isValid(request, context)).isFalse();
    }

    @Test
    void validPercentageRule() {
        var request = new CreateFeeRuleRequest("DOMESTIC", "FPS", "BorneByDebtor",
                null, "CHARGEType002", "PERCENTAGE", null, new BigDecimal("0.002"), null, "GBP");
        assertThat(validator.isValid(request, context)).isTrue();
    }

    @Test
    void percentageOverOneIsInvalid() {
        var request = new CreateFeeRuleRequest("DOMESTIC", "FPS", "BorneByDebtor",
                null, "CHARGEType002", "PERCENTAGE", null, new BigDecimal("1.5"), null, "GBP");
        assertThat(validator.isValid(request, context)).isFalse();
    }

    @Test
    void percentageWithoutRateIsInvalid() {
        var request = new CreateFeeRuleRequest("DOMESTIC", "FPS", "BorneByDebtor",
                null, "CHARGEType002", "PERCENTAGE", null, null, null, "GBP");
        assertThat(validator.isValid(request, context)).isFalse();
    }

    @Test
    void validTieredRule() {
        var tiers = List.of(new TierDto(new BigDecimal("0"), new BigDecimal("1000"), new BigDecimal("0.50")));
        var request = new CreateFeeRuleRequest("DOMESTIC", "FPS", "BorneByDebtor",
                null, "CHARGEType003", "TIERED", null, null, tiers, "GBP");
        assertThat(validator.isValid(request, context)).isTrue();
    }

    @Test
    void tieredRuleWithoutTiersIsInvalid() {
        var request = new CreateFeeRuleRequest("DOMESTIC", "FPS", "BorneByDebtor",
                null, "CHARGEType003", "TIERED", null, null, null, "GBP");
        assertThat(validator.isValid(request, context)).isFalse();
    }

    @Test
    void tierWithMinGreaterOrEqualToMaxIsInvalid() {
        var tiers = List.of(new TierDto(new BigDecimal("1000"), new BigDecimal("100"), new BigDecimal("0.50")));
        var request = new CreateFeeRuleRequest("DOMESTIC", "FPS", "BorneByDebtor",
                null, "CHARGEType003", "TIERED", null, null, tiers, "GBP");
        assertThat(validator.isValid(request, context)).isFalse();
    }

    @Test
    void validFreeRule() {
        var request = new CreateFeeRuleRequest("DOMESTIC", "FPS", "BorneByDebtor",
                null, "CHARGEType004", "FREE", null, null, null, "GBP");
        assertThat(validator.isValid(request, context)).isTrue();
    }

    @Test
    void freeRuleWithFlatAmountIsInvalid() {
        var request = new CreateFeeRuleRequest("DOMESTIC", "FPS", "BorneByDebtor",
                null, "CHARGEType004", "FREE", new BigDecimal("1.00"), null, null, "GBP");
        assertThat(validator.isValid(request, context)).isFalse();
    }

    @Test
    void sharedChargeBearerIsInvalid() {
        var request = new CreateFeeRuleRequest("DOMESTIC", "FPS", "Shared",
                null, "CHARGEType001", "FLAT", new BigDecimal("1.50"), null, null, "GBP");
        assertThat(validator.isValid(request, context)).isFalse();
    }

    @Test
    void followingServiceLevelChargeBearerIsInvalid() {
        var request = new CreateFeeRuleRequest("DOMESTIC", "FPS", "FollowingServiceLevel",
                null, "CHARGEType001", "FLAT", new BigDecimal("1.50"), null, null, "GBP");
        assertThat(validator.isValid(request, context)).isFalse();
    }

    @Test
    void worksForUpdateFeeRuleRequest() {
        var request = new UpdateFeeRuleRequest("DOMESTIC", "FPS", "BorneByDebtor",
                null, "CHARGEType001", "FLAT", new BigDecimal("1.50"), null, null, "GBP", 0L);
        assertThat(validator.isValid(request, context)).isTrue();
    }
}
