package com.wpanther.pisp.fee.engine.adapter.in.rest;

import com.wpanther.pisp.fee.engine.application.port.in.CalculateFeesUseCase;
import com.wpanther.pisp.fee.engine.domain.model.*;
import com.wpanther.pisp.fee.engine.infrastructure.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FeeCalculationController.class)
@Import(SecurityConfig.class)
class FeeCalculationControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean CalculateFeesUseCase calculateFeesUseCase;

    @Test
    void returnsChargesOnValidRequest() throws Exception {
        when(calculateFeesUseCase.calculate(any())).thenReturn(List.of(
                new Charge(ChargeBearer.BorneByDebtor, "CHARGEType001",
                        new InstructedAmount(new BigDecimal("1.50"), "GBP"),
                        new AccountRef("SortCodeAccountNumber", "12345678901234"))));

        mockMvc.perform(post("/fee-calculations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "paymentType": "DOMESTIC",
                              "scheme": "FPS",
                              "chargeBearer": "BorneByDebtor",
                              "instructedAmount": { "amount": "100.00", "currency": "GBP" },
                              "debtorAccount": { "schemeName": "SortCodeAccountNumber",
                                                 "identification": "12345678901234" }
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.charges[0].chargeBearer").value("BorneByDebtor"))
                .andExpect(jsonPath("$.charges[0].type").value("CHARGEType001"))
                .andExpect(jsonPath("$.charges[0].amount.amount").value("1.50"))
                .andExpect(jsonPath("$.charges[0].amount.currency").value("GBP"))
                .andExpect(jsonPath("$.charges[0].chargingParty.identification")
                        .value("12345678901234"));
    }

    @Test
    void returnsEmptyChargesWhenNoRulesMatch() throws Exception {
        when(calculateFeesUseCase.calculate(any())).thenReturn(List.of());

        mockMvc.perform(post("/fee-calculations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "paymentType": "DOMESTIC",
                              "scheme": "FPS",
                              "chargeBearer": "BorneByDebtor",
                              "instructedAmount": { "amount": "100.00", "currency": "GBP" }
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.charges").isEmpty());
    }

    @Test
    void returnsFreeChargeWithZeroAmount() throws Exception {
        when(calculateFeesUseCase.calculate(any())).thenReturn(List.of(
                new Charge(ChargeBearer.BorneByDebtor, "CHARGEType004",
                        new InstructedAmount(new BigDecimal("0.00"), "GBP"),
                        new AccountRef("SortCodeAccountNumber", "12345678901234"))));

        mockMvc.perform(post("/fee-calculations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "paymentType": "DOMESTIC",
                              "scheme": "FPS",
                              "chargeBearer": "BorneByDebtor",
                              "instructedAmount": { "amount": "100.00", "currency": "GBP" },
                              "debtorAccount": { "schemeName": "SortCodeAccountNumber",
                                                 "identification": "12345678901234" }
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.charges[0].chargeBearer").value("BorneByDebtor"))
                .andExpect(jsonPath("$.charges[0].type").value("CHARGEType004"))
                .andExpect(jsonPath("$.charges[0].amount.amount").value("0.00"))
                .andExpect(jsonPath("$.charges[0].amount.currency").value("GBP"))
                .andExpect(jsonPath("$.charges[0].chargingParty.identification").value("12345678901234"));
    }

    @Test
    void returns400WhenPaymentTypeIsMissing() throws Exception {
        mockMvc.perform(post("/fee-calculations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "scheme": "FPS",
                              "chargeBearer": "BorneByDebtor",
                              "instructedAmount": { "amount": "100.00", "currency": "GBP" }
                            }
                            """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returns400WhenChargeBearerIsInvalid() throws Exception {
        mockMvc.perform(post("/fee-calculations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "paymentType": "DOMESTIC",
                              "scheme": "FPS",
                              "chargeBearer": "INVALID_BEARER",
                              "instructedAmount": { "amount": "100.00", "currency": "GBP" }
                            }
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid request parameter value"));
    }

    @Test
    void passesDestinationCountryFromRequestToUseCase() throws Exception {
        when(calculateFeesUseCase.calculate(any())).thenReturn(List.of());

        mockMvc.perform(post("/fee-calculations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "paymentType": "INTERNATIONAL",
                              "scheme": "SWIFT",
                              "chargeBearer": "BorneByDebtor",
                              "instructedAmount": { "amount": "500.00", "currency": "USD" },
                              "destinationCountry": "IN"
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.charges").isEmpty());

        var captor = org.mockito.ArgumentCaptor.forClass(CalculateFeesUseCase.Command.class);
        verify(calculateFeesUseCase).calculate(captor.capture());
        assertThat(captor.getValue().destinationCountry()).contains("IN");
    }
}
