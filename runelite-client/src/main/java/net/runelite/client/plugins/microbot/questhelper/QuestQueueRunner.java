package net.runelite.client.plugins.microbot.questhelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.runelite.api.QuestState;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.questhelper.planner.QuestApproval;
import net.runelite.client.plugins.microbot.questhelper.planner.QuestPlannerFilter;
import net.runelite.client.plugins.microbot.questhelper.planner.QuestPlannerLauncher;
import net.runelite.client.plugins.microbot.questhelper.planner.QuestProfile;
import net.runelite.client.plugins.microbot.questhelper.planner.QuestQueue;
import net.runelite.client.plugins.microbot.questhelper.planner.QuestRandomPicker;
import net.runelite.client.plugins.microbot.questhelper.questhelpers.QuestHelper;
import net.runelite.client.plugins.microbot.questhelper.questinfo.QuestHelperQuest;
import org.slf4j.event.Level;

/**
 * Runs the {@link QuestQueue} from the quest script's tick: starts the current quest with its profile, moves on when it
 * is finished, picks the next one in random mode (re-checking requirements each time) and rests between quests. The
 * game-bound parts (preflight, banking, stopping) come from the script through {@link Host}.
 */
public final class QuestQueueRunner
{
	/** What the runner needs from the quest script. */
	public interface Host
	{
		QuestHelperPlugin plugin();

		boolean ironman();

		/** Buys, in one go, what the remaining quests need; returns true while it is still working. */
		boolean runQueuePreflight(List<QuestProfile> remaining);

		/** The player needs items for this quest that they did not allow buying and do not own. */
		boolean needsBlockedItems(QuestHelper helper, QuestProfile profile);

		/** Rest until the given time, as a short break (lowers fatigue, quiets the watchdog). */
		void rest(long untilMs);

		/** Session fatigue from 0 to 1. */
		double fatigue();

		/** Turns automation off. */
		void turnOff(String reason);
	}

	private static final long START_RETRY_MS = 4_000L;

	/** The profile and approval the quest window gave the quest that is starting or running; null when none. */
	private volatile QuestProfile windowProfile;
	private volatile QuestApproval windowApproval;

	private final Host host;
	private final Random random = new Random();
	private long lastStartAttemptMs;

	public QuestQueueRunner(Host host)
	{
		this.host = host;
	}

	private QuestQueue queue()
	{
		return QuestQueue.shared();
	}

	/** True when a queue run is in charge. */
	public boolean active()
	{
		return queue().isActive();
	}

	/** The profile of the running queued quest, or null. */
	public QuestProfile current()
	{
		return queue().isActive() ? queue().current() : null;
	}

	/** Remembers what the quest window decided for the quest that is starting (null clears it). */
	public void setWindowResult(QuestProfile profile, QuestApproval approval)
	{
		windowProfile = profile;
		windowApproval = approval;
	}

	/** The profile that governs the running quest: the window's, else the queue's, else none. */
	public QuestProfile profileInForce()
	{
		QuestProfile window = windowProfile;
		return window != null ? window : current();
	}

	/** Missing-item rule of the running quest (window or queue profile), or null when there is none. */
	public Boolean obtainOverride()
	{
		QuestProfile profile = profileInForce();
		return profile == null ? null : profile.geAllowed() && !host.ironman();
	}

	private QuestApproval approvalInForce()
	{
		QuestApproval window = windowApproval;
		if (window != null)
		{
			return window;
		}
		return queue().isActive() ? queue().approval() : null;
	}

	public boolean hasApproval()
	{
		return approvalInForce() != null;
	}

	/** Whether a purchase of these items (and total cost) is inside what the player approved. */
	public boolean covers(List<String> names, long totalGp)
	{
		QuestApproval approval = approvalInForce();
		return approval != null && approval.covers(names, totalGp);
	}

	/** Counts a purchase against the approval. */
	public void recordSpend(long totalGp)
	{
		QuestApproval approval = approvalInForce();
		if (approval != null)
		{
			approval.record(totalGp);
			if (approval == queue().approval())
			{
				queue().save();
			}
		}
	}

	public String refusal(List<String> names, long totalGp)
	{
		QuestApproval approval = approvalInForce();
		return approval == null ? "" : approval.explainRefusal(names, totalGp);
	}

