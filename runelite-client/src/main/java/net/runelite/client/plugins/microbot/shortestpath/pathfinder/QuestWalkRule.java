package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;

/**
 * Quest automation walks stay on foot. Ships, charters, quetzals, ferries, spirit trees, fairy rings, gliders,
 * carpets, balloons, minecarts, canoes, mushtrees and teleports are blocked unless the destination cannot be
 * reached without them. {@link Pathfinder#run()} then searches again in widening {@link Stage}s and announces which
 * transports it used:
 * <ol>
 *   <li>{@link Stage#ON_FOOT}: nothing restricted.</li>
 *   <li>{@link Stage#NAMED_ROUTES}: only the routes the user prefers (the Veos ferry from Port Sarim, the Skills
 *   necklace to the Fishing Guild).</li>
 *   <li>{@link Stage#CROSSINGS}: any boat, ship, charter, canoe, quetzal or long NPC ferry.</li>
 *   <li>{@link Stage#TELEPORTS}: everything else, the last resort.</li>
 * </ol>
 * While the rule is active the pathfinder config also keeps the transports of every stage loaded whatever the
 * user's global ShortestPath toggles say ({@link #forcesToggle(String)}); the stage decides what a search may
 * use, so the toggles no longer decide whether a quest can cross water.
 */
public final class QuestWalkRule
{
	/** How far a search may widen, in order. */
	public enum Stage
	{
		ON_FOOT,
		NAMED_ROUTES,
		CROSSINGS,
		TELEPORTS;

		/** The next wider stage, or {@code null} when nothing is left to try. */
		public Stage next()
		{
			Stage[] all = values();
			return ordinal() + 1 < all.length ? all[ordinal() + 1] : null;
		}
	}

	private static volatile boolean active;
	private static final ThreadLocal<Stage> STAGE = ThreadLocal.withInitial(() -> Stage.ON_FOOT);
	private static final UnreachableBackoff UNREACHABLE = new UnreachableBackoff();
	private static volatile String lastAnnounced = "";

	/** Boats, ships and the like: they carry the player to another landmass. */
	private static final Set<TransportType> CROSSING = EnumSet.of(
		TransportType.BOAT, TransportType.CANOE, TransportType.CHARTER_SHIP, TransportType.SHIP,
		TransportType.QUETZAL);

	/** Other travel networks; only allowed in the teleport stage. */
	private static final Set<TransportType> TRAVEL = EnumSet.of(
		TransportType.FAIRY_RING, TransportType.GNOME_GLIDER, TransportType.MINECART, TransportType.POH,
		TransportType.SPIRIT_TREE, TransportType.MAGIC_CARPET, TransportType.HOT_AIR_BALLOON,
		TransportType.MAGIC_MUSHTREE);

	/** Config keys of the toggles for {@link #CROSSING} and {@link #TRAVEL} transports and teleport spells. */
	private static final Set<String> FORCED_TOGGLES = Set.of(
		"useBoats", "useCanoes", "useCharterShips", "useShips", "useQuetzals",
		"useFairyRings", "useGnomeGliders", "useMinecarts", "usePoh", "useSpiritTrees", "useMagicCarpets",
		"useHotAirBalloons", "useMagicMushtrees", "useTeleportationSpells", "useTeleportationMinigames");

	/** NPC rides longer than this are ferries (Primio, Varrock to Civitas); the local guides are all under 200. */
	static final int LONG_NPC_RIDE_TILES = 1000;

	private static final String VEOS = "veos";
	private static final String CABIN_BOY_HERBERT = "cabin boy herbert";
	private static final String SKILLS_NECKLACE_FISHING_GUILD = "skills necklace: fishing guild";

	private QuestWalkRule()
	{
	}

	public static void setActive(boolean on)
	{
		active = on;
		if (!on)
		{
			UNREACHABLE.reset();
			lastAnnounced = "";
		}
	}

	public static boolean isActive()
	{
		return active;
	}

	static Stage stage()
	{
		return STAGE.get();
	}

	/** Runs {@code body} with the search widened to {@code stage} on this thread. */
	static void runAt(Stage stage, Runnable body)
	{
		STAGE.set(stage);
		try
		{
			body.run();
		}
		finally
		{
			STAGE.set(Stage.ON_FOOT);
		}
	}

