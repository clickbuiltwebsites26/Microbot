package net.runelite.client.plugins.microbot.ddchopper;

import net.runelite.client.plugins.microbot.shortestpath.pathfinder.QuestWalkRule;

/** Keeps the walker on foot (no ships, teleports, spirit trees) while the script runs, via QuestWalkRule. */
final class WalkerOverrides
{
	private boolean applied;

	void apply()
	{
		QuestWalkRule.setOnFootOnly(true);
		applied = true;
	}

	void clear()
	{
		if (applied)
		{
			QuestWalkRule.setOnFootOnly(false);
			applied = false;
		}
	}
}