	/**
	 * One tick. Returns true when the queue used it and the quest script must not act on a quest this tick.
	 */
	public boolean tick(long now)
	{
		QuestQueue queue = queue();
		if (!queue.isActive())
		{
			return false;
		}
		QuestHelperPlugin plugin = host.plugin();
		if (now < queue.pauseUntilMs())
		{
			Microbot.status = "Quest queue: resting before the next quest";
			host.rest(queue.pauseUntilMs());
			return true;
		}
		QuestProfile current = queue.current();
		if (current == null)
		{
			if (!queue.isRandom())
			{
				queue.stop();
				Microbot.log("Quest queue finished", Level.INFO);
				return false;
			}
			return pickNext(now);
		}
		if (!queue.isRandom() && !queue.isPreflightDone() && plugin.getSelectedQuest() == null)
		{
			if (host.runQueuePreflight(queue.remaining()))
			{
				return true;
			}
			queue.setPreflightDone(true);
		}
		QuestHelper helper = QuestHelperQuest.getByName(current.getQuestName());
		if (helper == null || helper.getQuest() == null)
		{
			Microbot.log("Quest queue: unknown quest " + current.getQuestName() + ", skipping it", Level.WARN);
			skipCurrent("unknown quest");
			return true;
		}
		QuestState state = Microbot.getClientThread().runOnClientThreadOptional(
			() -> helper.getState(Microbot.getClient())).orElse(null);
		QuestHelper selected = plugin.getSelectedQuest();
		boolean selectedIsCurrent = selected != null && selected.getQuest().getName().equals(current.getQuestName());
		if (state == QuestState.FINISHED)
		{
			Microbot.log("Quest queue: " + current.getQuestName() + " is complete", Level.INFO);
			if (selectedIsCurrent)
			{
				shutDownSelected(plugin);
			}
			finishCurrent(now);
			return true;
		}
		if (selectedIsCurrent)
		{
			return false;
		}
		if (selected != null)
		{
			return false; // another quest was picked by hand; leave it alone
		}
		if (now - lastStartAttemptMs < START_RETRY_MS)
		{
			return true;
		}
		lastStartAttemptMs = now;
		QuestPlannerLauncher.applyProfile(current);
		Microbot.log("Quest queue: starting " + current.getQuestName() + " (" + current.getType() + ")", Level.INFO);
		if (!plugin.startQuestHelper(current.getQuestName()))
		{
			Microbot.log("Quest queue: could not start " + current.getQuestName() + ", skipping it", Level.WARN);
			skipCurrent("could not start");
		}
		return true;
	}

	private void finishCurrent(long now)
	{
		QuestQueue queue = queue();
		boolean random = queue.isRandom();
		queue.advance();
		if (random && queue.isActive())
		{
			long pause = QuestRandomPickerAccess.pause(queue, host.fatigue(), this.random);
			queue.setPauseUntilMs(now + pause);
			Microbot.log(String.format("Quest queue: resting %.1f min before the next quest", pause / 60_000.0), Level.INFO);
		}
	}

	/** Gives up on the running queued quest: remembers it as skipped, deselects it and moves on. */
	public void skipCurrent(String reason)
	{
		QuestQueue queue = queue();
		QuestProfile current = queue.current();
		if (current == null)
		{
			return;
		}
		Microbot.log("Quest queue: skipping " + current.getQuestName() + ": " + reason, Level.WARN);
		queue.markSkipped(current.getQuestName());
		shutDownSelected(host.plugin());
		finishCurrent(System.currentTimeMillis());
	}

	/**
	 * A quest stopped for a reason that would normally switch automation off. While a queue runs the quest is skipped
	 * instead and the queue goes on. Returns true when handled here.
	 */
	public boolean onStop(String reason)
	{
		if (queue().isActive() && queue().current() != null)
		{
			skipCurrent(reason);
			return true;
		}
		return false;
	}

	private void shutDownSelected(QuestHelperPlugin plugin)
	{
		if (plugin.getSelectedQuest() != null)
		{
			Microbot.getClientThread().invokeLater(() -> plugin.getQuestManager().shutDownQuest(true));
		}
	}

	/** Random mode: chooses the next doable quest, or ends the run when nothing is left. */
	private boolean pickNext(long now)
	{
		QuestQueue queue = queue();
		List<QuestPlannerFilter.Row> rows = QuestPlannerLauncher.buildRows();
		List<QuestRandomPicker.Candidate> candidates = new ArrayList<>();
		for (QuestPlannerFilter.Row row : rows)
		{
			boolean other = false;
			QuestHelper helper = QuestHelperQuest.getByName(row.name);
			if (helper != null && helper.getQuest() != null)
			{
				other = QuestPreflight.anotherPlayerFailure(helper.getQuest().name(), row.name) != null;
			}
			candidates.add(new QuestRandomPicker.Candidate(row.name, row.available, other, false));
		}
		while (true)
		{
			String name = QuestRandomPicker.pick(candidates, queue.pool(), queue.skipped(), random);
			if (name == null)
			{
				Microbot.log("Quest queue: no doable quest is left, stopping", Level.INFO);
				queue.stop();
				host.turnOff("Quest queue finished: nothing doable is left");
				return false;
			}
			QuestHelper helper = QuestHelperQuest.getByName(name);
			QuestProfile profile = queue.savedProfile(name);
			if (profile == null)
			{
				profile = QuestPlannerLauncher.suggestedProfile(name);
			}
			if (helper != null && host.needsBlockedItems(helper, profile))
			{
				for (int i = 0; i < candidates.size(); i++)
				{
					QuestRandomPicker.Candidate old = candidates.get(i);
					if (old.name.equals(name))
					{
						candidates.set(i, new QuestRandomPicker.Candidate(name, old.doable, old.needsOtherPlayer, true));
					}
				}
				continue;
			}
			queue.setRandomCurrent(profile);
			Microbot.log("Quest queue: picked " + name + " at random (" + profile.getType() + ")", Level.INFO);
			return true;
		}
	}

	/** Pause length helper kept separate so the picker class stays free of queue types. */
	private static final class QuestRandomPickerAccess
	{
		static long pause(QuestQueue queue, double fatigue, Random random)
		{
			return QuestRandomPicker.pauseMs(queue.pauseMinMinutes(), queue.pauseMaxMinutes(), fatigue, random);
		}
	}
}
