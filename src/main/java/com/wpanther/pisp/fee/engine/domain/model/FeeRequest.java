package com.wpanther.pisp.fee.engine.domain.model;

import java.util.Optional;

public final class FeeRequest {
    private final PaymentType paymentType;
    private final PaymentScheme scheme;
    private final ChargeBearer chargeBearer;
    private final InstructedAmount instructedAmount;
    private final AccountRef debtorAccount;    // nullable
    private final AccountRef creditorAccount;  // nullable
    private final String destinationCountry;

    public FeeRequest(PaymentType paymentType, PaymentScheme scheme, ChargeBearer chargeBearer,
                      InstructedAmount instructedAmount,
                      AccountRef debtorAccount, AccountRef creditorAccount,
                      String destinationCountry) {
        this.paymentType = paymentType;
        this.scheme = scheme;
        this.chargeBearer = chargeBearer;
        this.instructedAmount = instructedAmount;
        this.debtorAccount = debtorAccount;
        this.creditorAccount = creditorAccount;
        this.destinationCountry = destinationCountry;
    }

    public PaymentType getPaymentType()                    { return paymentType; }
    public PaymentScheme getScheme()                       { return scheme; }
    public ChargeBearer getChargeBearer()                  { return chargeBearer; }
    public InstructedAmount getInstructedAmount()          { return instructedAmount; }
    public AccountRef getDebtorAccountOrNull()             { return debtorAccount; }
    public AccountRef getCreditorAccountOrNull()           { return creditorAccount; }
    public Optional<AccountRef> getDebtorAccount()         { return Optional.ofNullable(debtorAccount); }
    public Optional<AccountRef> getCreditorAccount()       { return Optional.ofNullable(creditorAccount); }
    public Optional<String> getDestinationCountry()        { return Optional.ofNullable(destinationCountry); }
}
