package net.runelite.client.plugins.microbot.questhelper.combat;

import java.util.Collection;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Charged items (glories, rings of wealth, games necklaces) and dosed potions are separate item ids per charge or
 * dose, named "Ring of wealth (4)" or "Prayer potion(3)". A saved setup that recorded one charge must still match
 * any other, so names are compared with the trailing charge/dose stripped.
 */
public final class ChargedItemNames
{
	private static final Pattern SUFFIX = Pattern.compile("\\s*\\((?:t)?(\\d+)\\)\\s*$", Pattern.CASE_INSENSITIVE);

	private ChargedItemNames()
	{
	}

	/** Lower-case name without a trailing " (n)" / "(n)" charge or dose; empty for null. */
	public static String baseName(String name)
	{
		if (name == null)
		{
			return "";
		}
		return SUFFIX.matcher(name.trim()).replaceFirst("").trim().toLowerCase(Locale.ROOT);
	}

	/** The charge or dose in the name; 0 when it has no suffix (the uncharged base item). */
	public static int charge(String name)
	{
		if (name == null)
		{
			return 0;
		}
		Matcher m = SUFFIX.matcher(name.trim());
		return m.find() ? Integer.parseInt(m.group(1)) : 0;
	}

	public static boolean sameFamily(String a, String b)
	{
		String base = baseName(a);
		return !base.isEmpty() && base.equals(baseName(b));
	}

	/**
	 * Picks the best candidate for a wanted item: the exact id, else the highest charge of the same family, else
	 * the uncharged base. Returns null when nothing matches.
	 */
	public static <T> T pick(Collection<T> candidates, ToIntFunction<T> id, Function<T, String> name,
		int wantedId, String wantedName)
	{
		T best = null;
		int bestCharge = -1;
		for (T candidate : candidates)
		{
			if (candidate == null)
			{
				continue;
			}
			if (wantedId > 0 && id.applyAsInt(candidate) == wantedId)
			{
				return candidate;
			}
			String candidateName = name.apply(candidate);
			if (wantedName != null && !wantedName.isEmpty() && sameFamily(candidateName, wantedName))
			{
				int charge = charge(candidateName);
				if (charge > bestCharge)
				{
					best = candidate;
					bestCharge = charge;
				}
			}
		}
		return best;
	}
}
