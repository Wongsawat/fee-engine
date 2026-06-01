package com.wpanther.pisp.fee.engine.application.port.in;

import com.wpanther.pisp.fee.engine.domain.model.*;
import java.util.List;
import java.util.Optional;

public interface CalculateFeesUseCase {

    record Command(
        PaymentType paymentType,
        PaymentScheme scheme,
        ChargeBearer chargeBearer,
        InstructedAmount instructedAmount,
        Optional<AccountRef> debtorAccount,
        Optional<AccountRef> creditorAccount
    ) {}

    List<Charge> calculate(Command command);
}
