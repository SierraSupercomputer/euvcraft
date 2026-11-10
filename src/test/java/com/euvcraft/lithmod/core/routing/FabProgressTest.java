package com.euvcraft.lithmod.core.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link FabProgress}.
 */
class FabProgressTest {

    @Nested
    @DisplayName("Constructor validation")
    class ConstructorTest {

        @Test
        @DisplayName("rejects null stage")
        void nullStageRejected() {
            assertThrows(NullPointerException.class, () -> new FabProgress(null, 0, FabProgress.CURRENT_VERSION));
        }

        @Test
        @DisplayName("rejects version other than 1")
        void wrongVersionRejected() {
            assertThrows(IllegalArgumentException.class, () -> new FabProgress(FabProgress.Stage.UNPROCESSED, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> new FabProgress(FabProgress.Stage.UNPROCESSED, 0, 2));
            assertThrows(IllegalArgumentException.class, () -> new FabProgress(FabProgress.Stage.UNPROCESSED, 0, -1));
        }

        @Test
        @DisplayName("rejects tier outside 0..8")
        void tierOutOfRangeRejected() {
            assertThrows(IllegalArgumentException.class, () -> new FabProgress(FabProgress.Stage.UNPROCESSED, -1, FabProgress.CURRENT_VERSION));
            assertThrows(IllegalArgumentException.class, () -> new FabProgress(FabProgress.Stage.UNPROCESSED, 9, FabProgress.CURRENT_VERSION));
        }

        @Test
        @DisplayName("UNPROCESSED requires tier 0")
        void unprocessedRequiresTierZero() {
            assertThrows(IllegalArgumentException.class, () -> new FabProgress(FabProgress.Stage.UNPROCESSED, 1, FabProgress.CURRENT_VERSION));
            assertThrows(IllegalArgumentException.class, () -> new FabProgress(FabProgress.Stage.UNPROCESSED, 5, FabProgress.CURRENT_VERSION));
        }

        @Test
        @DisplayName("DEPOSITED requires tier 0")
        void depositedRequiresTierZero() {
            assertThrows(IllegalArgumentException.class, () -> new FabProgress(FabProgress.Stage.DEPOSITED, 1, FabProgress.CURRENT_VERSION));
            assertThrows(IllegalArgumentException.class, () -> new FabProgress(FabProgress.Stage.DEPOSITED, 5, FabProgress.CURRENT_VERSION));
        }

        @Test
        @DisplayName("COATED requires tier 5..8")
        void coatedRequiresTierFiveToSeven() {
            assertThrows(IllegalArgumentException.class, () -> new FabProgress(FabProgress.Stage.COATED, 0, FabProgress.CURRENT_VERSION));
            assertThrows(IllegalArgumentException.class, () -> new FabProgress(FabProgress.Stage.COATED, 4, FabProgress.CURRENT_VERSION));
            assertThrows(IllegalArgumentException.class, () -> new FabProgress(FabProgress.Stage.COATED, 9, FabProgress.CURRENT_VERSION));
        }

        @Test
        @DisplayName("EXPOSED requires tier 1..8")
        void exposedRequiresTierOneToSeven() {
            assertThrows(IllegalArgumentException.class, () -> new FabProgress(FabProgress.Stage.EXPOSED, 0, FabProgress.CURRENT_VERSION));
            assertThrows(IllegalArgumentException.class, () -> new FabProgress(FabProgress.Stage.EXPOSED, 9, FabProgress.CURRENT_VERSION));
        }

        @Test
        @DisplayName("ETCHED requires tier 4..8")
        void etchedRequiresTierFourToSeven() {
            assertThrows(IllegalArgumentException.class, () -> new FabProgress(FabProgress.Stage.ETCHED, 0, FabProgress.CURRENT_VERSION));
            assertThrows(IllegalArgumentException.class, () -> new FabProgress(FabProgress.Stage.ETCHED, 3, FabProgress.CURRENT_VERSION));
            assertThrows(IllegalArgumentException.class, () -> new FabProgress(FabProgress.Stage.ETCHED, 9, FabProgress.CURRENT_VERSION));
        }

        @Test
        @DisplayName("CLEANED requires tier 5..8")
        void cleanedRequiresTierFiveToSeven() {
            assertThrows(IllegalArgumentException.class, () -> new FabProgress(FabProgress.Stage.CLEANED, 0, FabProgress.CURRENT_VERSION));
            assertThrows(IllegalArgumentException.class, () -> new FabProgress(FabProgress.Stage.CLEANED, 4, FabProgress.CURRENT_VERSION));
            assertThrows(IllegalArgumentException.class, () -> new FabProgress(FabProgress.Stage.CLEANED, 9, FabProgress.CURRENT_VERSION));
        }

        @Test
        @DisplayName("accepts all valid stage/tier combinations")
        void acceptsValidCombinations() {
            // UNPROCESSED/DEPOSITED at tier 0
            assertEquals(FabProgress.Stage.UNPROCESSED, new FabProgress(FabProgress.Stage.UNPROCESSED, 0, FabProgress.CURRENT_VERSION).stage());
            assertEquals(FabProgress.Stage.DEPOSITED, new FabProgress(FabProgress.Stage.DEPOSITED, 0, FabProgress.CURRENT_VERSION).stage());

            // COATED at tiers 5, 6, 7
            for (int t = 5; t <= 7; t++) {
                assertEquals(FabProgress.Stage.COATED, new FabProgress(FabProgress.Stage.COATED, t, FabProgress.CURRENT_VERSION).stage());
                assertEquals(t, new FabProgress(FabProgress.Stage.COATED, t, FabProgress.CURRENT_VERSION).tier());
            }

            // EXPOSED at tiers 1..8
            for (int t = 1; t <= 7; t++) {
                assertEquals(FabProgress.Stage.EXPOSED, new FabProgress(FabProgress.Stage.EXPOSED, t, FabProgress.CURRENT_VERSION).stage());
                assertEquals(t, new FabProgress(FabProgress.Stage.EXPOSED, t, FabProgress.CURRENT_VERSION).tier());
            }

            // ETCHED at tiers 4..8
            for (int t = 4; t <= 7; t++) {
                assertEquals(FabProgress.Stage.ETCHED, new FabProgress(FabProgress.Stage.ETCHED, t, FabProgress.CURRENT_VERSION).stage());
                assertEquals(t, new FabProgress(FabProgress.Stage.ETCHED, t, FabProgress.CURRENT_VERSION).tier());
            }

            // CLEANED at tiers 5..8
            for (int t = 5; t <= 7; t++) {
                assertEquals(FabProgress.Stage.CLEANED, new FabProgress(FabProgress.Stage.CLEANED, t, FabProgress.CURRENT_VERSION).stage());
                assertEquals(t, new FabProgress(FabProgress.Stage.CLEANED, t, FabProgress.CURRENT_VERSION).tier());
            }
        }
    }

