package net.runelite.client.plugins.microbot.shortestpath;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathEdge;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathTerminationReason;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.Pathfinder;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.QuestWalkRule;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.SplitFlagMap;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

/** Quest mode never uses ships, spirit trees and the like when the target is reachable on foot. */
public class QuestWalkRuleTest
{
	private static SplitFlagMap collisionMap;
	private static HashMap<WorldPoint, Set<Transport>> all;

	@BeforeClass
	public static void load()
	{
		collisionMap = SplitFlagMap.fromResources();
		all = Transport.loadAllFromResources();
	}

	@After
	public void off()
	{
		QuestWalkRule.setActive(false);
	}

	private Pathfinder run(WorldPoint from, WorldPoint to) throws Exception
	{
		PathfinderConfig config = new PathfinderConfig(collisionMap, all, Collections.emptyList(), null, null);
		Field f = PathfinderConfig.class.getDeclaredField("calculationCutoffMillis");
		f.setAccessible(true);
		f.setLong(config, 15_000);
		for (Map.Entry<WorldPoint, Set<Transport>> e : all.entrySet())
		{
			if (e.getKey() != null)
			{
				config.getTransports().put(e.getKey(), e.getValue());
				config.getTransportsPacked().put(WorldPointUtil.packWorldPoint(e.getKey()), e.getValue());
			}
		}
		Pathfinder pf = new Pathfinder(config, from, to);
		// a quest walk: the rule is on, crossing mode is on and the quest loop is walking
		QuestWalkRule.setCrossingMode(true);
		QuestWalkRule.markQuestThread(true);
		boolean walking = QuestWalkRule.beginQuestWalk(to);
		try
		{
			pf.run();
		}
		finally
		{
			QuestWalkRule.endQuestWalk(walking);
			QuestWalkRule.markQuestThread(false);
		}
		return pf;
	}

	private void assertNoRestricted(WorldPoint from, WorldPoint to) throws Exception
	{
		QuestWalkRule.setActive(true);
		Pathfinder pf = run(from, to);
		assertFalse(pf.getPath().isEmpty());
		assertTrue("ended at " + pf.getPath().get(pf.getPath().size() - 1),
			pf.getPath().get(pf.getPath().size() - 1).distanceTo2D(to) <= 3);
		for (PathEdge e : pf.getPathEdges())
		{
			assertFalse("used " + (e.isTransport() ? e.getTransport().getType() : ""),
				e.isTransport() && QuestWalkRule.isRestrictedType(e.getTransport().getType(), e.getTransport().getOrigin()));
		}
	}

	@Test
	public void geToRimmington() throws Exception
	{
		assertNoRestricted(new WorldPoint(3164, 3485, 0), new WorldPoint(2968, 3205, 0));
	}

	@Test
	public void rimmingtonToFaladorBank() throws Exception
	{
		assertNoRestricted(new WorldPoint(2969, 3206, 0), new WorldPoint(2946, 3368, 0));
	}

	@Test
	public void lumbridgeToDraynor() throws Exception
	{
		assertNoRestricted(new WorldPoint(3222, 3218, 0), new WorldPoint(3092, 3243, 0));
	}

	/** Hosidius (Zeah) to Elena in East Ardougne: no route on foot, so the walk crosses and says what it used. */
	@Test
	public void zeahReachesArdougneByCrossingAndNeverBySurpriseTeleport() throws Exception
	{
		QuestWalkRule.setActive(true);
		WorldPoint elena = new WorldPoint(2592, 3336, 0);
		Pathfinder pf = run(new WorldPoint(1884, 3481, 0), elena);
		assertFalse(pf.getPath().isEmpty());
		assertTrue(pf.getPath().get(pf.getPath().size() - 1).distanceTo2D(elena) <= 3);
		for (PathEdge e : pf.getPathEdges())
		{
			assertFalse("used " + e.getTransport() + " " + e.getFrom() + ">" + e.getTo(),
				e.isTransport() && TransportType.isTeleport(e.getTransport().getType(), e.getTransport().getOrigin()));
			if (e.isTransport() && e.getTransport().getType() == TransportType.SHIP)
			{
				String ferry = e.getTransport().getName();
				assertTrue("unexpected ferry " + ferry, "Veos".equals(ferry) || "Cabin Boy Herbert".equals(ferry));
			}
		}
	}

	/** Nothing reaches this tile: the on-foot result stands, with no boat or teleport left in the partial path. */
	@Test
	public void unreachableTargetLeavesNoRestrictedTransportInThePath() throws Exception
	{
		QuestWalkRule.setActive(true);
		// deep sea south of Karamja: on the map, walkable from nowhere, and no transport lands there
		Pathfinder pf = run(new WorldPoint(3222, 3218, 0), new WorldPoint(2900, 2800, 0));
		assertNotEquals(PathTerminationReason.TARGET_REACHED, pf.getTerminationReason());
		for (PathEdge e : pf.getPathEdges())
		{
			assertFalse("kept " + e.getTransport() + " in a failed search",
				e.isTransport() && QuestWalkRule.isRestricted(e.getTransport()));
		}
	}

	@Test
	public void islandFallsBackToTheShip() throws Exception
	{
		QuestWalkRule.setActive(true);
		Pathfinder pf = run(new WorldPoint(3029, 3217, 0), new WorldPoint(3724, 3808, 0));
		assertFalse(pf.getPath().isEmpty());
		boolean ship = false;
		for (PathEdge e : pf.getPathEdges())
		{
			ship |= e.isTransport() && (e.getTransport().getType() == TransportType.SHIP
				|| e.getTransport().getType() == TransportType.CHARTER_SHIP || e.getTransport().getType() == TransportType.BOAT);
		}
		StringBuilder sb = new StringBuilder(); for (PathEdge e : pf.getPathEdges()) if (e.isTransport()) sb.append(e.getTransport().getDisplayInfo()).append(e.getTransport().getType()).append(e.getFrom()).append(">").append(e.getTo()).append("; ");
		assertTrue("expected a ship to Fossil Island: " + sb + " end=" + pf.getPath().get(pf.getPath().size() - 1) + " reason=" + pf.getTerminationReason(), ship);
		assertEquals(true, pf.getPath().get(pf.getPath().size() - 1).distanceTo2D(new WorldPoint(3724, 3808, 0)) <= 3);
	}
}
