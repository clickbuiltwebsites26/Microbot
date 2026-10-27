package net.runelite.client.plugins.microbot.questhelper;

import static org.junit.Assert.assertEquals;

import net.runelite.client.plugins.microbot.questhelper.QuestNpcApproach.Move;
import org.junit.Test;

/** Click what can be reached or is plainly in sight; walk to what is behind a door the server cannot open. */
public class QuestNpcApproachTest
{
	@Test
	public void reachableNpcIsClickedAtAnyDistance()
	{
		assertEquals(Move.INTERACT, QuestNpcApproach.decide(true, true, 15));
	}

	@Test
	public void nearbyNpcInSightIsClickedEvenWhenThePlannerDoubtsTheRoute()
	{
		assertEquals(Move.INTERACT, QuestNpcApproach.decide(false, true, 3));
		assertEquals(Move.INTERACT, QuestNpcApproach.decide(false, true, QuestNpcApproach.CLICK_RANGE));
	}

	@Test
	public void npcBehindAClosedDoorIsWalkedToSoTheWalkerOpensIt()
	{
		// no line of sight through a closed door, however near it is
		assertEquals(Move.WALK, QuestNpcApproach.decide(false, false, 2));
	}

	@Test
	public void farNpcInSightButNotReachableIsWalkedTo()
	{
		assertEquals(Move.WALK, QuestNpcApproach.decide(false, true, QuestNpcApproach.CLICK_RANGE + 1));
	}
}