    @Nested
    @DisplayName("Initial state")
    class InitialTest {

        @Test
        @DisplayName("initial() returns UNPROCESSED tier 0 version 1")
        void initialStateIsCorrect() {
            FabProgress initial = FabProgress.initial();
            assertEquals(FabProgress.Stage.UNPROCESSED, initial.stage());
            assertEquals(0, initial.tier());
            assertEquals(FabProgress.CURRENT_VERSION, initial.version());
        }
    }

    @Nested
    @DisplayName("Valid tier 1-3 route: UNPROCESSED -> EXPOSED -> ETCHED (not ready) / stop at EXPOSED")
    class TierOneToThreeRouteTest {

        @Test
        @DisplayName("tier 1: UNPROCESSED -> EXPOSED(1) ready for packaging")
        void tierOneRoute() {
            FabProgress p = FabProgress.initial();
            FabProgress exposed = p.exposed(1);
            assertEquals(FabProgress.Stage.EXPOSED, exposed.stage());
            assertEquals(1, exposed.tier());
            assertTrue(exposed.readyForPackaging());

            // Cannot etch tier 1
            assertThrows(IllegalStateException.class, exposed::etched);
        }

        @Test
        @DisplayName("tier 2: UNPROCESSED -> EXPOSED(2) ready for packaging")
        void tierTwoRoute() {
            FabProgress p = FabProgress.initial();
            FabProgress exposed = p.exposed(2);
            assertEquals(FabProgress.Stage.EXPOSED, exposed.stage());
            assertEquals(2, exposed.tier());
            assertTrue(exposed.readyForPackaging());

            assertThrows(IllegalStateException.class, exposed::etched);
        }

