package net.runelite.client.plugins.microbot.questhelper.combat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.ItemComposition;
import net.runelite.api.NPCComposition;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.questhelper.questhelpers.BasicQuestHelper;
import net.runelite.client.plugins.microbot.questhelper.questhelpers.QuestHelper;
import net.runelite.client.plugins.microbot.questhelper.requirements.item.ItemRequirement;
import net.runelite.client.plugins.microbot.questhelper.steps.NpcStep;
import net.runelite.client.plugins.microbot.questhelper.steps.OwnerStep;
import net.runelite.client.plugins.microbot.questhelper.steps.QuestStep;

/** Decides whether a quest has fights, which ones, and whether a step is one. */
public final class QuestCombatDetector
{
	private static final Pattern KILL_TEXT = Pattern.compile("\\b(kill|defeat|fight|slay)", Pattern.CASE_INSENSITIVE);
	private static final Pattern TARGET = Pattern.compile(
		"\\b(?:kill|defeat|fight|slay)\\s+(?:the\\s+|a\\s+|an\\s+|some\\s+|all\\s+|any\\s+)?(?:\\d+\\s+)?(.+?)"
			+ "(?=\\s*\\(|[.,;!:]|\\s+(?:to|for|with|in|at|on|using|until|and|so|from|by|inside|outside|near"
			+ "|before|after|while|if|when)\\b|$)",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern LEVEL = Pattern.compile("\\(level\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
	private static final Map<Integer, Boolean> STACKABLE = new ConcurrentHashMap<>();
	private static final int RESERVE_HEADROOM = 4;

	/** One distinct fight of a quest: every kill step against the same target. */
	public static final class Fight
	{
		public final String key;
		public final String label;
		/** Combat notes naming the target, else the step text; shown next to the fight. */
		public final List<String> notes;
		/** Notes plus step text; what the style restriction check reads. */
		public final List<String> restrictionTexts;

		Fight(String key, String label, List<String> notes, List<String> restrictionTexts)
		{
			this.key = key;
			this.label = label;
			this.notes = notes;
			this.restrictionTexts = restrictionTexts;
		}
	}

	private QuestCombatDetector()
	{
	}

	public static boolean isKillText(List<String> text)
	{
		if (text == null)
		{
			return false;
		}
		for (String line : text)
		{
			if (line != null && KILL_TEXT.matcher(line).find())
			{
				return true;
			}
		}
		return false;
	}

	public static boolean isKillStep(QuestStep step)
	{
		return step instanceof NpcStep && isKillText(step.getText());
	}

	public static boolean hasFights(QuestHelper quest)
	{
		List<String> requirements = quest.getCombatRequirements();
		if (requirements != null && !requirements.isEmpty())
		{
			return true;
		}

		Set<QuestStep> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		if (quest instanceof BasicQuestHelper && ((BasicQuestHelper) quest).getStepList() != null
			&& containsKillStep(((BasicQuestHelper) quest).getStepList().values(), seen))
		{
			return true;
		}
		QuestStep current = quest.getCurrentStep();
		return current != null && containsKillStep(Collections.singleton(current), seen);
	}

	private static boolean containsKillStep(Collection<QuestStep> steps, Set<QuestStep> seen)
	{
		for (QuestStep step : steps)
		{
			if (step == null || !seen.add(step))
			{
				continue;
			}
			if (isKillStep(step))
			{
				return true;
			}
			if (step instanceof OwnerStep && containsKillStep(((OwnerStep) step).getSteps(), seen))
			{
				return true;
			}
		}
		return false;
	}

	/** Target name in a kill step text ("Kill the Temple Guardian (level 30)." gives "Temple Guardian"), or null. */
	static String parseTarget(List<String> text)
	{
		if (text == null)
		{
			return null;
		}
		for (String line : text)
		{
			if (line == null)
			{
				continue;
			}
			Matcher matcher = TARGET.matcher(line.replaceAll("<[^>]*>", ""));
			if (matcher.find())
			{
				String name = matcher.group(1).trim();
				if (!name.isEmpty())
				{
					return name;
				}
			}
		}
		return null;
	}

	static int parseLevel(List<String> text)
	{
		if (text != null)
		{
			for (String line : text)
			{
				Matcher matcher = line == null ? null : LEVEL.matcher(line);
				if (matcher != null && matcher.find())
				{
					return Integer.parseInt(matcher.group(1));
				}
			}
		}
		return 0;
	}

	private static String targetName(NpcStep step)
	{
		if (step.getNpcName() != null && !step.getNpcName().trim().isEmpty())
		{
			return step.getNpcName().trim();
		}
		String parsed = parseTarget(step.getText());
		if (parsed != null)
		{
			return parsed;
		}
		try
		{
			NPCComposition composition = Microbot.getClientThread()
				.runOnClientThreadOptional(() -> Microbot.getClient().getNpcDefinition(step.getNpcID())).orElse(null);
			if (composition != null && composition.getName() != null && !composition.getName().isEmpty())
			{
				return composition.getName();
			}
		}
		catch (RuntimeException ignored)
		{
			// fall through to the generic label
		}
		return "Fight";
	}

	/** Key of the fight a kill step belongs to, or null when the step is not a kill step. */
	public static String fightKeyOf(QuestStep step)
	{
		if (!isKillStep(step))
		{
			return null;
		}
		return QuestCombatLoadout.fightKey(targetName((NpcStep) step));
	}

	/** Every distinct fight in the quest's kill steps, in step order. */
	public static List<Fight> enumerateFights(QuestHelper quest)
	{
		List<NpcStep> kills = new ArrayList<>();
		Set<QuestStep> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		if (quest instanceof BasicQuestHelper && ((BasicQuestHelper) quest).getStepList() != null)
		{
			collectKillSteps(((BasicQuestHelper) quest).getStepList().values(), seen, kills);
		}
		if (quest.getCurrentStep() != null)
		{
			collectKillSteps(Collections.singleton(quest.getCurrentStep()), seen, kills);
		}

		List<String> requirements = quest.getCombatRequirements() == null
			? Collections.<String>emptyList() : quest.getCombatRequirements();
		Map<String, Fight> fights = new LinkedHashMap<>();
		for (NpcStep step : kills)
		{
			String name = targetName(step);
			String key = QuestCombatLoadout.fightKey(name);
			if (key.isEmpty() || fights.containsKey(key))
			{
				continue;
			}
			int level = parseLevel(step.getText());
			String label = level > 0 ? name + " (level " + level + ")" : name;
			List<String> notes = new ArrayList<>();
			for (String line : requirements)
			{
				if (line != null && line.toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT)))
				{
					notes.add(line);
				}
			}
			List<String> stepText = step.getText() == null ? Collections.<String>emptyList() : step.getText();
			List<String> restriction = new ArrayList<>(notes);
			restriction.addAll(stepText);
			if (notes.isEmpty())
			{
				notes.addAll(stepText);
			}
			fights.put(key, new Fight(key, label, notes, restriction));
		}
		return new ArrayList<>(fights.values());
	}

