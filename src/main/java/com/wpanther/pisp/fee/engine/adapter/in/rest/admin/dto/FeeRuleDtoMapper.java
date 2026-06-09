package com.wpanther.pisp.fee.engine.adapter.in.rest.admin.dto;

import com.wpanther.pisp.fee.engine.adapter.in.rest.dto.FeeCalculationResponse;
import com.wpanther.pisp.fee.engine.application.port.in.ManageFeeRulesUseCase;
import com.wpanther.pisp.fee.engine.application.port.out.FeeRuleDetails;
import com.wpanther.pisp.fee.engine.domain.model.*;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FeeRuleDtoMapper {

    public FeeRuleResponse toResponse(FeeRuleDetails details) {
        return new FeeRuleResponse(
                details.id(), details.paymentType(), details.scheme(), details.chargeBearer(),
                details.accountIdentification(), details.destinationCountry(),
                details.chargeType(), details.feeType(),
                details.flatAmount(), details.percentage(),
                details.minFee(), details.maxFee(),
                toTierDtoList(details.tiers()), details.currency(),
                details.priority(), details.active(), details.version(),
                details.createdAt(), details.createdBy(), details.updatedAt(), details.updatedBy());
    }

    public FeeRule toFeeRule(CreateFeeRuleRequest request) {
        List<Tier> tiers = request.tiers() != null
                ? request.tiers().stream().map(t -> new Tier(
                        t.min(), t.max(),
                        com.wpanther.pisp.fee.engine.domain.model.TierRateType.valueOf(t.rateType()),
                        t.amount(), t.percentage())).toList()
                : List.of();
        return new FeeRule(
                request.chargeType(), ChargeBearer.valueOf(request.chargeBearer()),
                FeeType.valueOf(request.feeType()),
                request.flatAmount(), request.percentage(),
                request.minFee(), request.maxFee(),
                tiers, request.currency(), request.destinationCountry(),
                request.priority() != null ? request.priority() : 0);
    }

    public FeeRequest toFeeRequest(DryRunRequest request) {
        var amount = request.instructedAmount();
        InstructedAmount instructedAmount = amount != null
                ? new InstructedAmount(amount.amount(), amount.currency())
                : null;
        ChargeBearer chargeBearer = ChargeBearer.valueOf(request.rule().chargeBearer());
        // Drools rules require a non-null account ref to fire (fee must land somewhere).
        // In dry-run the caller may omit accounts, so we inject a placeholder so the rule
        // always fires and the caller can see the calculated fee amount.
        AccountRef placeholder = new AccountRef("N/A", "N/A");
        AccountRef debtor = request.debtorAccount() != null
                ? new AccountRef(request.debtorAccount().schemeName(), request.debtorAccount().identification())
                : (chargeBearer == ChargeBearer.BorneByDebtor ? placeholder : null);
        AccountRef creditor = request.creditorAccount() != null
                ? new AccountRef(request.creditorAccount().schemeName(), request.creditorAccount().identification())
                : (chargeBearer == ChargeBearer.BorneByCreditor ? placeholder : null);
        // null is intentional — destinationCountry is not part of the Drools session input;
        // matching is done in Java before the session fires (dry-run bypasses matching entirely).
        return new FeeRequest(
                PaymentType.valueOf(request.rule().paymentType()),
                PaymentScheme.valueOf(request.rule().scheme()),
                chargeBearer,
                instructedAmount, debtor, creditor, null);
    }

    public ManageFeeRulesUseCase.CreateCommand toCreateCommand(CreateFeeRuleRequest request) {
        return new ManageFeeRulesUseCase.CreateCommand(
                request.paymentType(), request.scheme(), request.chargeBearer(),
                request.accountIdentification(), request.destinationCountry(),
                request.chargeType(), request.feeType(),
                request.flatAmount(), request.percentage(),
                request.minFee(), request.maxFee(),
                toTierInfoList(request.tiers()), request.currency(),
                request.priority() != null ? request.priority() : 0);
    }

    public ManageFeeRulesUseCase.UpdateCommand toUpdateCommand(UpdateFeeRuleRequest request, java.util.UUID id) {
        return new ManageFeeRulesUseCase.UpdateCommand(
                id, request.paymentType(), request.scheme(), request.chargeBearer(),
                request.accountIdentification(), request.destinationCountry(),
                request.chargeType(), request.feeType(),
                request.flatAmount(), request.percentage(),
                request.minFee(), request.maxFee(),
                toTierInfoList(request.tiers()), request.currency(),
                request.priority() != null ? request.priority() : 0,
                request.version());
    }

    public List<FeeCalculationResponse.ChargeDto> toChargeDtos(List<Charge> charges) {
        return charges.stream().map(c -> new FeeCalculationResponse.ChargeDto(
                c.chargeBearer().name(),
                c.chargeType(),
                new FeeCalculationResponse.AmountDto(
                        c.amount().amount().toPlainString(), c.amount().currency()),
                c.chargingParty() != null
                        ? new FeeCalculationResponse.AccountDto(
                                c.chargingParty().schemeName(), c.chargingParty().identification())
                        : null
        )).toList();
    }

    private List<FeeRuleDetails.TierInfo> toTierInfoList(List<TierDto> tiers) {
        if (tiers == null) return null;
        return tiers.stream()
                .map(t -> new FeeRuleDetails.TierInfo(t.min(), t.max(), t.rateType(),
                        t.amount(), t.percentage()))
                .toList();
    }

    private List<TierDto> toTierDtoList(List<FeeRuleDetails.TierInfo> tiers) {
        if (tiers == null) return null;
        return tiers.stream()
                .map(t -> new TierDto(t.min(), t.max(), t.rateType(), t.amount(), t.percentage()))
                .toList();
    }
}