        @Test
        @DisplayName("tier 3: UNPROCESSED -> EXPOSED(3) ready for packaging")
        void tierThreeRoute() {
            FabProgress p = FabProgress.initial();
            FabProgress exposed = p.exposed(3);
            assertEquals(FabProgress.Stage.EXPOSED, exposed.stage());
            assertEquals(3, exposed.tier());
            assertTrue(exposed.readyForPackaging());

            assertThrows(IllegalStateException.class, exposed::etched);
        }
    }

    @Nested
    @DisplayName("Valid tier 4 route: UNPROCESSED -> DEPOSITED -> EXPOSED(4) -> ETCHED(4) ready")
    class TierFourRouteTest {

        @Test
        @DisplayName("tier 4 full route")
        void tierFourFullRoute() {
            FabProgress p = FabProgress.initial();
            FabProgress deposited = p.deposited();
            assertEquals(FabProgress.Stage.DEPOSITED, deposited.stage());
            assertEquals(0, deposited.tier());
            assertFalse(deposited.readyForPackaging());

            FabProgress exposed = deposited.exposed(4);
            assertEquals(FabProgress.Stage.EXPOSED, exposed.stage());
            assertEquals(4, exposed.tier());
            assertFalse(exposed.readyForPackaging()); // EXPOSED tier 4 not ready

            FabProgress etched = exposed.etched();
            assertEquals(FabProgress.Stage.ETCHED, etched.stage());
            assertEquals(4, etched.tier());
            assertTrue(etched.readyForPackaging()); // ETCHED tier 4 ready

            // Cannot clean tier 4
            assertThrows(IllegalStateException.class, etched::cleaned);
        }
    }

    @Nested
    @DisplayName("Valid tier 5-7 route: UNPROCESSED -> DEPOSITED -> COATED(tier) -> EXPOSED(tier) -> ETCHED(tier) -> CLEANED(tier) ready")
    class TierFiveToSevenRouteTest {

        @Test
        @DisplayName("tier 5 full route")
        void tierFiveFullRoute() {
            FabProgress p = FabProgress.initial();
            FabProgress deposited = p.deposited();
            FabProgress coated = deposited.coated(5);
            assertEquals(FabProgress.Stage.COATED, coated.stage());
            assertEquals(5, coated.tier());
            assertFalse(coated.readyForPackaging());

            FabProgress exposed = coated.exposed(5);
            assertEquals(FabProgress.Stage.EXPOSED, exposed.stage());
            assertEquals(5, exposed.tier());
            assertFalse(exposed.readyForPackaging()); // EXPOSED tier 5 not ready

            FabProgress etched = exposed.etched();
            assertEquals(FabProgress.Stage.ETCHED, etched.stage());
            assertEquals(5, etched.tier());
            assertFalse(etched.readyForPackaging()); // ETCHED tier 5 not ready

            FabProgress cleaned = etched.cleaned();
            assertEquals(FabProgress.Stage.CLEANED, cleaned.stage());
            assertEquals(5, cleaned.tier());
            assertTrue(cleaned.readyForPackaging()); // CLEANED tier 5 ready
        }

        @Test
        @DisplayName("tier 6 full route")
        void tierSixFullRoute() {
            FabProgress p = FabProgress.initial();
            FabProgress deposited = p.deposited();
            FabProgress coated = deposited.coated(6);
            FabProgress exposed = coated.exposed(6);
            FabProgress etched = exposed.etched();
            FabProgress cleaned = etched.cleaned();
            assertEquals(FabProgress.Stage.CLEANED, cleaned.stage());
            assertEquals(6, cleaned.tier());
            assertTrue(cleaned.readyForPackaging());
        }

        @Test
        @DisplayName("tier 7 full route")
        void tierSevenFullRoute() {
            FabProgress p = FabProgress.initial();
            FabProgress deposited = p.deposited();
            FabProgress coated = deposited.coated(7);
            FabProgress exposed = coated.exposed(7);
            FabProgress etched = exposed.etched();
            FabProgress cleaned = etched.cleaned();
            assertEquals(FabProgress.Stage.CLEANED, cleaned.stage());
            assertEquals(7, cleaned.tier());
            assertTrue(cleaned.readyForPackaging());
        }

