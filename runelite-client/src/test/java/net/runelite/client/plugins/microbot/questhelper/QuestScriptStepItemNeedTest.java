package net.runelite.client.plugins.microbot.questhelper;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class QuestScriptStepItemNeedTest
{
	@Test
	public void ladderCarryAlongDoesNotTriggerABankTrip()
	{
		assertFalse(QuestScript.stepNeedsItemRequirement(false, "Climb up the ladder in the chapel room east of you.", false, false));
		assertFalse(QuestScript.stepNeedsItemRequirement(false, "Push the wall to the south-east to enter the storage room", false, false));
		assertFalse(QuestScript.stepNeedsItemRequirement(false, null, false, false));
	}

	@Test
	public void equippedHighlightedAndIconItemsAreFetched()
	{
		assertTrue(QuestScript.stepNeedsItemRequirement(false, "Enter the fortress.", true, false));
		assertTrue(QuestScript.stepNeedsItemRequirement(false, "Go to the hole.", false, true));
		assertTrue(QuestScript.stepNeedsItemRequirement(true, "Go to the hole.", false, false));
	}

	@Test
	public void handInTextIsFetched()
	{
		assertTrue(QuestScript.stepNeedsItemRequirement(false, "Bring Drezel 50 UNNOTED rune/pure essence in the underground of the Salve Temple.", false, false));
		assertTrue(QuestScript.stepNeedsItemRequirement(false, "Hand the ring to Bob.", false, false));
		assertTrue(QuestScript.stepNeedsItemRequirement(false, "USE the cabbage on the hole.", false, false));
		assertFalse(QuestScript.stepNeedsItemRequirement(false, "Browse the housewares.", false, false));
	}

	/** Dragon Slayer I magic door: the mind-bomb step must not fetch the lobster pot already used on the door. */
	@Test
	public void aUseItemOnObjectStepNeedsOnlyItsOwnItem()
	{
		java.util.List<Integer> pot = java.util.List.of(301);
		java.util.List<Integer> bomb = java.util.List.of(1907);
		assertFalse(QuestScript.objectStepNeedsRequirement(true, 1907, pot, false, false));
		assertTrue(QuestScript.objectStepNeedsRequirement(true, 1907, bomb, false, false));
		assertTrue("worn items are still needed", QuestScript.objectStepNeedsRequirement(true, 1907, pot, true, false));
		assertTrue("highlighted items are still needed", QuestScript.objectStepNeedsRequirement(true, 1907, pot, false, true));
		assertTrue("an object step without an icon keeps its requirements",
			QuestScript.objectStepNeedsRequirement(true, -1, pot, false, false));
		assertTrue("other step types are unchanged", QuestScript.objectStepNeedsRequirement(false, 1907, pot, false, false));
	}
}
