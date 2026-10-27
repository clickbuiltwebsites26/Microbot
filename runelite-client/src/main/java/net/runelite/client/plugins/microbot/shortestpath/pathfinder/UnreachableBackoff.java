package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import net.runelite.api.coords.WorldPoint;

/**
 * Remembers the quest walk target that no route reaches, so the quest loop stops searching for it every few
 * seconds. Each repeat failure for the same target doubles the wait, up to {@link #MAX_MS}; a different target
 * starts again from {@link #FIRST_MS}.
 */
final class UnreachableBackoff
{
	static final long FIRST_MS = 30_000L;
	static final long MAX_MS = 300_000L;

	private WorldPoint target;
	private long retryAtMs;
	private long waitMs;

	/** Notes a failed walk. Returns true when the caller should log it: the first failure for this target. */
	synchronized boolean record(WorldPoint failed, long nowMs)
	{
		boolean sameTarget = failed.equals(target);
		waitMs = sameTarget ? Math.min(waitMs * 2, MAX_MS) : FIRST_MS;
		retryAtMs = nowMs + waitMs;
		target = failed;
		return !sameTarget;
	}

	synchronized boolean isBackedOff(WorldPoint walkTarget, long nowMs)
	{
		return walkTarget != null && walkTarget.equals(target) && nowMs < retryAtMs;
	}

	/** The target being waited out, or {@code null} once the wait is over. */
	synchronized WorldPoint target(long nowMs)
	{
		return nowMs < retryAtMs ? target : null;
	}

	synchronized long remainingMs(long nowMs)
	{
		return Math.max(0, retryAtMs - nowMs);
	}

	synchronized void reset()
	{
		target = null;
		retryAtMs = 0;
		waitMs = 0;
	}
}