        @Test
        @DisplayName("late tier commitment: can choose any tier 5-7 at COATED stage")
        void lateTierCommitment() {
            FabProgress deposited = FabProgress.initial().deposited();

            // Commit to tier 5
            FabProgress coated5 = deposited.coated(5);
            assertEquals(5, coated5.tier());
            FabProgress exposed5 = coated5.exposed(5);
            assertEquals(5, exposed5.tier());

            // Fresh start, commit to tier 6
            FabProgress coated6 = deposited.coated(6);
            assertEquals(6, coated6.tier());
            FabProgress exposed6 = coated6.exposed(6);
            assertEquals(6, exposed6.tier());

            // Fresh start, commit to tier 7
            FabProgress coated7 = deposited.coated(7);
            assertEquals(7, coated7.tier());
            FabProgress exposed7 = coated7.exposed(7);
            assertEquals(7, exposed7.tier());
        }
    }

    @Nested
    @DisplayName("Forbidden skips")
    class SkipTest {

        @Test
        @DisplayName("cannot skip to DEPOSITED from non-UNPROCESSED")
        void cannotSkipToDeposited() {
            FabProgress p = FabProgress.initial().deposited();
            assertThrows(IllegalStateException.class, p::deposited);
            assertThrows(IllegalStateException.class, () -> p.coated(5).deposited());
        }

        @Test
        @DisplayName("cannot skip to COATED from UNPROCESSED or EXPOSED")
        void cannotSkipToCoated() {
            assertThrows(IllegalStateException.class, () -> FabProgress.initial().coated(5));
            FabProgress exposed = FabProgress.initial().exposed(1);
            assertThrows(IllegalStateException.class, () -> exposed.coated(5));
        }

        @Test
        @DisplayName("cannot skip to EXPOSED from wrong stage for tier")
        void cannotSkipToExposed() {
            // Tier 1-3 requires UNPROCESSED
            FabProgress deposited = FabProgress.initial().deposited();
            assertThrows(IllegalStateException.class, () -> deposited.exposed(1));
            assertThrows(IllegalStateException.class, () -> deposited.exposed(2));
            assertThrows(IllegalStateException.class, () -> deposited.exposed(3));

            // Tier 4 requires DEPOSITED
            assertThrows(IllegalStateException.class, () -> FabProgress.initial().exposed(4));
            FabProgress coated = deposited.coated(5);
            assertThrows(IllegalStateException.class, () -> coated.exposed(4));

            // Tier 5-7 requires COATED with matching tier
            assertThrows(IllegalStateException.class, () -> FabProgress.initial().exposed(5));
            assertThrows(IllegalStateException.class, () -> deposited.exposed(5));
            // Mismatched tier
            assertThrows(IllegalArgumentException.class, () -> coated.exposed(6));
        }

        @Test
        @DisplayName("cannot skip to ETCHED from non-EXPOSED or wrong tier")
        void cannotSkipToEtched() {
            assertThrows(IllegalStateException.class, () -> FabProgress.initial().etched());
            assertThrows(IllegalStateException.class, () -> FabProgress.initial().deposited().etched());
            assertThrows(IllegalStateException.class, () -> FabProgress.initial().deposited().coated(5).etched());

            // EXPOSED tier 1-3 cannot etch
            for (int t = 1; t <= 3; t++) {
                FabProgress exposed = FabProgress.initial().exposed(t);
                assertThrows(IllegalStateException.class, exposed::etched);
            }
        }

        @Test
        @DisplayName("cannot skip to CLEANED from non-ETCHED or wrong tier")
        void cannotSkipToCleaned() {
            assertThrows(IllegalStateException.class, () -> FabProgress.initial().cleaned());
            assertThrows(IllegalStateException.class, () -> FabProgress.initial().deposited().cleaned());
            assertThrows(IllegalStateException.class, () -> FabProgress.initial().exposed(1).cleaned());

            // ETCHED tier 4 cannot clean
            FabProgress etched4 = FabProgress.initial().deposited().exposed(4).etched();
            assertThrows(IllegalStateException.class, etched4::cleaned);
        }
    }

    @Nested
    @DisplayName("Forbidden replays (re-applying same transition)")
    class ReplayTest {