	/** Whether the pathfinder config must keep the transports behind this toggle even when the user switched it off. */
	public static boolean forcesToggle(String configKey)
	{
		return active && FORCED_TOGGLES.contains(configKey);
	}

	/** Whether teleport items must be loaded although the user's policy is {@code NONE}. */
	public static boolean forcesTeleportItems()
	{
		return active;
	}

	public static boolean isRestrictedType(TransportType type, WorldPoint origin)
	{
		return CROSSING.contains(type) || TRAVEL.contains(type) || TransportType.isTeleport(type, origin);
	}

	/** Whether quest walking keeps this transport off until the search is widened. */
	public static boolean isRestricted(Transport transport)
	{
		return isRestrictedType(transport.getType(), transport.getOrigin()) || isLongNpcRide(transport);
	}

	static boolean isCrossing(Transport transport)
	{
		return CROSSING.contains(transport.getType()) || isLongNpcRide(transport);
	}

	private static boolean isLongNpcRide(Transport transport)
	{
		WorldPoint from = transport.getOrigin();
		WorldPoint to = transport.getDestination();
		return transport.getType() == TransportType.NPC && from != null && to != null
			&& from.distanceTo2D(to) > LONG_NPC_RIDE_TILES;
	}

	/** The user's named routes: the Veos ferry from Port Sarim, and the Skills necklace to the Fishing Guild. */
	static boolean isNamedRoute(Transport transport)
	{
		if (transport.getType() == TransportType.SHIP && transport.getName() != null)
		{
			String npc = transport.getName().toLowerCase(Locale.ROOT);
			return npc.equals(VEOS) || npc.equals(CABIN_BOY_HERBERT);
		}
		return transport.getType() == TransportType.TELEPORTATION_ITEM && transport.getDisplayInfo() != null
			&& transport.getDisplayInfo().toLowerCase(Locale.ROOT).equals(SKILLS_NECKLACE_FISHING_GUILD);
	}

	/** Whether a search at {@code stage} may use {@code transport}. */
	public static boolean permits(Stage stage, Transport transport)
	{
		if (!isRestricted(transport))
		{
			return true;
		}
		switch (stage)
		{
			case NAMED_ROUTES:
				return isNamedRoute(transport);
			case CROSSINGS:
				return isCrossing(transport);
			case TELEPORTS:
				return true;
			default:
				return false;
		}
	}

	/** Whether the search must skip this transport right now. */
	public static boolean blocks(Transport transport)
	{
		return active && !permits(STAGE.get(), transport);
	}

	/** True the first time this stage is announced for these targets, so a step is logged once. */
	static boolean firstAnnouncement(Stage stage, Set<?> targets)
	{
		String key = stage + "@" + targets;
		if (key.equals(lastAnnounced))
		{
			return false;
		}
		lastAnnounced = key;
		return true;
	}

	/** The log line for a route found at a widened stage; {@code used} lists the restricted transports it takes. */
	public static String describe(Stage stage, String target, Set<String> used)
	{
		if (stage == Stage.TELEPORTS)
		{
			return "Quest walk: " + target + " not reachable on foot or by crossing; teleporting via " + used;
		}
		return "Quest walk: " + target + " not reachable on foot; crossing via " + used;
	}

	/** Records that the walker could not reach {@code target} even with every stage. Returns true the first time. */
	public static boolean reportUnreachable(WorldPoint target)
	{
		return UNREACHABLE.record(target, System.currentTimeMillis());
	}

	/** Whether walks to {@code target} should be skipped for now because it just proved unreachable. */
	public static boolean isBackedOff(WorldPoint target)
	{
		return active && UNREACHABLE.isBackedOff(target, System.currentTimeMillis());
	}

	/** The target currently given up on, or {@code null}. */
	public static WorldPoint unreachableTarget()
	{
		return active ? UNREACHABLE.target(System.currentTimeMillis()) : null;
	}

	public static long unreachableRetryInMs()
	{
		return UNREACHABLE.remainingMs(System.currentTimeMillis());
	}
}
