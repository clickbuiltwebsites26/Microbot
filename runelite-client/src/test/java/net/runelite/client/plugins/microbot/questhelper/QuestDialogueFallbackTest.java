package net.runelite.client.plugins.microbot.questhelper;

import static org.junit.Assert.assertEquals;
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
	public void aConversationThatLoopsTriesTheNextBestOptionEachAttempt()
	{
		assertEquals("So you were once a proper vampyre hunter?", QuestDialogueRules.pickFallbackOption(THIRD, HINTS, 0));
		assertEquals("You said no one's ever seen Count Draynor?", QuestDialogueRules.pickFallbackOption(THIRD, HINTS, 1));
		assertEquals("I'd best get going.", QuestDialogueRules.pickFallbackOption(THIRD, HINTS, 2));
		assertEquals("So you were once a proper vampyre hunter?", QuestDialogueRules.pickFallbackOption(THIRD, HINTS, 3));
	}

	@Test
	public void goAheadWordingBreaksTiesWhenNoHintMatches()
	{
		assertEquals("Yes, I will.", QuestDialogueRules.pickFallbackOption(
			Arrays.asList("Tell me about the weather.", "Yes, I will."), Collections.emptyList()));
	}
}
