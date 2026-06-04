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
                null, null, "CHARGEType001", "FLAT", new BigDecimal("1.50"), null, null, null, null, "GBP");
        assertThat(validator.isValid(request, context)).isTrue();
    }

    @Test
    void flatRuleWithoutAmountIsInvalid() {
        var request = new CreateFeeRuleRequest("DOMESTIC", "FPS", "BorneByDebtor",
                null, null, "CHARGEType001", "FLAT", null, null, null, null, null, "GBP");
        assertThat(validator.isValid(request, context)).isFalse();
    }

    @Test
    void flatRuleWithZeroAmountIsInvalid() {
        var request = new CreateFeeRuleRequest("DOMESTIC", "FPS", "BorneByDebtor",
                null, null, "CHARGEType001", "FLAT", BigDecimal.ZERO, null, null, null, null, "GBP");
        assertThat(validator.isValid(request, context)).isFalse();
    }

    @Test
    void validPercentageRule() {
        var request = new CreateFeeRuleRequest("DOMESTIC", "FPS", "BorneByDebtor",
                null, null, "CHARGEType002", "PERCENTAGE", null, new BigDecimal("0.002"), null, null, null, "GBP");
        assertThat(validator.isValid(request, context)).isTrue();
    }

    @Test
    void percentageOverOneIsInvalid() {
        var request = new CreateFeeRuleRequest("DOMESTIC", "FPS", "BorneByDebtor",
                null, null, "CHARGEType002", "PERCENTAGE", null, new BigDecimal("1.5"), null, null, null, "GBP");
        assertThat(validator.isValid(request, context)).isFalse();
    }

    @Test
    void percentageWithoutRateIsInvalid() {
        var request = new CreateFeeRuleRequest("DOMESTIC", "FPS", "BorneByDebtor",
                null, null, "CHARGEType002", "PERCENTAGE", null, null, null, null, null, "GBP");
        assertThat(validator.isValid(request, context)).isFalse();
    }

    @Test
    void validTieredRule() {
        var tiers = List.of(new TierDto(new BigDecimal("0"), new BigDecimal("1000"), new BigDecimal("0.50")));
        var request = new CreateFeeRuleRequest("DOMESTIC", "FPS", "BorneByDebtor",
                null, null, "CHARGEType003", "TIERED", null, null, null, null, tiers, "GBP");
        assertThat(validator.isValid(request, context)).isTrue();
    }

    @Test
    void tieredRuleWithoutTiersIsInvalid() {
        var request = new CreateFeeRuleRequest("DOMESTIC", "FPS", "BorneByDebtor",
                null, null, "CHARGEType003", "TIERED", null, null, null, null, null, "GBP");
        assertThat(validator.isValid(request, context)).isFalse();
    }

    @Test
    void tierWithMinGreaterOrEqualToMaxIsInvalid() {
        var tiers = List.of(new TierDto(new BigDecimal("1000"), new BigDecimal("100"), new BigDecimal("0.50")));
        var request = new CreateFeeRuleRequest("DOMESTIC", "FPS", "BorneByDebtor",
                null, null, "CHARGEType003", "TIERED", null, null, null, null, tiers, "GBP");
        assertThat(validator.isValid(request, context)).isFalse();
    }

    @Test
    void validFreeRule() {
        var request = new CreateFeeRuleRequest("DOMESTIC", "FPS", "BorneByDebtor",
                null, null, "CHARGEType004", "FREE", null, null, null, null, null, "GBP");
        assertThat(validator.isValid(request, context)).isTrue();
    }

    @Test
    void freeRuleWithFlatAmountIsInvalid() {
        var request = new CreateFeeRuleRequest("DOMESTIC", "FPS", "BorneByDebtor",
                null, null, "CHARGEType004", "FREE", new BigDecimal("1.00"), null, null, null, null, "GBP");
        assertThat(validator.isValid(request, context)).isFalse();
    }

    @Test
    void sharedChargeBearerIsInvalid() {
        var request = new CreateFeeRuleRequest("DOMESTIC", "FPS", "Shared",
                null, null, "CHARGEType001", "FLAT", new BigDecimal("1.50"), null, null, null, null, "GBP");
        assertThat(validator.isValid(request, context)).isFalse();
    }

    @Test
    void followingServiceLevelChargeBearerIsInvalid() {
        var request = new CreateFeeRuleRequest("DOMESTIC", "FPS", "FollowingServiceLevel",
                null, null, "CHARGEType001", "FLAT", new BigDecimal("1.50"), null, null, null, null, "GBP");
        assertThat(validator.isValid(request, context)).isFalse();
    }

    @Test
    void worksForUpdateFeeRuleRequest() {
        var request = new UpdateFeeRuleRequest("DOMESTIC", "FPS", "BorneByDebtor",
                null, null, "CHARGEType001", "FLAT", new BigDecimal("1.50"), null, null, null, null, "GBP", 0L);
        assertThat(validator.isValid(request, context)).isTrue();
    }

    @Test
    void validPercentageRuleWithCaps() {
        var request = new CreateFeeRuleRequest("DOMESTIC", "FPS", "BorneByDebtor",
                null, null, "CHARGEType002", "PERCENTAGE", null, new BigDecimal("0.01"),
                new BigDecimal("1.00"), new BigDecimal("50.00"), null, "GBP");
        assertThat(validator.isValid(request, context)).isTrue();
    }

    @Test
    void percentageRuleWithFloorOnlyIsValid() {
        var request = new CreateFeeRuleRequest("DOMESTIC", "FPS", "BorneByDebtor",
                null, null, "CHARGEType002", "PERCENTAGE", null, new BigDecimal("0.01"),
                new BigDecimal("1.00"), null, null, "GBP");
        assertThat(validator.isValid(request, context)).isTrue();
    }

    @Test
    void percentageWithMinGreaterThanMaxIsInvalid() {
        var request = new CreateFeeRuleRequest("DOMESTIC", "FPS", "BorneByDebtor",
                null, null, "CHARGEType002", "PERCENTAGE", null, new BigDecimal("0.01"),
                new BigDecimal("50.00"), new BigDecimal("1.00"), null, "GBP");
        assertThat(validator.isValid(request, context)).isFalse();
    }

    @Test
    void percentageWithNonPositiveMinFeeIsInvalid() {
        var request = new CreateFeeRuleRequest("DOMESTIC", "FPS", "BorneByDebtor",
                null, null, "CHARGEType002", "PERCENTAGE", null, new BigDecimal("0.01"),
                BigDecimal.ZERO, null, null, "GBP");
        assertThat(validator.isValid(request, context)).isFalse();
    }

    @Test
    void flatRuleWithCapsIsInvalid() {
        var request = new CreateFeeRuleRequest("DOMESTIC", "FPS", "BorneByDebtor",
                null, null, "CHARGEType001", "FLAT", new BigDecimal("1.50"), null,
                new BigDecimal("1.00"), null, null, "GBP");
        assertThat(validator.isValid(request, context)).isFalse();
    }

    @Test
    void tieredRuleWithCapsIsInvalid() {
        var tiers = List.of(new TierDto(new BigDecimal("0"), new BigDecimal("1000"), new BigDecimal("0.50")));
        var request = new CreateFeeRuleRequest("DOMESTIC", "FPS", "BorneByDebtor",
                null, null, "CHARGEType003", "TIERED", null, null,
                null, new BigDecimal("5.00"), tiers, "GBP");
        assertThat(validator.isValid(request, context)).isFalse();
    }

    @Test
    void freeRuleWithCapsIsInvalid() {
        var request = new CreateFeeRuleRequest("DOMESTIC", "FPS", "BorneByDebtor",
                null, null, "CHARGEType004", "FREE", null, null,
                new BigDecimal("1.00"), null, null, "GBP");
        assertThat(validator.isValid(request, context)).isFalse();
    }

    @Test
    void validInternationalRuleWithDestinationCountry() {
        var request = new CreateFeeRuleRequest("INTERNATIONAL", "SWIFT", "BorneByDebtor",
                null, "IN", "CHARGEType001", "FLAT", new BigDecimal("1.50"),
                null, null, null, null, "USD");
        assertThat(validator.isValid(request, context)).isTrue();
    }

    @Test
    void rejectsLowercaseDestinationCountry() {
        var request = new CreateFeeRuleRequest("INTERNATIONAL", "SWIFT", "BorneByDebtor",
                null, "in", "CHARGEType001", "FLAT", new BigDecimal("1.50"),
                null, null, null, null, "USD");
        assertThat(validator.isValid(request, context)).isFalse();
    }

    @Test
    void rejectsThreeLetterDestinationCountry() {
        var request = new CreateFeeRuleRequest("INTERNATIONAL", "SWIFT", "BorneByDebtor",
                null, "GBR", "CHARGEType001", "FLAT", new BigDecimal("1.50"),
                null, null, null, null, "USD");
        assertThat(validator.isValid(request, context)).isFalse();
    }

    @Test
    void rejectsDestinationCountryOnDomesticRule() {
        var request = new CreateFeeRuleRequest("DOMESTIC", "FPS", "BorneByDebtor",
                null, "IN", "CHARGEType001", "FLAT", new BigDecimal("1.50"),
                null, null, null, null, "GBP");
        assertThat(validator.isValid(request, context)).isFalse();
    }

    @Test
    void acceptsNullDestinationCountryOnDomesticRule() {
        var request = new CreateFeeRuleRequest("DOMESTIC", "FPS", "BorneByDebtor",
                null, null, "CHARGEType001", "FLAT", new BigDecimal("1.50"),
                null, null, null, null, "GBP");
        assertThat(validator.isValid(request, context)).isTrue();
    }

    @Test
    void acceptsDestinationCountryOnInternationalScheduledRule() {
        var request = new CreateFeeRuleRequest("INTERNATIONAL_SCHEDULED", "SWIFT", "BorneByDebtor",
                null, "FR", "CHARGEType001", "FLAT", new BigDecimal("1.50"),
                null, null, null, null, "EUR");
        assertThat(validator.isValid(request, context)).isTrue();
    }
}
