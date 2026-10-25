package net.runelite.client.plugins.microbot.questhelper.combat;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.Data;

/** What the player wants to bring to, and how to fight, the kill steps of one quest. */
@Data
public class QuestCombatLoadout
{
	public enum Potion
	{
		NONE("None", ""),
		PRAYER_POTION("Prayer potion", "Prayer potion"),
		SUPER_RESTORE("Super restore", "Super restore");

		private final String display;
		private final String itemName;

		Potion(String display, String itemName)
		{
			this.display = display;
			this.itemName = itemName;
		}

		public String getItemName()
		{
			return itemName;
		}

		@Override
		public String toString()
		{
			return display;
		}
	}

	public enum Prayer
	{
		OFF("Off"),
		QUICK("Quick prayers"),
		PROTECT_MELEE("Protect from Melee"),
		PROTECT_RANGE("Protect from Missiles"),
		PROTECT_MAGIC("Protect from Magic");

		private final String display;

		Prayer(String display)
		{
			this.display = display;
		}

		@Override
		public String toString()
		{
			return display;
		}
	}

	public enum Style
	{
		MELEE("Melee"),
		RANGED("Ranged"),
		MAGIC("Magic");

		private final String display;

		Style(String display)
		{
			this.display = display;
		}

		@Override
		public String toString()
		{
			return display;
		}
	}

	private static final Gson GSON = new Gson();

	/** Fight with whatever is worn, bring nothing. */
	private boolean skip;
	/** Inventory Setup name whose equipment gets worn; empty keeps what is worn now. */
	private String gearSetup = "";
	private String food = "Lobster";
	private int foodQty = 10;
	private int eatAtPercent = 50;
	private Potion potion = Potion.NONE;
	private int potionQty = 2;
	private Prayer prayer = Prayer.OFF;
	private int drinkAtPrayer = 20;
	private Style style = Style.MELEE;

	public static QuestCombatLoadout skipped()
	{
		QuestCombatLoadout loadout = new QuestCombatLoadout();
		loadout.skip = true;
		loadout.foodQty = 0;
		loadout.potionQty = 0;
		return loadout.normalised();
	}

	public boolean wantsFood()
	{
		return !skip && foodQty > 0 && !food.isEmpty();
	}

	public boolean wantsPotions()
	{
		return !skip && potion != Potion.NONE && potionQty > 0;
	}

	public boolean usesPrayer()
	{
		return !skip && prayer != Prayer.OFF;
	}

	/** Clamps values and fills nulls, so a hand-edited or partial JSON cannot break the fight loop. */
	public QuestCombatLoadout normalised()
	{
		gearSetup = gearSetup == null ? "" : gearSetup.trim();
		food = food == null ? "" : food.trim();
		foodQty = clamp(foodQty, 0, 27);
		eatAtPercent = clamp(eatAtPercent, 1, 99);
		potion = potion == null ? Potion.NONE : potion;
		potionQty = clamp(potionQty, 0, 27);
		prayer = prayer == null ? Prayer.OFF : prayer;
		drinkAtPrayer = clamp(drinkAtPrayer, 1, 98);
		style = style == null ? Style.MELEE : style;
		return this;
	}

	public String toJson()
	{
		return GSON.toJson(this);
	}

	/** @return the loadout, or null when the text is empty or not valid JSON */
	public static QuestCombatLoadout fromJson(String json)
	{
		if (json == null || json.trim().isEmpty())
		{
			return null;
		}
		try
		{
			QuestCombatLoadout loadout = GSON.fromJson(json, QuestCombatLoadout.class);
			return loadout == null ? null : loadout.normalised();
		}
		catch (JsonSyntaxException e)
		{
			return null;
		}
	}

	/**
	 * Builds a loadout from the Agent Server "combat" object; absent keys keep their defaults.
	 *
	 * @throws IllegalArgumentException for an unknown enum value or a non-numeric number
	 */
	public static QuestCombatLoadout fromMap(Map<?, ?> map)
	{
		QuestCombatLoadout loadout = new QuestCombatLoadout();
		if (map.get("gearSetup") != null)
		{
			loadout.gearSetup = String.valueOf(map.get("gearSetup"));
		}
		if (map.get("food") != null)
		{
			loadout.food = String.valueOf(map.get("food"));
		}
		loadout.foodQty = intValue(map, "foodQty", loadout.foodQty);
		loadout.eatAtPercent = intValue(map, "eatAtPercent", loadout.eatAtPercent);
		loadout.potion = enumValue(map, "potion", Potion.class, loadout.potion);
		loadout.potionQty = intValue(map, "potionQty", loadout.potionQty);
		loadout.prayer = enumValue(map, "prayer", Prayer.class, loadout.prayer);
		loadout.drinkAtPrayer = intValue(map, "drinkAtPrayer", loadout.drinkAtPrayer);
		loadout.style = enumValue(map, "style", Style.class, loadout.style);
		return loadout.normalised();
	}

	/** True when the quest's combat notes say magic does not work against its fights. */
	public static boolean mentionsMagicImmunity(List<String> combatRequirements)
	{
		if (combatRequirements == null)
		{
			return false;
		}
		for (String line : combatRequirements)
		{
			if (line == null)
			{
				continue;
			}
			String text = line.toLowerCase(Locale.ROOT);
			if (text.contains("immune to magic") || text.contains("cannot use magic")
				|| text.contains("can't use magic") || text.contains("magic is ineffective"))
			{
				return true;
			}
		}
		return false;
	}

	private static int clamp(int value, int min, int max)
	{
		return Math.max(min, Math.min(max, value));
	}

	private static int intValue(Map<?, ?> map, String key, int fallback)
	{
		Object value = map.get(key);
		if (value == null)
		{
			return fallback;
		}
		if (value instanceof Number)
		{
			return ((Number) value).intValue();
		}
		try
		{
			return Integer.parseInt(String.valueOf(value).trim());
		}
		catch (NumberFormatException e)
		{
			throw new IllegalArgumentException("combat." + key + " must be a number");
		}
	}

	private static <E extends Enum<E>> E enumValue(Map<?, ?> map, String key, Class<E> type, E fallback)
	{
		Object value = map.get(key);
		if (value == null)
		{
			return fallback;
		}
		String wanted = String.valueOf(value).trim().toUpperCase(Locale.ROOT).replace(' ', '_');
		for (E constant : type.getEnumConstants())
		{
			if (constant.name().equals(wanted))
			{
				return constant;
			}
		}
		throw new IllegalArgumentException("combat." + key + " must be one of "
			+ Arrays.toString(type.getEnumConstants()).toLowerCase(Locale.ROOT));
	}
}
