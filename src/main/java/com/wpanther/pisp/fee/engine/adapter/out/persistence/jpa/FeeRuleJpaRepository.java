package com.wpanther.pisp.fee.engine.adapter.out.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface FeeRuleJpaRepository extends JpaRepository<FeeRuleEntity, UUID> {

    @Query("SELECT f FROM FeeRuleEntity f WHERE f.paymentType = :paymentType " +
           "AND f.scheme = :scheme AND f.chargeBearer = :chargeBearer " +
           "AND f.currency = :currency AND f.active = true")
    List<FeeRuleEntity> findActive(@Param("paymentType") String paymentType,
                                   @Param("scheme") String scheme,
                                   @Param("chargeBearer") String chargeBearer,
                                   @Param("currency") String currency);
}
