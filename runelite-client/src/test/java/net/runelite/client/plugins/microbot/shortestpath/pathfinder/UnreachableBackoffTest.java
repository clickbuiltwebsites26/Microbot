package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

public class UnreachableBackoffTest
{
	private static final WorldPoint ELENA = new WorldPoint(2592, 3336, 0);
	private static final WorldPoint OTHER = new WorldPoint(3222, 3218, 0);

	@Test
	public void firstFailureIsReportedOnceAndBacksOff()
	{
		UnreachableBackoff backoff = new UnreachableBackoff();
		assertTrue(backoff.record(ELENA, 0));
		assertFalse(backoff.record(ELENA, 40_000));
		assertTrue(backoff.isBackedOff(ELENA, 41_000));
		assertEquals(ELENA, backoff.target(41_000));
	}

	@Test
	public void waitDoublesUpToTheCap()
	{
		UnreachableBackoff backoff = new UnreachableBackoff();
		backoff.record(ELENA, 0);
		assertEquals(UnreachableBackoff.FIRST_MS, backoff.remainingMs(0));
		backoff.record(ELENA, 0);
		assertEquals(UnreachableBackoff.FIRST_MS * 2, backoff.remainingMs(0));
		for (int i = 0; i < 10; i++)
		{
			backoff.record(ELENA, 0);
		}
		assertEquals(UnreachableBackoff.MAX_MS, backoff.remainingMs(0));
	}

	@Test
	public void otherTargetsAndExpiryAreNotBackedOff()
	{
		UnreachableBackoff backoff = new UnreachableBackoff();
		backoff.record(ELENA, 0);
		assertFalse(backoff.isBackedOff(OTHER, 1_000));
		assertFalse(backoff.isBackedOff(ELENA, UnreachableBackoff.FIRST_MS));
		assertNull(backoff.target(UnreachableBackoff.FIRST_MS));
	}

	@Test
	public void newTargetRestartsTheWait()
	{
		UnreachableBackoff backoff = new UnreachableBackoff();
		backoff.record(ELENA, 0);
		backoff.record(ELENA, 0);
		assertTrue(backoff.record(OTHER, 0));
		assertEquals(UnreachableBackoff.FIRST_MS, backoff.remainingMs(0));
	}

	@Test
	public void resetClearsTheState()
	{
		UnreachableBackoff backoff = new UnreachableBackoff();
		backoff.record(ELENA, 0);
		backoff.reset();
		assertFalse(backoff.isBackedOff(ELENA, 1));
		assertNull(backoff.target(1));
	}
}
