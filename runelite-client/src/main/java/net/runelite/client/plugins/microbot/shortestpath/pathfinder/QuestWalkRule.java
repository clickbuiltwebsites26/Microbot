package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil;

/**
 * Two switches, both off by default.
 *
 * <p><b>On-foot restriction</b> ({@link #setActive}): ships, charters, quetzals, ferries, spirit trees, fairy
 * rings, gliders, carpets, balloons, minecarts, canoes, mushtrees and teleports are blocked for every search.
 * Quest automation and the DD Chopper plugin use it to keep the walker on foot.
 *
 * <p><b>Crossing mode</b> ({@link #setCrossingMode}): only the quest script sets it, and it only takes effect
 * while a walk started by the quest script's own thread is in flight ({@link #beginQuestWalk}). A search that
 * ends with the target proven unreachable ({@link PathTerminationReason#SEARCH_EXHAUSTED}, not a cutoff, not a
 * sealed target) is repeated in widening {@link Stage}s, and the first stage that reaches the target is announced:
 * <ol>
 *   <li>{@link Stage#ON_FOOT}: nothing restricted.</li>
 *   <li>{@link Stage#NAMED_ROUTES}: only the routes the user prefers (the Veos ferry from Port Sarim; the Skills
 *   necklace to the Fishing Guild, for targets around East Ardougne only).</li>
 *   <li>{@link Stage#CROSSINGS}: any boat, ship, charter, canoe, quetzal or long surface ride (Primio, the travel
 *   cart, Waydar, the Fremennik boat).</li>
 *   <li>{@link Stage#TELEPORTS}: everything else, the last resort.</li>
 * </ol>
 * While crossing mode is in effect the pathfinder config also keeps the transports of every stage loaded
 * whatever the user's global ShortestPath toggles say ({@link #forcesToggle}); the stage decides what a search may
 * use. The teleport-stage toggles it forces include teleport spells and minigame teleports.
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
	private static volatile boolean crossingMode;
	private static final AtomicInteger QUEST_WALKS_IN_FLIGHT = new AtomicInteger();
	private static final ThreadLocal<Boolean> QUEST_THREAD = ThreadLocal.withInitial(() -> Boolean.FALSE);
	private static volatile WorldPoint lastQuestWalkTarget;
	private static final ThreadLocal<Stage> STAGE = ThreadLocal.withInitial(() -> Stage.ON_FOOT);
	private static final ThreadLocal<Boolean> ARDOUGNE_TARGET = ThreadLocal.withInitial(() -> Boolean.FALSE);
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

	/**
	 * NPC and travel-cart rides longer than this between two surface tiles carry the player across the map
	 * (Primio is 1600 tiles, Waydar 893, the travel cart 270, the Fremennik boat 335); the local guides and
	 * ladders are all under 200.
	 */
	static final int LONG_RIDE_TILES = 250;
	private static final int SURFACE_MAX_X = 4000;
	private static final int SURFACE_MAX_Y = 4200;

	/** Rough bounds of East Ardougne, its Fishing Guild approach and the roads between. */
	private static final int ARDOUGNE_MIN_X = 2430;
	private static final int ARDOUGNE_MAX_X = 2700;
	private static final int ARDOUGNE_MIN_Y = 3200;
	private static final int ARDOUGNE_MAX_Y = 3420;

	private static final String VEOS = "veos";
	private static final String CABIN_BOY_HERBERT = "cabin boy herbert";
	private static final String SKILLS_NECKLACE_FISHING_GUILD = "skills necklace: fishing guild";

	private QuestWalkRule()
	{
	}

	/** Switches the on-foot restriction; turning it off also ends crossing mode and forgets the unreachable target. */
	public static void setActive(boolean on)
	{
		active = on;
		if (!on)
		{
			crossingMode = false;
			UNREACHABLE.reset();
			lastAnnounced = "";
			lastQuestWalkTarget = null;
		}
	}

	public static boolean isActive()
	{
		return active;
	}

	/** Quest script only: whether walks of the quest thread may cross water and teleport when nothing else reaches. */
	public static void setCrossingMode(boolean on)
	{
		crossingMode = on && active;
	}

	/** Marks the calling thread as the quest script's loop thread. */
	public static void markQuestThread(boolean on)
	{
		QUEST_THREAD.set(on);
	}

	/**
	 * Called when a walk starts. Returns true when it is a quest walk (the caller must pass the result to
	 * {@link #endQuestWalk}); records the target for the status and the unreachable back-off.
	 */
	public static boolean beginQuestWalk(WorldPoint target)
	{
		if (!QUEST_THREAD.get())
		{
			return false;
		}
		QUEST_WALKS_IN_FLIGHT.incrementAndGet();
		lastQuestWalkTarget = target;
		return true;
	}

	public static void endQuestWalk(boolean wasQuestWalk)
	{
		if (wasQuestWalk)
		{
			QUEST_WALKS_IN_FLIGHT.decrementAndGet();
		}
	}

	/** Whether a walk started by the quest script is running right now. */
	public static boolean isQuestWalkInFlight()
	{
		return QUEST_WALKS_IN_FLIGHT.get() > 0;
	}

	/** Whether widening and the forced toggles apply right now: crossing mode, during a quest walk. */
	public static boolean crossingEnabled()
	{
		return active && crossingMode && isQuestWalkInFlight();
	}

	/** Pure decision: widen only when the on-foot search proved the target unreachable. */
	public static boolean shouldWiden(boolean crossingEnabled, PathTerminationReason onFootResult, boolean sealedTarget)
	{
		return crossingEnabled && !sealedTarget && onFootResult == PathTerminationReason.SEARCH_EXHAUSTED;
	}

	static Stage stage()
	{
		return STAGE.get();
	}

	/** Runs {@code body} with the search widened to {@code stage} on this thread. */
	static void runAt(Stage stage, boolean ardougneTarget, Runnable body)
	{
		STAGE.set(stage);
		ARDOUGNE_TARGET.set(ardougneTarget);
		try
		{
			body.run();
		}
		finally
		{
			STAGE.set(Stage.ON_FOOT);
			ARDOUGNE_TARGET.set(Boolean.FALSE);
		}
	}

	/** Whether the pathfinder config must keep the transports behind this toggle even when the user switched it off. */
	public static boolean forcesToggle(String configKey)
	{
		return FORCED_TOGGLES.contains(configKey) && crossingEnabled();
	}

	/** Whether teleport items must be loaded although the user's policy is {@code NONE}. */
	public static boolean forcesTeleportItems()
	{
		return crossingEnabled();
	}

	public static boolean isRestrictedType(TransportType type, WorldPoint origin)
	{
		return CROSSING.contains(type) || TRAVEL.contains(type) || TransportType.isTeleport(type, origin);
	}

	/** Whether quest walking keeps this transport off until the search is widened. */
	public static boolean isRestricted(Transport transport)
	{
		return isRestrictedType(transport.getType(), transport.getOrigin()) || isLongRide(transport);
	}

	static boolean isCrossing(Transport transport)
	{
		return CROSSING.contains(transport.getType()) || isLongRide(transport);
	}

	private static boolean isLongRide(Transport transport)
	{
		WorldPoint from = transport.getOrigin();
		WorldPoint to = transport.getDestination();
		return (transport.getType() == TransportType.NPC || transport.getType() == TransportType.TRANSPORT)
			&& from != null && to != null && onSurface(from) && onSurface(to)
			&& from.distanceTo2D(to) > LONG_RIDE_TILES;
	}

	private static boolean onSurface(WorldPoint point)
	{
		return point.getX() < SURFACE_MAX_X && point.getY() < SURFACE_MAX_Y;
	}

	/** Whether any target lies around East Ardougne, where the Skills necklace route is wanted. */
	public static boolean isArdougneArea(Set<Integer> packedTargets)
	{
		for (int packed : packedTargets)
		{
			WorldPoint point = WorldPointUtil.unpackWorldPoint(packed);
			if (point.getX() >= ARDOUGNE_MIN_X && point.getX() <= ARDOUGNE_MAX_X
				&& point.getY() >= ARDOUGNE_MIN_Y && point.getY() <= ARDOUGNE_MAX_Y)
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * The user's named routes: the Veos ferry between Port Sarim and Zeah, and, for Ardougne targets only, the
	 * Skills necklace to the Fishing Guild.
	 */
	static boolean isNamedRoute(Transport transport, boolean ardougneTarget)
	{
		if (transport.getType() == TransportType.SHIP && transport.getName() != null)
		{
			String npc = transport.getName().toLowerCase(Locale.ROOT);
			return npc.equals(VEOS) || npc.equals(CABIN_BOY_HERBERT);
		}
		return ardougneTarget && transport.getType() == TransportType.TELEPORTATION_ITEM
			&& transport.getDisplayInfo() != null
			&& transport.getDisplayInfo().toLowerCase(Locale.ROOT).equals(SKILLS_NECKLACE_FISHING_GUILD);
	}

	/** Whether a search at {@code stage} may use {@code transport}. */
	public static boolean permits(Stage stage, Transport transport, boolean ardougneTarget)
	{
		if (!isRestricted(transport))
		{
			return true;
		}
		switch (stage)
		{
			case NAMED_ROUTES:
				return isNamedRoute(transport, ardougneTarget);
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
		return active && !permits(STAGE.get(), transport, ARDOUGNE_TARGET.get());
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

	/** Records that no stage reached {@code target} from {@code playerAt}. Returns true the first time. */
	public static boolean reportUnreachable(WorldPoint target, WorldPoint playerAt)
	{
		return UNREACHABLE.record(target, playerAt, System.currentTimeMillis());
	}

	/** Whether a quest walk to {@code target} should be skipped for now because it just proved unreachable. */
	public static boolean isBackedOff(WorldPoint target, WorldPoint playerAt)
	{
		return active && QUEST_THREAD.get() && UNREACHABLE.isBackedOff(target, playerAt, System.currentTimeMillis());
	}

	/** A quest walk reached its target: forget any earlier failure for it. */
	public static void walkArrived(WorldPoint target)
	{
		UNREACHABLE.clear(target);
	}

	/** The step target currently given up on, or {@code null} (also when the quest has moved on to another step). */
	public static WorldPoint unreachableTarget(WorldPoint playerAt)
	{
		if (!active)
		{
			return null;
		}
		WorldPoint given = UNREACHABLE.target(playerAt, System.currentTimeMillis());
		return given != null && given.equals(lastQuestWalkTarget) ? given : null;
	}

	public static long unreachableRetryInMs()
	{
		return UNREACHABLE.remainingMs(System.currentTimeMillis());
	}
}
