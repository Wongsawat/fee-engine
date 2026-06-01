package com.wpanther.pisp.fee.engine.adapter.in.rest;

import com.wpanther.pisp.fee.engine.adapter.in.rest.dto.FeeCalculationRequest;
import com.wpanther.pisp.fee.engine.adapter.in.rest.dto.FeeCalculationResponse;
import com.wpanther.pisp.fee.engine.application.port.in.CalculateFeesUseCase;
import com.wpanther.pisp.fee.engine.domain.model.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/fee-calculations")
public class FeeCalculationController {

    private final CalculateFeesUseCase calculateFeesUseCase;

    public FeeCalculationController(CalculateFeesUseCase calculateFeesUseCase) {
        this.calculateFeesUseCase = calculateFeesUseCase;
    }

    @PostMapping
    public ResponseEntity<FeeCalculationResponse> calculate(
            @RequestBody @Valid FeeCalculationRequest request) {

        var command = new CalculateFeesUseCase.Command(
                PaymentType.valueOf(request.paymentType()),
                PaymentScheme.valueOf(request.scheme()),
                ChargeBearer.valueOf(request.chargeBearer()),
                new InstructedAmount(request.instructedAmount().amount(),
                        request.instructedAmount().currency()),
                Optional.ofNullable(request.debtorAccount())
                        .map(a -> new AccountRef(a.schemeName(), a.identification())),
                Optional.ofNullable(request.creditorAccount())
                        .map(a -> new AccountRef(a.schemeName(), a.identification())));

        var charges = calculateFeesUseCase.calculate(command);

        var response = new FeeCalculationResponse(
                charges.stream().map(c -> new FeeCalculationResponse.ChargeDto(
                        c.chargeBearer().name(),
                        c.chargeType(),
                        new FeeCalculationResponse.AmountDto(
                                c.amount().amount().toPlainString(), c.amount().currency()),
                        c.chargingParty() != null
                                ? new FeeCalculationResponse.AccountDto(
                                        c.chargingParty().schemeName(), c.chargingParty().identification())
                                : null
                )).toList());

        return ResponseEntity.ok(response);
    }
}
