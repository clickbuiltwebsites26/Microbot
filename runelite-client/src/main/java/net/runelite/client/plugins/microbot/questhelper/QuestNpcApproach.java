package net.runelite.client.plugins.microbot.questhelper;

/**
 * What an NPC step does with an NPC it knows about. A click the server cannot act on (the NPC is behind a closed door)
 * repeats every tick without effect, while walking lets the walker open the door; and walking toward an NPC the player
 * already stands next to does nothing at all. So: click when the NPC can be reached or is plainly in sight and near,
 * walk otherwise. The click itself is verified by {@link VerifiedInteraction}, which also turns the camera.
 */
public final class QuestNpcApproach
{
	/** Distance in tiles up to which an NPC in line of sight is clicked even when the planner doubts the route. */
	static final int CLICK_RANGE = 8;

	/** The next move toward an NPC. */
	public enum Move
	{
		/** Click the NPC. */
		INTERACT,
		/** Walk closer. */
		WALK
	}

	private QuestNpcApproach()
	{
	}

	/**
	 * @param reachableAndSeen the route planner reaches it and there is line of sight
	 * @param lineOfSight      there is line of sight to it (no closed door or wall between)
	 * @param distance         tiles between the player and the NPC
	 */
	public static Move decide(boolean reachableAndSeen, boolean lineOfSight, int distance)
	{
		if (reachableAndSeen || (lineOfSight && distance <= CLICK_RANGE))
		{
			return Move.INTERACT;
		}
		return Move.WALK;
	}
}
