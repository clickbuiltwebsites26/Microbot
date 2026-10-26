package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Per-walk, non-negative cost jitter for plain walking steps so near-equal routes (a different
 * street, door or staircase) are chosen on different walks. Costs only ever increase and are a pure
 * function of (seed, tile), so the search stays consistent and the extra distance stays small.
 * Transports, targets and ignored-collision tiles are never penalised; blocked tiles are not
 * unblocked (this only adds cost to tiles the collision map already allows).
 */
public final class PathJitter
{
	/** Strength levels: 0 off, 1 low, 2 medium, 3 high. */
	private static volatile int level;
	private static volatile int seed;
	private static final Object LOCK = new Object();
	private static Object walkKey;
	private static long walkStamp;
	private static final Map<Object, Integer> LAST_SEED = new HashMap<>();
	private static final long RESEED_AFTER_MS = 120_000L;

	private PathJitter()
	{
	}

	public static void configure(boolean enabled, int strengthLevel)
	{
		level = enabled ? Math.max(1, Math.min(3, strengthLevel)) : 0;
	}

	public static boolean isActive()
	{
		return level > 0;
	}

	/**
	 * Picks the seed for a walk to {@code key}. The same key keeps its seed (re-paths mid-walk must not
	 * flip routes); a new key, or the same key after a pause, gets a seed different from its last one.
	 */
	public static void beginWalk(Object key, long nowMs)
	{
		if (level == 0)
		{
			return;
		}
		synchronized (LOCK)
		{
			if (java.util.Objects.equals(key, walkKey) && nowMs - walkStamp < RESEED_AFTER_MS)
			{
				walkStamp = nowMs;
				return;
			}
			Integer previous = LAST_SEED.get(key);
			int next;
			Random rng = new Random(System.nanoTime() ^ nowMs);
			do
			{
				next = rng.nextInt();
			}
			while (previous != null && next == previous);
			if (LAST_SEED.size() > 256)
			{
				LAST_SEED.clear();
			}
			LAST_SEED.put(key, next);
			walkKey = key;
			walkStamp = nowMs;
			seed = next;
		}
	}

	/** Extra cost of entering {@code packedTile}, 0 when inactive. */
	public static int penalty(int packedTile)
	{
		int lv = level;
		return lv == 0 ? 0 : penalty(packedTile, seed, lv);
	}

	/** Pure form for tests. Low: 1/16 tiles +1; medium: 1/10 tiles +1..2; high: 1/6 tiles +1..3. */
	public static int penalty(int packedTile, int seed, int lv)
	{
		if (lv <= 0)
		{
			return 0;
		}
		int h = mix(packedTile * 0x9E3779B1 ^ seed);
		int every = lv == 1 ? 16 : lv == 2 ? 10 : 6;
		int max = lv;
		if (Integer.remainderUnsigned(h, every) != 0)
		{
			return 0;
		}
		return 1 + Integer.remainderUnsigned(h >>> 8, max);
	}

	private static int mix(int x)
	{
		x ^= x >>> 16;
		x *= 0x85EBCA6B;
		x ^= x >>> 13;
		x *= 0xC2B2AE35;
		x ^= x >>> 16;
		return x;
	}
}
