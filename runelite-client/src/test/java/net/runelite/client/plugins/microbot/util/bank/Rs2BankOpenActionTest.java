package net.runelite.client.plugins.microbot.util.bank;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class Rs2BankOpenActionTest {
    @Test
    public void bankBoothUsesBank() {
        assertEquals("Bank", Rs2Bank.bankOpenAction(new String[]{null, "Bank", null, "Collect"}));
    }

    @Test
    public void bankChestUsesUse() {
        assertEquals("Use", Rs2Bank.bankOpenAction(new String[]{"Use", null, "Collect", null, null}));
    }

    @Test
    public void colourTagsAreIgnored() {
        assertEquals("Bank", Rs2Bank.bankOpenAction(new String[]{"<col=ff0000>Bank</col>"}));
    }

    @Test
    public void collectOnlyFallsBackToBank() {
        assertEquals("Bank", Rs2Bank.bankOpenAction(new String[]{null, null, "Collect"}));
    }

    @Test
    public void nullAndEmptyFallBackToBank() {
        assertEquals("Bank", Rs2Bank.bankOpenAction(null));
        assertEquals("Bank", Rs2Bank.bankOpenAction(new String[0]));
    }
}
