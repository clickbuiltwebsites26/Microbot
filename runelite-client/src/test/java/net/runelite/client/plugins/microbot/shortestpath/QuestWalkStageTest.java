package net.runelite.client.plugins.microbot.shortestpath;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.QuestWalkRule;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.QuestWalkRule.Stage;
import org.junit.After;
import org.junit.Test;

/** The stage ladder (on foot, named routes, crossings, teleports) and the transport allowlist of each stage. */
public class QuestWalkStageTest
{
	private static final WorldPoint A = new WorldPoint(3055, 3245, 0);
	private static final WorldPoint B = new WorldPoint(1824, 3695, 1);

	@After
	public void off()
	{
		QuestWalkRule.setActive(false);
	}

	private static Transport npc(TransportType type, String name, WorldPoint from, WorldPoint to)
	{
		return new Transport(from, to, "x", type, true, "Travel", name, 1);
	}

	private static Transport plain(TransportType type)
	{
		return new Transport(A, B, "x", type, true, 4);
	}

	private static Transport item(String display)
	{
		return new Transport(null, B, display, TransportType.TELEPORTATION_ITEM, true, 4);
	}

	@Test
	public void stagesWidenInOrderThenGiveUp()
	{
		assertEquals(Stage.NAMED_ROUTES, Stage.ON_FOOT.next());
		assertEquals(Stage.CROSSINGS, Stage.NAMED_ROUTES.next());
		assertEquals(Stage.TELEPORTS, Stage.CROSSINGS.next());
		assertNull(Stage.TELEPORTS.next());
	}

	@Test
	public void unrestrictedTransportsAreAlwaysPermitted()
	{
		Transport shortcut = plain(TransportType.AGILITY_SHORTCUT);
		for (Stage stage : Stage.values())
		{
			assertTrue(QuestWalkRule.permits(stage, shortcut));
		}
	}

	@Test
	public void onFootPermitsNoRestrictedTransport()
	{
		assertFalse(QuestWalkRule.permits(Stage.ON_FOOT, npc(TransportType.SHIP, "Veos", A, B)));
		assertFalse(QuestWalkRule.permits(Stage.ON_FOOT, plain(TransportType.QUETZAL)));
		assertFalse(QuestWalkRule.permits(Stage.ON_FOOT, item("Ring of wealth: Grand Exchange")));
		assertFalse(QuestWalkRule.permits(Stage.ON_FOOT, plain(TransportType.FAIRY_RING)));
	}

	@Test
	public void namedRoutesAreVeosAndTheSkillsNecklaceOnly()
	{
		assertTrue(QuestWalkRule.permits(Stage.NAMED_ROUTES, npc(TransportType.SHIP, "Veos", A, B)));
		assertTrue(QuestWalkRule.permits(Stage.NAMED_ROUTES, npc(TransportType.SHIP, "Cabin Boy Herbert", A, B)));
		assertTrue(QuestWalkRule.permits(Stage.NAMED_ROUTES, item("Skills necklace: Fishing Guild")));
		assertFalse(QuestWalkRule.permits(Stage.NAMED_ROUTES, item("Skills necklace: Mining Guild")));
		assertFalse(QuestWalkRule.permits(Stage.NAMED_ROUTES, npc(TransportType.SHIP, "Captain Tobias", A, B)));
		assertFalse(QuestWalkRule.permits(Stage.NAMED_ROUTES, npc(TransportType.CHARTER_SHIP, "Trader Crewmember", A, B)));
	}

