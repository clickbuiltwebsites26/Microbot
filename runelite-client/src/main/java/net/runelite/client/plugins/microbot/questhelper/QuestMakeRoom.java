package net.runelite.client.plugins.microbot.questhelper;

import java.util.List;

/**
 * What to do when a full inventory stands between the player and an item to pick up ("You don't have enough inventory
 * space to hold that item"): eat if hurt and carrying food, otherwise drop the least valuable thing the quest does not
 * need. Never an item a quest step asks for. Pure, so the decision can be tested.
 */
public final class QuestMakeRoom
{
	/** One inventory item as the decision sees it. */
	public static final class Slot
	{
		final int id;
		final String name;
		final long value;
		final boolean food;
		final boolean needed;

		public Slot(int id, String name, long value, boolean food, boolean needed)
		{
			this.id = id;
			this.name = name;
			this.value = value;
			this.food = food;
			this.needed = needed;
		}
	}

	public enum Action
	{
		/** A slot is free already. */
		NONE,
		/** Eat food: it heals and frees the slot. */
		EAT,
		/** Drop the item in {@link Decision#slot}. */
		DROP,
		/** Everything carried is needed: nothing can be freed. */
		STUCK
	}

	public static final class Decision
	{
		public final Action action;
		public final Slot slot;

		Decision(Action action, Slot slot)
		{
			this.action = action;
			this.slot = slot;
		}
	}

	private QuestMakeRoom()
	{
	}

	/**
	 * @param freeSlots   free inventory slots right now
	 * @param hurt        hitpoints are below the maximum, so eating is not wasted
	 * @param items       one entry per distinct item stack carried
	 */
	public static Decision decide(int freeSlots, boolean hurt, List<Slot> items)
	{
		if (freeSlots > 0)
		{
			return new Decision(Action.NONE, null);
		}
		if (hurt)
		{
			for (Slot slot : items)
			{
				if (slot.food && !slot.needed)
				{
					return new Decision(Action.EAT, slot);
				}
			}
		}
		Slot cheapest = null;
		for (Slot slot : items)
		{
			if (slot.needed)
			{
				continue;
			}
			if (cheapest == null || slot.value < cheapest.value)
			{
				cheapest = slot;
			}
		}
		return cheapest == null ? new Decision(Action.STUCK, null) : new Decision(Action.DROP, cheapest);
	}
}
