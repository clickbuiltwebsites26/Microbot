package net.runelite.client.plugins.microbot.questhelper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/** Vampyre Slayer, Dr. Harlow in the Blue Moon Inn: the options box was closed on each visit instead of answered. */
public class QuestDialogueFallbackTest
{
	private static final List<String> HINTS = Arrays.asList(
		"Vampyre Slayer", "Talk to Dr. Harlow in the Blue Moon Inn in Varrock.", "Morgan needs your help!");

	private static final List<String> FIRST = Arrays.asList(
		"I need your help dealing with a vampyre.", "I think you've had enough.");
	private static final List<String> SECOND = Arrays.asList(
		"Alright, here you go.", "No. I think you've had enough.");
	private static final List<String> THIRD = Arrays.asList(
		"You said no one's ever seen Count Draynor?", "So you were once a proper vampyre hunter?", "I'd best get going.");

	@Test
	public void theHarlowTranscriptPicksTheGoAheadOptionEveryTime()
	{
		assertEquals("I need your help dealing with a vampyre.", QuestDialogueRules.pickFallbackOption(FIRST, HINTS));
		assertEquals("Alright, here you go.", QuestDialogueRules.pickFallbackOption(SECOND, HINTS));
		assertEquals("So you were once a proper vampyre hunter?", QuestDialogueRules.pickFallbackOption(THIRD, HINTS));
	}

	@Test
	public void declineAndLeaveLinesAreNeverPicked()
	{
		assertTrue(QuestDialogueRules.isRefusal("I think you've had enough."));
		assertTrue(QuestDialogueRules.isRefusal("No. I think you've had enough."));
		assertTrue(QuestDialogueRules.isRefusal("No"));
		assertTrue(QuestDialogueRules.isRefusal("<col=0>[2] Goodbye.</col>"));
		assertTrue(QuestDialogueRules.isRefusal("Not now."));
		assertEquals(Collections.singletonList("Alright, here you go."), QuestDialogueRules.rankOptions(SECOND, HINTS));
		assertNull(QuestDialogueRules.pickFallbackOption(
			Arrays.asList("No.", "I think you've had enough.", "Goodbye."), HINTS));
	}

	@Test
	public void aConversationThatLoopsTriesTheNextBestOptionOnceThenGivesUp()
	{
		assertEquals("So you were once a proper vampyre hunter?", QuestDialogueRules.pickFallbackOption(THIRD, HINTS, 0));
		assertEquals("You said no one's ever seen Count Draynor?", QuestDialogueRules.pickFallbackOption(THIRD, HINTS, 1));
		// "I'd best get going." is a leave line, never chosen by the fallback; the menu is then closed as before
		assertNull(QuestDialogueRules.pickFallbackOption(THIRD, HINTS, 2));
		assertNull(QuestDialogueRules.pickFallbackOption(THIRD, HINTS, 3));
	}

	/** Port Sarim sailor: "Yes please, take me to Karamja." would sail the player away from the quest. */
	@Test
	public void aSailorTranscriptIsNeverAnsweredByTheFallback()
	{
		List<String> options = Arrays.asList("Yes please, take me to Karamja.", "No thanks.");
		List<String> hints = Arrays.asList("Pirate's Treasure", "Talk to the customs officer in Port Sarim.");
		assertNull(QuestDialogueRules.pickFallbackOption(options, hints, 0,
			"Do you want to sail to Karamja? It will cost 30 coins."));
		assertNull(QuestDialogueRules.pickFallbackOption(Arrays.asList("Yes please.", "No thanks."), hints, 0,
			"Do you want to sail to Karamja? It will cost 30 coins."));
	}

	/** Aubury: a bare "Yes please" after the offer to teleport to the Rune Essence mine. */
	@Test
	public void anAuburyTranscriptIsNeverAnsweredByTheFallback()
	{
		List<String> stepWords = Arrays.asList("Talk to Aubury in Varrock.");
		List<String> questName = Arrays.asList("Rune Mysteries");
		assertNull(QuestDialogueRules.pickFallbackOption(Arrays.asList("Yes please.", "No thanks."), stepWords, questName, 0,
			"Would you like me to teleport you to the Rune Essence mine?"));
		// the quest's name is only a weak hint: "Rune Mysteries" does not make "buy some runes" safe
		assertNull(QuestDialogueRules.pickFallbackOption(
			Arrays.asList("Can I buy some runes?", "Teleport me to the essence mine.", "Goodbye."), stepWords, questName, 0, null));
	}

	@Test
	public void aRiskyOptionIsChosenWhenTheStepsOwnWordsMatchIt()
	{
		List<String> hints = Arrays.asList("Buy a beer from the bartender");
		assertEquals("I want to buy some beer.", QuestDialogueRules.pickFallbackOption(
			Arrays.asList("Goodbye.", "I want to buy some beer.", "What's new?"), hints, 0));
		// without that match the same menu picks the safe line
		assertEquals("What's new?", QuestDialogueRules.pickFallbackOption(
			Arrays.asList("Goodbye.", "I want to buy some beer.", "What's new?"), Collections.emptyList(), 0));
	}

	@Test
	public void goAheadWordingBreaksTiesWhenNoHintMatches()
	{
		assertEquals("Yes, I will.", QuestDialogueRules.pickFallbackOption(
			Arrays.asList("Tell me about the weather.", "Yes, I will."), Collections.emptyList()));
	}
}
