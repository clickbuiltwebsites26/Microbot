package net.runelite.client.plugins.microbot.agentserver.handler;

import net.runelite.api.coords.WorldPoint;

/**
 * Picks the exact object or NPC a request names. With several of the same id around (crates on both sides of a wall) the
 * nearest one is not the intended one, so a request may add the tile (x, y, optionally plane) of an object, or the index
 * or tile of an NPC, and only that target matches.
 */
final class TargetFilter
{
	private TargetFilter()
	{
	}

	/** Whether {@code at} is the tile asked for; an absent x or y (or a null tile) matches everything. */
	static boolean matchesTile(WorldPoint at, Integer x, Integer y, Integer plane)
	{
		if (x == null && y == null && plane == null)
		{
			return true;
		}
		if (at == null)
		{
			return false;
		}
		return (x == null || at.getX() == x) && (y == null || at.getY() == y) && (plane == null || at.getPlane() == plane);
	}

	/** Whether an NPC is the one asked for: by index when given, otherwise by tile. */
	static boolean matchesNpc(int npcIndex, WorldPoint at, Integer wantedIndex, Integer x, Integer y, Integer plane)
	{
		if (wantedIndex != null)
		{
			return npcIndex == wantedIndex;
		}
		return matchesTile(at, x, y, plane);
	}

	/** Whether the request names a particular target at all. */
	static boolean isSpecific(Integer index, Integer x, Integer y, Integer plane)
	{
		return index != null || x != null || y != null || plane != null;
	}
}
