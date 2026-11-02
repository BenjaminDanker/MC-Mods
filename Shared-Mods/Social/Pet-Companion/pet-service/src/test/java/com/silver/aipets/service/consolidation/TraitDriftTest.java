package com.silver.aipets.service.consolidation;

import com.silver.aipets.common.domain.TraitName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TraitDriftTest {
    @Test
    void temperamentDriftsOnePointAtATimeUpToThreePerDay() {
        assertEquals(1, JdbcConsolidationRepository.boundedDelta(TraitName.BOLDNESS, 50, 0));
        assertEquals(-1, JdbcConsolidationRepository.boundedDelta(TraitName.BOLDNESS, -50, 0));
        assertEquals(0, JdbcConsolidationRepository.boundedDelta(TraitName.BOLDNESS, 1, 3));
    }

    @Test
    void trustIsBuiltSlowlyAndLostQuickly() {
        assertEquals(1, JdbcConsolidationRepository.boundedDelta(TraitName.TRUST, 100, 0));
        assertEquals(-3, JdbcConsolidationRepository.boundedDelta(TraitName.TRUST, -100, 0));
        assertEquals(1, JdbcConsolidationRepository.boundedDelta(TraitName.SECURITY, 2, 0));
        assertEquals(-3, JdbcConsolidationRepository.boundedDelta(TraitName.SECURITY, -3, 0));
    }

    @Test
    void theDailyTotalStillBoundsRelationshipChange() {
        assertEquals(-2, JdbcConsolidationRepository.boundedDelta(TraitName.TRUST, -100, 4));
        assertEquals(0, JdbcConsolidationRepository.boundedDelta(TraitName.TRUST, -100, 6));
        assertEquals(2, JdbcConsolidationRepository.boundedDelta(TraitName.ATTACHMENT, 5, 0));
    }

    @Test
    void aPetInItsFirstWeekImprintsFasterOnRelationshipTraitsOnly() {
        assertEquals(2, JdbcConsolidationRepository.boundedDelta(TraitName.TRUST, 100, 0, true));
        assertEquals(3, JdbcConsolidationRepository.boundedDelta(TraitName.ATTACHMENT, 100, 0, true));
        assertEquals(1, JdbcConsolidationRepository.boundedDelta(TraitName.BOLDNESS, 100, 0, true));
        assertEquals(2, JdbcConsolidationRepository.boundedDelta(TraitName.TRUST, 100, 6, true));
        assertEquals(0, JdbcConsolidationRepository.boundedDelta(TraitName.TRUST, 100, 6, false));
        assertEquals(-3, JdbcConsolidationRepository.boundedDelta(TraitName.TRUST, -100, 0, true));
    }
}