        @Test
        @DisplayName("cannot call deposited() twice")
        void cannotReplayDeposited() {
            FabProgress p = FabProgress.initial().deposited();
            assertThrows(IllegalStateException.class, p::deposited);
        }

        @Test
        @DisplayName("cannot call coated() twice")
        void cannotReplayCoated() {
            FabProgress p = FabProgress.initial().deposited().coated(5);
            assertThrows(IllegalStateException.class, () -> p.coated(5));
            assertThrows(IllegalStateException.class, () -> p.coated(6));
        }

        @Test
        @DisplayName("cannot call exposed() twice")
        void cannotReplayExposed() {
            FabProgress p = FabProgress.initial().exposed(1);
            assertThrows(IllegalStateException.class, () -> p.exposed(1));
            assertThrows(IllegalStateException.class, () -> p.exposed(2));

            FabProgress p4 = FabProgress.initial().deposited().exposed(4);
            assertThrows(IllegalStateException.class, () -> p4.exposed(4));

            FabProgress p5 = FabProgress.initial().deposited().coated(5).exposed(5);
            assertThrows(IllegalStateException.class, () -> p5.exposed(5));
        }

        @Test
        @DisplayName("cannot call etched() twice")
        void cannotReplayEtched() {
            FabProgress p = FabProgress.initial().deposited().exposed(4).etched();
            assertThrows(IllegalStateException.class, p::etched);
        }

        @Test
        @DisplayName("cannot call cleaned() twice")
        void cannotReplayCleaned() {
            FabProgress p = FabProgress.initial().deposited().coated(5).exposed(5).etched().cleaned();
            assertThrows(IllegalStateException.class, p::cleaned);
        }
    }

    @Nested
    @DisplayName("Forbidden rebinding (changing bound tier)")
    class RebindingTest {

        @Test
        @DisplayName("cannot change tier at EXPOSED for tier 5-7")
        void cannotRebindAtExposed() {
            FabProgress p = FabProgress.initial().deposited().coated(5).exposed(5);
            // Try to expose again with different tier - should fail because stage is EXPOSED
            assertThrows(IllegalStateException.class, () -> p.exposed(6));
            assertThrows(IllegalStateException.class, () -> p.exposed(7));
        }

        @Test
        @DisplayName("tier is preserved through ETCHED and CLEANED")
        void tierPreservedThroughEtchedAndCleaned() {
            for (int t = 5; t <= 7; t++) {
                FabProgress p = FabProgress.initial()
                    .deposited()
                    .coated(t)
                    .exposed(t)
                    .etched()
                    .cleaned();
                assertEquals(t, p.tier(), "tier changed at CLEANED for tier " + t);
            }

            // Tier 4 preserved through ETCHED
            FabProgress p4 = FabProgress.initial().deposited().exposed(4).etched();
            assertEquals(4, p4.tier());
        }

        @Test
        @DisplayName("cannot construct EXPOSED with mismatched tier after COATED")
        void constructorPreventsMismatch() {
            // This is tested via exposed() transition, but verify constructor also enforces
            // EXPOSED tier 5-7 is allowed by constructor (no stage history check)
            // The stage history is enforced by transition methods, not constructor
            FabProgress p = new FabProgress(FabProgress.Stage.EXPOSED, 5, FabProgress.CURRENT_VERSION);
            assertEquals(5, p.tier());
        }
    }

    @Nested
    @DisplayName("readyForPackaging() exact conditions")
    class ReadyForPackagingTest {

        @Test
        @DisplayName("EXPOSED tier 1, 2, 3 are ready")
        void exposedTierOneToThreeReady() {
            for (int t = 1; t <= 3; t++) {
                FabProgress p = FabProgress.initial().exposed(t);
                assertTrue(p.readyForPackaging(), "EXPOSED tier " + t + " should be ready");
            }
        }

        @Test
        @DisplayName("EXPOSED tier 4, 5, 6, 7 are NOT ready")
        void exposedTierFourToSevenNotReady() {
            FabProgress p4 = FabProgress.initial().deposited().exposed(4);
            assertFalse(p4.readyForPackaging(), "EXPOSED tier 4 should not be ready");

            for (int t = 5; t <= 7; t++) {
                FabProgress p = FabProgress.initial().deposited().coated(t).exposed(t);
                assertFalse(p.readyForPackaging(), "EXPOSED tier " + t + " should not be ready");
            }
        }