	@Test
	public void crossingsPermitBoatsButNoTeleports()
	{
		assertTrue(QuestWalkRule.permits(Stage.CROSSINGS, npc(TransportType.SHIP, "Captain Tobias", A, B)));
		assertTrue(QuestWalkRule.permits(Stage.CROSSINGS, npc(TransportType.CHARTER_SHIP, "Trader Crewmember", A, B)));
		assertTrue(QuestWalkRule.permits(Stage.CROSSINGS, plain(TransportType.BOAT)));
		assertFalse(QuestWalkRule.permits(Stage.CROSSINGS, item("Ring of wealth: Grand Exchange")));
		assertFalse(QuestWalkRule.permits(Stage.CROSSINGS, plain(TransportType.FAIRY_RING)));
		assertFalse(QuestWalkRule.permits(Stage.CROSSINGS, plain(TransportType.SPIRIT_TREE)));
	}

	@Test
	public void teleportsStagePermitsEverything()
	{
		assertTrue(QuestWalkRule.permits(Stage.TELEPORTS, item("Ring of wealth: Grand Exchange")));
		assertTrue(QuestWalkRule.permits(Stage.TELEPORTS, plain(TransportType.FAIRY_RING)));
	}

	@Test
	public void longNpcFerriesAreCrossingsButLocalGuidesAreNot()
	{
		Transport primio = npc(TransportType.NPC, "Primio", new WorldPoint(3280, 3412, 0), new WorldPoint(1700, 3141, 0));
		Transport guide = npc(TransportType.NPC, "Mistag", new WorldPoint(3319, 9615, 0), new WorldPoint(3232, 9610, 0));
		assertTrue(QuestWalkRule.isRestricted(primio));
		assertFalse(QuestWalkRule.permits(Stage.ON_FOOT, primio));
		assertTrue(QuestWalkRule.permits(Stage.CROSSINGS, primio));
		assertFalse(QuestWalkRule.isRestricted(guide));
		assertTrue(QuestWalkRule.permits(Stage.ON_FOOT, guide));
	}

	@Test
	public void blocksOnlyWhileActive()
	{
		Transport veos = npc(TransportType.SHIP, "Veos", A, B);
		assertFalse(QuestWalkRule.blocks(veos));
		QuestWalkRule.setActive(true);
		assertTrue(QuestWalkRule.blocks(veos));
		QuestWalkRule.setActive(false);
		assertFalse(QuestWalkRule.blocks(veos));
	}

	@Test
	public void globalToggleOverridesApplyOnlyWhileActive()
	{
		assertFalse(QuestWalkRule.forcesToggle("useShips"));
		QuestWalkRule.setActive(true);
		assertTrue(QuestWalkRule.forcesToggle("useShips"));
		assertTrue(QuestWalkRule.forcesToggle("useCharterShips"));
		assertFalse(QuestWalkRule.forcesToggle("useAgilityShortcuts"));
		assertTrue(QuestWalkRule.forcesTeleportItems());
		QuestWalkRule.setActive(false);
		assertFalse(QuestWalkRule.forcesToggle("useShips"));
		assertFalse(QuestWalkRule.forcesTeleportItems());
	}

	@Test
	public void unreachableTargetClearsWhenTheRuleStops()
	{
		WorldPoint elena = new WorldPoint(2592, 3336, 0);
		QuestWalkRule.setActive(true);
		assertTrue(QuestWalkRule.reportUnreachable(elena));
		assertFalse(QuestWalkRule.reportUnreachable(elena));
		assertTrue(QuestWalkRule.isBackedOff(elena));
		assertEquals(elena, QuestWalkRule.unreachableTarget());
		QuestWalkRule.setActive(false);
		assertFalse(QuestWalkRule.isBackedOff(elena));
		assertNull(QuestWalkRule.unreachableTarget());
	}

	@Test
	public void announcementTextNamesTheTransports()
	{
		Set<String> used = Collections.singleton("SHIP Port Sarim");
		assertEquals("Quest walk: T not reachable on foot; crossing via [SHIP Port Sarim]",
			QuestWalkRule.describe(Stage.CROSSINGS, "T", used));
		assertTrue(QuestWalkRule.describe(Stage.TELEPORTS, "T", used).contains("teleporting via"));
	}
}
