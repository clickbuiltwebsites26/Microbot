package net.runelite.client.plugins.microbot.questhelper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

/** Gertrude's Cat: the lumberyard crates are searched once each, reachable first, nearest first. */
public class QuestSearchOrderTest
{
	private static final class Crate
	{
		final String name;
		final boolean reachable;
		final int distance;

		Crate(String name, boolean reachable, int distance)
		{
			this.name = name;
			this.reachable = reachable;
			this.distance = distance;
		}
	}

	private static final java.util.Comparator<Crate> ORDER = java.util.Comparator
		.comparingInt((Crate c) -> c.reachable ? 0 : 1).thenComparingInt(c -> c.distance);

	private static String next(List<Crate> crates, Set<String> searched)
	{
		Crate crate = QuestObjectApproach.nextToSearch(crates, c -> searched.contains(c.name), ORDER);
		return crate == null ? null : crate.name;
	}

	@Test
	public void eachCrateIsSearchedOnceNearestFirstAndUnreachableOnesLast()
	{
		List<Crate> crates = Arrays.asList(
			new Crate("far", true, 9), new Crate("blocked", false, 1), new Crate("near", true, 2), new Crate("mid", true, 5));
		Set<String> searched = new HashSet<>();
		StringBuilder order = new StringBuilder();
		for (int i = 0; i < 4; i++)
		{
			String crate = next(crates, searched);
			order.append(crate).append(' ');
			searched.add(crate);
		}
		assertEquals("near mid far blocked ", order.toString());
	}

	@Test
	public void afterEveryCrateWasTriedItGoesRoundAgainFromTheBest()
	{
		List<Crate> crates = Arrays.asList(new Crate("a", true, 4), new Crate("b", true, 1));
		assertEquals("b", next(crates, new HashSet<>(Arrays.asList("a", "b"))));
	}

	@Test
	public void noCratesGivesNothing()
	{
		assertNull(next(Collections.emptyList(), new HashSet<>()));
	}
}
