package com.wpanther.pisp.fee.engine.domain.model;

/**
 * OB spec OBChargeBearerType1Code.
 * Shared: decomposed at service layer into BorneByDebtor + BorneByCreditor lookups; never stored in DB.
 * FollowingServiceLevel: short-circuits to empty charges immediately; never stored in DB.
 */
public enum ChargeBearer {
    BorneByDebtor, BorneByCreditor, Shared, FollowingServiceLevel
}
