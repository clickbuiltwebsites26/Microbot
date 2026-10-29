package net.runelite.client.plugins.microbot.questhelper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.runelite.client.plugins.microbot.questhelper.requirements.item.ItemRequirement;
import net.runelite.client.plugins.microbot.questhelper.requirements.item.ItemRequirements;
import net.runelite.client.plugins.microbot.questhelper.requirements.util.LogicType;

/**
 * Turns a missing item requirement into the concrete items to fetch. A composite requirement ("telegrab or 10,000
 * coins") has no id or quantity of its own, so a bank trip or a purchase cannot act on it: it resolves to one child
 * (OR) or to all of its unmet children (AND). Pure, so the choice can be tested.
 */
public final class QuestCompositeRequirements
{
	private QuestCompositeRequirements()
	{
	}

	public static boolean isComposite(ItemRequirement requirement)
	{
		return requirement instanceof ItemRequirements;
	}

	/**
	 * @param requirement the requirement that is not met
	 * @param onPlayer    whether a requirement is met by what the player carries or wears
	 * @param bankCovers  whether the bank holds enough of a requirement to withdraw it
	 * @param tradable    whether a requirement can be bought on the Grand Exchange
	 * @return the concrete requirements to fetch; empty when the composite is already met by one of its children
	 */
	public static List<ItemRequirement> resolve(ItemRequirement requirement, Predicate<ItemRequirement> onPlayer,
		Predicate<ItemRequirement> bankCovers, Predicate<ItemRequirement> tradable)
	{
		List<ItemRequirement> out = new ArrayList<>();
		collect(requirement, onPlayer, bankCovers, tradable, out);
		return out;
	}

	private static void collect(ItemRequirement requirement, Predicate<ItemRequirement> onPlayer,
		Predicate<ItemRequirement> bankCovers, Predicate<ItemRequirement> tradable, List<ItemRequirement> out)
	{
		if (!(requirement instanceof ItemRequirements))
		{
			out.add(requirement);
			return;
		}
		ItemRequirements composite = (ItemRequirements) requirement;
		List<ItemRequirement> children = composite.getItemRequirements();
		if (children == null || children.isEmpty())
		{
			return;
		}
		if (composite.getLogicType() == LogicType.OR)
		{
			for (ItemRequirement child : children)
			{
				if (onPlayer.test(child))
				{
					return;
				}
			}
			ItemRequirement chosen = pick(children, bankCovers);
			if (chosen == null)
			{
				chosen = pick(children, tradable);
			}
			collect(chosen != null ? chosen : children.get(0), onPlayer, bankCovers, tradable, out);
			return;
		}
		for (ItemRequirement child : children)
		{
			if (!onPlayer.test(child))
			{
				collect(child, onPlayer, bankCovers, tradable, out);
			}
		}
	}

	/** The first child the test accepts; a nested composite counts when any of its leaves is accepted. */
	private static ItemRequirement pick(List<ItemRequirement> children, Predicate<ItemRequirement> test)
	{
		for (ItemRequirement child : children)
		{
			if (child instanceof ItemRequirements)
			{
				for (ItemRequirement leaf : resolve(child, c -> false, c -> true, c -> true))
				{
					if (test.test(leaf))
					{
						return child;
					}
				}
			}
			else if (test.test(child))
			{
				return child;
			}
		}
		return null;
	}
}
