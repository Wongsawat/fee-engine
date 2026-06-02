package com.wpanther.pisp.fee.engine.adapter.in.rest.admin.dto;

import com.wpanther.pisp.fee.engine.adapter.in.rest.dto.FeeCalculationResponse;
import com.wpanther.pisp.fee.engine.application.port.in.ManageFeeRulesUseCase;
import com.wpanther.pisp.fee.engine.application.port.out.FeeRuleDetails;
import com.wpanther.pisp.fee.engine.domain.model.*;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FeeRuleDtoMapper {

    public FeeRuleDetails toDetails(CreateFeeRuleRequest request) {
        return new FeeRuleDetails(
                null, request.paymentType(), request.scheme(), request.chargeBearer(),
                request.accountIdentification(), request.chargeType(), request.feeType(),
                request.flatAmount(), request.percentage(),
                toTierInfoList(request.tiers()), request.currency(),
                true, 0, null, null, null, null);
    }

    public FeeRuleDetails toDetails(UpdateFeeRuleRequest request, java.util.UUID id,
                                     boolean currentActive, java.time.Instant createdAt,
                                     String createdBy) {
        return new FeeRuleDetails(
                id, request.paymentType(), request.scheme(), request.chargeBearer(),
                request.accountIdentification(), request.chargeType(), request.feeType(),
                request.flatAmount(), request.percentage(),
                toTierInfoList(request.tiers()), request.currency(),
                currentActive, request.version(), createdAt, createdBy, null, null);
    }

    public FeeRuleResponse toResponse(FeeRuleDetails details) {
        return new FeeRuleResponse(
                details.id(), details.paymentType(), details.scheme(), details.chargeBearer(),
                details.accountIdentification(), details.chargeType(), details.feeType(),
                details.flatAmount(), details.percentage(),
                toTierDtoList(details.tiers()), details.currency(),
                details.active(), details.version(),
                details.createdAt(), details.createdBy(), details.updatedAt(), details.updatedBy());
    }

    public FeeRule toFeeRule(CreateFeeRuleRequest request) {
        List<Tier> tiers = request.tiers() != null
                ? request.tiers().stream().map(t -> new Tier(t.min(), t.max(), t.amount())).toList()
                : List.of();
        return new FeeRule(
                request.chargeType(), ChargeBearer.valueOf(request.chargeBearer()),
                FeeType.valueOf(request.feeType()),
                request.flatAmount(), request.percentage(),
                tiers, request.currency());
    }

    public FeeRequest toFeeRequest(DryRunRequest request) {
        var amount = request.instructedAmount();
        InstructedAmount instructedAmount = amount != null
                ? new InstructedAmount(amount.amount(), amount.currency())
                : null;
        AccountRef debtor = request.debtorAccount() != null
                ? new AccountRef(request.debtorAccount().schemeName(), request.debtorAccount().identification())
                : null;
        AccountRef creditor = request.creditorAccount() != null
                ? new AccountRef(request.creditorAccount().schemeName(), request.creditorAccount().identification())
                : null;
        return new FeeRequest(
                PaymentType.valueOf(request.rule().paymentType()),
                PaymentScheme.valueOf(request.rule().scheme()),
                ChargeBearer.valueOf(request.rule().chargeBearer()),
                instructedAmount, debtor, creditor);
    }

    public ManageFeeRulesUseCase.CreateCommand toCreateCommand(CreateFeeRuleRequest request) {
        return new ManageFeeRulesUseCase.CreateCommand(
                request.paymentType(), request.scheme(), request.chargeBearer(),
                request.accountIdentification(), request.chargeType(), request.feeType(),
                request.flatAmount(), request.percentage(),
                toTierInfoList(request.tiers()), request.currency());
    }

    public ManageFeeRulesUseCase.UpdateCommand toUpdateCommand(UpdateFeeRuleRequest request, java.util.UUID id) {
        return new ManageFeeRulesUseCase.UpdateCommand(
                id, request.paymentType(), request.scheme(), request.chargeBearer(),
                request.accountIdentification(), request.chargeType(), request.feeType(),
                request.flatAmount(), request.percentage(),
                toTierInfoList(request.tiers()), request.currency(), request.version());
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
                .map(t -> new FeeRuleDetails.TierInfo(t.min(), t.max(), t.amount()))
                .toList();
    }

    private List<TierDto> toTierDtoList(List<FeeRuleDetails.TierInfo> tiers) {
        if (tiers == null) return null;
        return tiers.stream()
                .map(t -> new TierDto(t.min(), t.max(), t.amount()))
                .toList();
    }
}
