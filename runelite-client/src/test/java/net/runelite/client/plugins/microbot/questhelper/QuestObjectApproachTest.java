package net.runelite.client.plugins.microbot.questhelper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

/**
 * The object's own tile is never walkable, and the closest walkable tile can be across a wall. The approach tile must be
 * one the object can be used from and the planner (which opens closed doors and gates on its route) can reach.
 * Biohazard supplied four shapes of this: cupboard in a house, staircase behind a door, crate in a caged area behind a
 * gate, and the way back out through a gate and a door.
 */
public class QuestObjectApproachTest
{
	private static WorldPoint at(int x, int y, int plane)
	{
		return new WorldPoint(x, y, plane);
	}

	private static WorldPoint choose(WorldPoint object, Set<WorldPoint> acrossWall, Map<WorldPoint, Integer> routes)
	{
		List<WorldPoint> candidates = Arrays.asList(routes.keySet().toArray(new WorldPoint[0]));
		return QuestObjectApproach.choose(object, candidates, tile -> !acrossWall.contains(tile),
			tile -> routes.getOrDefault(tile, QuestObjectApproach.UNREACHABLE));
	}

	/** #82: cupboard 2517,3276 inside Sarah's house; the nearest walkable tile outside is across the wall. */
	@Test
	public void cupboardInAHouseIsApproachedFromInsideNotAcrossTheWall()
	{
		WorldPoint cupboard = at(2517, 3276, 0);
		WorldPoint outside = at(2516, 3277, 0);
		WorldPoint inside = at(2517, 3275, 0);
		Map<WorldPoint, Integer> routes = new HashMap<>();
		routes.put(outside, 4);
		routes.put(inside, 12); // through the house door at 2519,3275
		assertEquals(inside, choose(cupboard, Collections.singleton(outside), routes));
	}

	/** #83: staircase 2542,3324 in the west room, closed door 2546,3325 between it and the player. */
	@Test
	public void staircaseBehindAClosedDoorIsApproachedFromItsOwnRoom()
	{
		WorldPoint staircase = at(2542, 3324, 0);
		WorldPoint eastSide = at(2541, 3324, 0);
		WorldPoint westRoom = at(2543, 3324, 0);
		Map<WorldPoint, Integer> routes = new HashMap<>();
		routes.put(eastSide, 3);
		routes.put(westRoom, 9);
		assertEquals(westRoom, choose(staircase, Collections.singleton(eastSide), routes));
	}

	/** #84: crate 2554,3327 upstairs in a cage behind gate 2058 at 2551,3326. */
	@Test
	public void crateInACagedAreaIsApproachedThroughTheGate()
	{
		WorldPoint crate = at(2554, 3327, 1);
		WorldPoint outsideCage = at(2555, 3327, 1);
		WorldPoint inCage = at(2553, 3326, 1);
		Map<WorldPoint, Integer> routes = new HashMap<>();
		routes.put(outsideCage, 5);
		routes.put(inCage, 7);
		assertEquals(inCage, choose(crate, Collections.singleton(outsideCage), routes));
	}

	/** #85: from inside the cage out to the staircase: a gate that closed behind the player, then a door. */
	@Test
	public void routeThroughAChainOfClosedGateAndDoorIsAccepted()
	{
		WorldPoint staircase = at(2543, 3325, 1);
		WorldPoint nextToStairs = at(2544, 3325, 1);
		Map<WorldPoint, Integer> routes = new HashMap<>();
		routes.put(nextToStairs, 21); // gate 2058, door 2034, then the corridor
		assertEquals(nextToStairs, choose(staircase, Collections.emptySet(), routes));
	}

	@Test
	public void shortestRouteWinsAndTiesPreferTheTileNearestTheObject()
	{
		WorldPoint object = at(100, 100, 0);
		WorldPoint near = at(101, 100, 0);
		WorldPoint far = at(103, 100, 0);
		Map<WorldPoint, Integer> routes = new HashMap<>();
		routes.put(far, 6);
		routes.put(near, 6);
		assertEquals(near, choose(object, Collections.emptySet(), routes));
		routes.put(far, 4);
		assertEquals(far, choose(object, Collections.emptySet(), routes));
	}

	@Test
	public void nothingUsableOrReachableGivesNoTile()
	{
		WorldPoint object = at(100, 100, 0);
		WorldPoint blockedSide = at(101, 100, 0);
		WorldPoint sealed = at(99, 100, 0);
		Map<WorldPoint, Integer> routes = new HashMap<>();
		routes.put(blockedSide, 3);
		routes.put(sealed, QuestObjectApproach.UNREACHABLE);
		assertNull(choose(object, Collections.singleton(blockedSide), routes));
		assertNull(choose(object, Collections.emptySet(), Collections.emptyMap()));
	}
}
