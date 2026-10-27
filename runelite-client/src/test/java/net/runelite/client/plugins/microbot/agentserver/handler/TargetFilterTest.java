package net.runelite.client.plugins.microbot.agentserver.handler;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

/** Interact requests that name x,y or an index must hit exactly that target, not the nearest one with the same id. */
public class TargetFilterTest
{
	private static final WorldPoint ASKED = new WorldPoint(3311, 3501, 0);
	private static final WorldPoint NEAREST = new WorldPoint(3309, 3503, 0);

	@Test
	public void anObjectAtTheAskedTileMatchesAndNeighboursDoNot()
	{
		assertTrue(TargetFilter.matchesTile(ASKED, 3311, 3501, null));
		assertFalse("the nearest crate, behind a wall, is not the one asked for", TargetFilter.matchesTile(NEAREST, 3311, 3501, null));
		assertFalse(TargetFilter.matchesTile(new WorldPoint(3311, 3500, 0), 3311, 3501, null));
	}

	@Test
	public void thePlaneIsCheckedWhenGiven()
	{
		assertTrue(TargetFilter.matchesTile(new WorldPoint(3311, 3501, 1), 3311, 3501, 1));
		assertFalse(TargetFilter.matchesTile(new WorldPoint(3311, 3501, 0), 3311, 3501, 1));
	}

	@Test
	public void aRequestWithoutATileKeepsTheOldNearestBehaviour()
	{
		assertTrue(TargetFilter.matchesTile(NEAREST, null, null, null));
		assertTrue(TargetFilter.matchesTile(null, null, null, null));
		assertFalse(TargetFilter.isSpecific(null, null, null, null));
		assertTrue(TargetFilter.isSpecific(null, 3311, null, null));
	}

	@Test
	public void anNpcIsPickedByIndexWhenGivenElseByTile()
	{
		assertTrue(TargetFilter.matchesNpc(42, NEAREST, 42, null, null, null));
		assertFalse(TargetFilter.matchesNpc(43, ASKED, 42, 3311, 3501, null));
		assertTrue(TargetFilter.matchesNpc(43, ASKED, null, 3311, 3501, null));
		assertFalse(TargetFilter.matchesNpc(43, NEAREST, null, 3311, 3501, null));
	}
}
