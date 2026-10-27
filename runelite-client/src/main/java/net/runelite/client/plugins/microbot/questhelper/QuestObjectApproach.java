package net.runelite.client.plugins.microbot.questhelper;

import java.util.Collection;
import java.util.Comparator;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import net.runelite.api.coords.WorldPoint;

/**
 * Picks the tile to stand on for an object step. The object's own tile is usually not walkable (cupboard, crate,
 * staircase), and the closest walkable tile may be on the far side of a wall. The right tile is one the object can be
 * used from and that the real route planner can reach, including through closed doors and gates it opens on the way.
 */
public final class QuestObjectApproach
{
	/** Route length for a tile the planner cannot reach. */
	public static final int UNREACHABLE = -1;

	private QuestObjectApproach()
	{
	}

	/**
	 * @param objectTile       the tile of the object
	 * @param candidates       walkable tiles near the object
	 * @param usableFrom       whether the object can be used from the tile (not through a wall)
	 * @param routeLength      length of the planned route from the player to the tile, or {@link #UNREACHABLE}
	 * @return the usable, reachable tile with the shortest route (nearest to the object on ties), or {@code null}
	 */
	public static WorldPoint choose(WorldPoint objectTile, Collection<WorldPoint> candidates,
		Predicate<WorldPoint> usableFrom, ToIntFunction<WorldPoint> routeLength)
	{
		WorldPoint best = null;
		int bestLength = Integer.MAX_VALUE;
		Comparator<WorldPoint> tieBreak = Comparator
			.comparingInt((WorldPoint tile) -> tile.distanceTo2D(objectTile))
			.thenComparingInt(WorldPoint::getX)
			.thenComparingInt(WorldPoint::getY);
		for (WorldPoint tile : candidates)
		{
			if (!usableFrom.test(tile))
			{
				continue;
			}
			int length = routeLength.applyAsInt(tile);
			if (length == UNREACHABLE)
			{
				continue;
			}
			if (length < bestLength || (length == bestLength && tieBreak.compare(tile, best) < 0))
			{
				best = tile;
				bestLength = length;
			}
		}
		return best;
	}
}
