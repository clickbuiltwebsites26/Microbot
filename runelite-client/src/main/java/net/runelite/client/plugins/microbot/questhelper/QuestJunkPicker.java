package net.runelite.client.plugins.microbot.questhelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Which carried items a full inventory may drop to make room for a step's pickup: anything the quest does not need, the
 * player did not bring, that is cheap, tradeable, not stacked and has a Drop action. Pure, so it can be tested.
 */
public final class QuestJunkPicker
{
	/** Items worth this much each or more are gear or valuables, not junk. */
	public static final long VALUABLE_VALUE = 1_000;

	/** Free slots to aim for, so one pickup does not bring the next "no space" straight back. */
	public static final int TARGET_FREE_SLOTS = 3;

	/** One inventory slot as the decision sees it; {@code value} is the price of a single item. */
	public static final class Item
	{
		final int id;
		final String name;
		final long value;
		final boolean stackable;
		final boolean noted;
		final boolean tradeable;
		final boolean hasDrop;

		public Item(int id, String name, long value, boolean stackable, boolean noted, boolean tradeable, boolean hasDrop)
		{
			this.id = id;
			this.name = name;
			this.value = value;
			this.stackable = stackable;
			this.noted = noted;
			this.tradeable = tradeable;
			this.hasDrop = hasDrop;
		}

		public int id()
		{
			return id;
		}

		public String name()
		{
			return name;
		}

		/** Not needed by the quest or the player: the only items that may leave the inventory at all. */
		private boolean unclaimed(Set<Integer> protectedIds)
		{
			return !protectedIds.contains(id) && !stackable && !noted;
		}

		private boolean droppable(Set<Integer> protectedIds)
		{
			// untradeable items may be quest items the quest never lists; a Drop-less item cannot be dropped anyway
			return unclaimed(protectedIds) && hasDrop && tradeable && value < VALUABLE_VALUE;
		}
	}

	private QuestJunkPicker()
	{
	}

	/**
	 * @param protectedIds items the quest still needs and the supplies the player or the preflight brought
	 * @return the slots to drop, cheapest first, as many as it takes to reach {@link #TARGET_FREE_SLOTS} free slots
	 */
	public static List<Item> pickJunk(List<Item> items, Set<Integer> protectedIds, int freeSlots)
	{
		List<Item> junk = new ArrayList<>();
		for (Item item : items)
		{
			if (item.droppable(protectedIds))
			{
				junk.add(item);
			}
		}
		junk.sort(Comparator.comparingLong((Item i) -> i.value).thenComparing(i -> i.name == null ? "" : i.name));
		int wanted = Math.max(0, TARGET_FREE_SLOTS - freeSlots);
		return junk.size() > wanted ? new ArrayList<>(junk.subList(0, wanted)) : junk;
	}

	/** Slots nothing claims that the drop rules keep (valuables, untradeables): what a bank trip could still clear. */
	public static List<Item> bankable(List<Item> items, Set<Integer> protectedIds)
	{
		List<Item> out = new ArrayList<>();
		for (Item item : items)
		{
			if (item.unclaimed(protectedIds) && !item.droppable(protectedIds))
			{
				out.add(item);
			}
		}
		return out;
	}
}
