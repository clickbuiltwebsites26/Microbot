package net.runelite.client.plugins.microbot.questhelper.combat;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import net.runelite.client.plugins.microbot.questhelper.questhelpers.BasicQuestHelper;
import net.runelite.client.plugins.microbot.questhelper.questhelpers.QuestHelper;
import net.runelite.client.plugins.microbot.questhelper.steps.NpcStep;
import net.runelite.client.plugins.microbot.questhelper.steps.OwnerStep;
import net.runelite.client.plugins.microbot.questhelper.steps.QuestStep;

/** Decides whether a quest has fights, and whether a step is one. */
public final class QuestCombatDetector
{
	private static final Pattern KILL_TEXT = Pattern.compile("\\b(kill|defeat|fight|slay)", Pattern.CASE_INSENSITIVE);

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
}
