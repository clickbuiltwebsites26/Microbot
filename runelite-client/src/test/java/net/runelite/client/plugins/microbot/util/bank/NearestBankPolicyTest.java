package net.runelite.client.plugins.microbot.util.bank;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NearestBankPolicyTest {
    private static final WorldPoint FALADOR_NORTH = new WorldPoint(3024, 3504, 0);

    private static final List<WorldPoint> CANDIDATES = Arrays.asList(
            BankLocation.EDGEVILLE.getWorldPoint(),
            BankLocation.FALADOR_EAST.getWorldPoint(),
            BankLocation.FALADOR_WEST.getWorldPoint());

    @Test
    public void minigameBankExcludedFarAway() {
        assertFalse(NearestBankPolicy.isEligibleTarget(FALADOR_NORTH, BankLocation.ISLE_OF_SOULS));
        assertTrue(NearestBankPolicy.isEligibleTarget(new WorldPoint(2215, 2860, 0), BankLocation.ISLE_OF_SOULS));
        assertTrue(NearestBankPolicy.isEligibleTarget(FALADOR_NORTH, BankLocation.EDGEVILLE));
    }

    @Test
    public void minigamePortalRouteToFarBankRejected() {
        assertFalse(NearestBankPolicy.isPlausible(FALADOR_NORTH, BankLocation.ISLE_OF_SOULS.getWorldPoint(),
                CANDIDATES, true, false));
    }

    @Test
    public void farWalkingRouteRejectedByRatio() {
        assertFalse(NearestBankPolicy.isPlausible(FALADOR_NORTH, new WorldPoint(3024 + 300, 3504, 0),
                CANDIDATES, false, false));
    }

    @Test
    public void nearBankAccepted() {
        assertTrue(NearestBankPolicy.isPlausible(FALADOR_NORTH, BankLocation.EDGEVILLE.getWorldPoint(),
                CANDIDATES, false, false));
    }

    @Test
    public void genuineItemTeleportStillAllowed() {
        WorldPoint desert = new WorldPoint(3300, 3000, 0);
        assertTrue(NearestBankPolicy.isPlausible(desert, BankLocation.EDGEVILLE.getWorldPoint(),
                CANDIDATES, false, true));
    }
}