        @Test
        @DisplayName("ETCHED tier 4 IS ready, tiers 5-7 are NOT ready")
        void etchedTierFourReadyOthersNot() {
            FabProgress p4 = FabProgress.initial().deposited().exposed(4).etched();
            assertTrue(p4.readyForPackaging(), "ETCHED tier 4 should be ready");

            for (int t = 5; t <= 7; t++) {
                FabProgress p = FabProgress.initial().deposited().coated(t).exposed(t).etched();
                assertFalse(p.readyForPackaging(), "ETCHED tier " + t + " should not be ready");
            }
        }

        @Test
        @DisplayName("CLEANED tier 5, 6, 7 ARE ready")
        void cleanedTierFiveToSevenReady() {
            for (int t = 5; t <= 7; t++) {
                FabProgress p = FabProgress.initial().deposited().coated(t).exposed(t).etched().cleaned();
                assertTrue(p.readyForPackaging(), "CLEANED tier " + t + " should be ready");
            }
        }

        @Test
        @DisplayName("UNPROCESSED, DEPOSITED, COATED are never ready")
        void earlyStagesNeverReady() {
            assertFalse(FabProgress.initial().readyForPackaging());
            assertFalse(FabProgress.initial().deposited().readyForPackaging());
            for (int t = 5; t <= 7; t++) {
                assertFalse(FabProgress.initial().deposited().coated(t).readyForPackaging());
            }
        }
    }

    @Nested
    @DisplayName("Immutability")
    class ImmutabilityTest {

        @Test
        @DisplayName("transition methods return new instances")
        void transitionsReturnNewInstances() {
            FabProgress initial = FabProgress.initial();
            FabProgress deposited = initial.deposited();
            assertNotSame(initial, deposited);

            FabProgress coated = deposited.coated(5);
            assertNotSame(deposited, coated);

            FabProgress exposed = coated.exposed(5);
            assertNotSame(coated, exposed);

            FabProgress etched = exposed.etched();
            assertNotSame(exposed, etched);

            FabProgress cleaned = etched.cleaned();
            assertNotSame(etched, cleaned);
        }

        @Test
        @DisplayName("original instances unchanged after transitions")
        void originalsUnchanged() {
            FabProgress initial = FabProgress.initial();
            FabProgress deposited = initial.deposited();
            assertEquals(FabProgress.Stage.UNPROCESSED, initial.stage());
            assertEquals(0, initial.tier());

            FabProgress coated = deposited.coated(5);
            assertEquals(FabProgress.Stage.DEPOSITED, deposited.stage());
            assertEquals(0, deposited.tier());

            FabProgress exposed = coated.exposed(5);
            assertEquals(FabProgress.Stage.COATED, coated.stage());
            assertEquals(5, coated.tier());
        }

        @Test
        @DisplayName("record components are final and accessible")
        void recordComponentsAccessible() {
            FabProgress p = new FabProgress(FabProgress.Stage.EXPOSED, 3, FabProgress.CURRENT_VERSION);
            assertEquals(FabProgress.Stage.EXPOSED, p.stage());
            assertEquals(3, p.tier());
            assertEquals(FabProgress.CURRENT_VERSION, p.version());
        }
    }

    @Nested
    @DisplayName("Edge cases and error messages")
    class EdgeCaseTest {

        @Test
        @DisplayName("exposed() tier out of range 1..8 rejected")
        void exposedTierOutOfRange() {
            assertThrows(IllegalArgumentException.class, () -> FabProgress.initial().exposed(0));
            assertThrows(IllegalArgumentException.class, () -> FabProgress.initial().exposed(9));
        }

        @Test
        @DisplayName("coated() tier out of range 5..8 rejected")
        void coatedTierOutOfRange() {
            FabProgress deposited = FabProgress.initial().deposited();
            assertThrows(IllegalArgumentException.class, () -> deposited.coated(4));
            assertThrows(IllegalArgumentException.class, () -> deposited.coated(9));
        }

        @Test
        @DisplayName("toString format")
        void toStringFormat() {
            FabProgress p = new FabProgress(FabProgress.Stage.EXPOSED, 4, FabProgress.CURRENT_VERSION);
            assertEquals("EXPOSED@tier4v1", p.toString());
        }
    }
}
