package net.runelite.client.plugins.microbot.questhelper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Set;
import net.runelite.client.plugins.microbot.questhelper.requirements.item.ItemRequirement;
import net.runelite.client.plugins.microbot.questhelper.requirements.item.ItemRequirements;
import net.runelite.client.plugins.microbot.questhelper.requirements.util.LogicType;
import org.junit.Test;

/** "Telegrab or 10,000 coins" (Dragon Slayer I) resolves to one concrete child a bank trip or purchase can act on. */
public class QuestCompositeRequirementsTest
{
	private final ItemRequirement coins = new ItemRequirement("Coins", 995, 10_000);
	private final ItemRequirement runes = new ItemRequirement("Law rune", 563, 1);
	private final ItemRequirements either = new ItemRequirements(LogicType.OR, "Telegrab or 10k", coins, runes);

	private static java.util.function.Predicate<ItemRequirement> in(Set<ItemRequirement> set)
	{
		return set::contains;
	}

	private static final java.util.function.Predicate<ItemRequirement> NONE = r -> false;
	private static final java.util.function.Predicate<ItemRequirement> ALL = r -> true;

	@Test
	public void aPlainRequirementIsItself()
	{
		List<ItemRequirement> out = QuestCompositeRequirements.resolve(coins, NONE, NONE, ALL);
		assertEquals(1, out.size());
		assertSame(coins, out.get(0));
	}

	@Test
	public void anOrIsMetWhenAnyChildIsOnThePlayer()
	{
		assertTrue(QuestCompositeRequirements.resolve(either, in(Set.of(runes)), ALL, ALL).isEmpty());
	}

	@Test
	public void anOrFetchesTheChildTheBankCovers()
	{
		List<ItemRequirement> out = QuestCompositeRequirements.resolve(either, NONE, in(Set.of(coins)), ALL);
		assertEquals(1, out.size());
		assertSame("withdraws exactly the 10,000 coins, not the composite", coins, out.get(0));
	}

	@Test
	public void anOrPrefersTheBankedChildOverAnEarlierBuyableOne()
	{
		List<ItemRequirement> out = QuestCompositeRequirements.resolve(either, NONE, in(Set.of(runes)), ALL);
		assertSame(runes, out.get(0));
	}

	@Test
	public void anOrBuysTheFirstTradableChildWhenTheBankHasNone()
	{
		List<ItemRequirement> out = QuestCompositeRequirements.resolve(either, NONE, NONE, in(Set.of(runes)));
		assertEquals(1, out.size());
		assertSame(runes, out.get(0));
	}

	@Test
	public void anOrFallsBackToTheFirstChildWhenNothingCanBeFetched()
	{
		List<ItemRequirement> out = QuestCompositeRequirements.resolve(either, NONE, NONE, NONE);
		assertSame(coins, out.get(0));
	}

	@Test
	public void anAndExpandsToItsUnmetChildren()
	{
		ItemRequirements both = new ItemRequirements(LogicType.AND, "Both", coins, runes);
		List<ItemRequirement> out = QuestCompositeRequirements.resolve(both, in(Set.of(coins)), ALL, ALL);
		assertEquals(1, out.size());
		assertSame(runes, out.get(0));
		assertEquals(2, QuestCompositeRequirements.resolve(both, NONE, ALL, ALL).size());
	}

	@Test
	public void aNestedCompositeResolvesToLeaves()
	{
		ItemRequirement bread = new ItemRequirement("Bread", 2309, 1);
		ItemRequirements inner = new ItemRequirements(LogicType.AND, "Inner", runes, bread);
		ItemRequirements outer = new ItemRequirements(LogicType.OR, "Outer", coins, inner);
		List<ItemRequirement> out = QuestCompositeRequirements.resolve(outer, NONE, in(Set.of(bread)), ALL);
		assertEquals(2, out.size());
		assertSame(runes, out.get(0));
		assertSame(bread, out.get(1));
	}
}
