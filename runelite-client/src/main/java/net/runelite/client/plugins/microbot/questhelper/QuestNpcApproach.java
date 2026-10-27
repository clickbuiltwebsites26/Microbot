package net.runelite.client.plugins.microbot.questhelper;

/**
 * What an NPC step does when its NPC is known but the plain "visible and reachable, so click" test fails. Walking
 * toward an NPC the player already stands next to does nothing, so a step whose NPC was merely out of the camera's view
 * (the Varrock pub in Biohazard) sat there for a minute without a single interaction.
 */
public final class QuestNpcApproach
{
	/** Distance in tiles up to which a click is worth trying even when the planner doubts the route. */
	static final int CLICK_RANGE = 4;
	/** Distance in tiles up to which turning the camera is better than walking. */
	static final int TURN_RANGE = 6;

	/** The next move toward an NPC. */
	public enum Move
	{
		/** Click the NPC. */
		INTERACT,
		/** Rotate the camera until the NPC is on screen. */
		TURN_CAMERA,
		/** Walk closer. */
		WALK
	}

	private QuestNpcApproach()
	{
	}

	/**
	 * @param onScreen          the NPC's tile is inside the viewport
	 * @param reachableAndSeen  the route planner reaches it and there is line of sight
	 * @param distance          tiles between the player and the NPC
	 */
	public static Move decide(boolean onScreen, boolean reachableAndSeen, int distance)
	{
		if (onScreen && (reachableAndSeen || distance <= CLICK_RANGE))
		{
			return Move.INTERACT;
		}
		if (!onScreen && distance <= TURN_RANGE)
		{
			return Move.TURN_CAMERA;
		}
		return Move.WALK;
	}
}
