package com.wpanther.pisp.fee.engine.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.pisp.fee.engine.support.PostgresTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class TieredFeeCalculationIntegrationTest extends PostgresTestSupport {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void tieredStep_percentageTiers_accumulatesAcrossBrackets() throws Exception {
        String createBody = """
            {
              "paymentType": "DOMESTIC",
              "scheme": "FPS",
              "chargeBearer": "BorneByDebtor",
              "chargeType": "INTEG_STEP_FEE",
              "feeType": "TIERED_STEP",
              "currency": "GBP",
              "tiers": [
                {"min": 0, "max": 10000, "rateType": "PERCENTAGE", "percentage": 0.03},
                {"min": 10000, "max": 50000, "rateType": "PERCENTAGE", "percentage": 0.02},
                {"min": 50000, "max": 999999999, "rateType": "PERCENTAGE", "percentage": 0.01}
              ]
            }
            """;

        mockMvc.perform(post("/admin/fee-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody)
                        .with(jwt().jwt(j -> j.claim("sub", "test-admin"))
                                .authorities(() -> "SCOPE_fee-rules:write")))
                .andExpect(status().isCreated());

        String calcBody = """
            {
              "paymentType": "DOMESTIC",
              "scheme": "FPS",
              "chargeBearer": "BorneByDebtor",
              "instructedAmount": {"amount": 60000.00, "currency": "GBP"},
              "debtorAccount": {"schemeName": "SortCodeAccountNumber", "identification": "12345678901234"}
            }
            """;

        mockMvc.perform(post("/fee-calculations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(calcBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.charges[0].type").value("INTEG_STEP_FEE"))
                .andExpect(jsonPath("$.charges[0].amount.amount").value("1200.00"));
    }

    @Test
    void tieredSlab_hybridTier_appliesBasePlusPercentageToFullAmount() throws Exception {
        String createBody = """
            {
              "paymentType": "DOMESTIC",
              "scheme": "BACS",
              "chargeBearer": "BorneByDebtor",
              "chargeType": "INTEG_SLAB_HYBRID",
              "feeType": "TIERED_SLAB",
              "currency": "GBP",
              "tiers": [
                {"min": 0, "max": 999999999, "rateType": "HYBRID", "amount": 2.00, "percentage": 0.005}
              ]
            }
            """;

        mockMvc.perform(post("/admin/fee-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody)
                        .with(jwt().jwt(j -> j.claim("sub", "test-admin"))
                                .authorities(() -> "SCOPE_fee-rules:write")))
                .andExpect(status().isCreated());

        String calcBody = """
            {
              "paymentType": "DOMESTIC",
              "scheme": "BACS",
              "chargeBearer": "BorneByDebtor",
              "instructedAmount": {"amount": 1000.00, "currency": "GBP"},
              "debtorAccount": {"schemeName": "SortCodeAccountNumber", "identification": "12345678901234"}
            }
            """;

        mockMvc.perform(post("/fee-calculations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(calcBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.charges[0].type").value("INTEG_SLAB_HYBRID"))
                .andExpect(jsonPath("$.charges[0].amount.amount").value("7.00"));
    }

    @Test
    void dryRun_tieredStep_returnsAccumulatedCharge() throws Exception {
        String dryRunBody = """
            {
              "rule": {
                "paymentType": "DOMESTIC",
                "scheme": "FPS",
                "chargeBearer": "BorneByDebtor",
                "chargeType": "DRY_STEP",
                "feeType": "TIERED_STEP",
                "currency": "GBP",
                "tiers": [
                  {"min": 0, "max": 10000, "rateType": "PERCENTAGE", "percentage": 0.03},
                  {"min": 10000, "max": 999999999, "rateType": "PERCENTAGE", "percentage": 0.01}
                ]
              },
              "instructedAmount": {"amount": 20000.00, "currency": "GBP"},
              "debtorAccount": {"schemeName": "SortCodeAccountNumber", "identification": "12345678901234"}
            }
            """;

        mockMvc.perform(post("/admin/fee-rules/dry-run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dryRunBody)
                        .with(jwt().jwt(j -> j.claim("sub", "test-admin"))
                                .authorities(() -> "SCOPE_fee-rules:write")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.charges[0].type").value("DRY_STEP"))
                .andExpect(jsonPath("$.charges[0].amount.amount").value("400.00"));
    }
}
