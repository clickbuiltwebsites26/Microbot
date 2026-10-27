package net.runelite.client.plugins.microbot.questhelper;

import static org.junit.Assert.assertEquals;

import net.runelite.client.plugins.microbot.questhelper.QuestNpcApproach.Move;
import org.junit.Test;

/** Biohazard's Varrock pub: the NPC is three tiles away, just not in view, and walking toward it did nothing. */
public class QuestNpcApproachTest
{
	@Test
	public void visibleAndReachableNpcIsClicked()
	{
		assertEquals(Move.INTERACT, QuestNpcApproach.decide(true, true, 9));
	}

	@Test
	public void nearbyNpcOutOfViewGetsTheCameraTurnedNotAWalkThatDoesNothing()
	{
		assertEquals(Move.TURN_CAMERA, QuestNpcApproach.decide(false, true, 3));
		assertEquals(Move.TURN_CAMERA, QuestNpcApproach.decide(false, false, 3));
		assertEquals(Move.TURN_CAMERA, QuestNpcApproach.decide(false, true, QuestNpcApproach.TURN_RANGE));
	}

	@Test
	public void nearbyVisibleNpcIsClickedEvenWhenThePlannerDoubtsTheRoute()
	{
		assertEquals(Move.INTERACT, QuestNpcApproach.decide(true, false, 3));
		assertEquals(Move.INTERACT, QuestNpcApproach.decide(true, false, QuestNpcApproach.CLICK_RANGE));
	}

	@Test
	public void farNpcIsWalkedTo()
	{
		assertEquals(Move.WALK, QuestNpcApproach.decide(false, true, 12));
		assertEquals(Move.WALK, QuestNpcApproach.decide(true, false, 12));
	}
}