	private static void collectKillSteps(Collection<QuestStep> steps, Set<QuestStep> seen, List<NpcStep> out)
	{
		for (QuestStep step : steps)
		{
			if (step == null || !seen.add(step))
			{
				continue;
			}
			if (isKillStep(step))
			{
				out.add((NpcStep) step);
			}
			if (step instanceof OwnerStep)
			{
				collectKillSteps(((OwnerStep) step).getSteps(), seen, out);
			}
		}
	}

	/** Text the style restriction check reads for one fight: matching combat notes plus the step text. */
	public static List<String> restrictionTextsFor(QuestHelper quest, QuestStep step, String key)
	{
		List<String> out = new ArrayList<>();
		if (quest != null && key != null)
		{
			for (Fight fight : enumerateFights(quest))
			{
				if (fight.key.equals(key))
				{
					out.addAll(fight.restrictionTexts);
				}
			}
		}
		if (out.isEmpty() && step != null && step.getText() != null)
		{
			out.addAll(step.getText());
		}
		return out;
	}

	/**
	 * Suggested free slots to keep for quest items: the distinct unstackable, non-equipped quest item requirements
	 * plus a little headroom for items picked up along the way.
	 */
	public static int defaultReservedSlots(QuestHelper quest)
	{
		int count = 0;
		List<ItemRequirement> requirements = quest == null ? null : quest.getItemRequirements();
		if (requirements != null)
		{
			for (ItemRequirement requirement : requirements)
			{
				if (requirement == null || requirement.mustBeEquipped() || requirement.getQuantity() <= 0
					|| requirement.getAllIds().isEmpty())
				{
					continue;
				}
				Integer id = requirement.getAllIds().get(0);
				if (id != null && id > 0 && !isStackable(id))
				{
					count++;
				}
			}
		}
		return Math.min(27, count + RESERVE_HEADROOM);
	}

	private static boolean isStackable(int id)
	{
		Boolean cached = STACKABLE.get(id);
		if (cached == null)
		{
			cached = Microbot.getClientThread().runOnClientThreadOptional(() ->
			{
				ItemComposition def = Microbot.getClient().getItemDefinition(id);
				return def != null && def.isStackable();
			}).orElse(null);
			if (cached == null)
			{
				return true;
			}
			STACKABLE.put(id, cached);
		}
		return cached;
	}
}
