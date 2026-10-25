package net.runelite.client.plugins.microbot.questhelper.combat;

import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.questhelper.QuestHelperConfig;
import net.runelite.client.plugins.microbot.questhelper.questhelpers.QuestHelper;
import net.runelite.client.plugins.microbot.questhelper.steps.QuestStep;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2CombatSpells;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import org.slf4j.event.Level;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * Per-quest combat setup: asks once for a loadout, wears and stocks it before the first kill step,
 * then eats, drinks and prays while fighting.
 * {@link #beginStep} and {@link #prepareForKill} run on the quest script thread, {@link #fightTick}
 * on its own short-period task so it keeps working while the script thread waits out a fight.
 */
public class QuestCombatHandler
{
	private static final AtomicBoolean PROMPT_IN_FLIGHT = new AtomicBoolean(false);
	private static final int MAX_BANK_ATTEMPTS = 4;
	private static final int MAX_RESTOCKS = 3;
	private static final long CONSUME_COOLDOWN_MS = 1800L;
	private static final long PRAYER_OFF_DELAY_MS = 4000L;

	private enum Phase
	{
		UNRESOLVED, PROMPTING, ACTIVE, NONE
	}

	private final Supplier<QuestHelperConfig> config;
	private final Supplier<ScheduledFuture<?>> scheduler;
	private final Consumer<String> stopper;

	private volatile Phase phase = Phase.UNRESOLVED;
	private volatile String questKey;
	private volatile QuestCombatLoadout loadout;
	private volatile List<String> combatNotes;
	private int generation;
	private final AtomicReference<QuestCombatSetupDialog.Result> pendingAnswer = new AtomicReference<>();

	private boolean gearDone;
	private int gearAttempts;
	private boolean styleDone;
	private boolean supplied;
	private int bankAttempts;
	private int restocks;

	private volatile boolean prayerOnByUs;
	private volatile long lastInCombatMs;
	private volatile long lastConsumeMs;

	public QuestCombatHandler(Supplier<QuestHelperConfig> config, Supplier<ScheduledFuture<?>> scheduler,
		Consumer<String> stopper)
	{
		this.config = config;
		this.scheduler = scheduler;
		this.stopper = stopper;
	}

	/** Forget the current quest's state; the next quest tick starts from scratch. */
	public synchronized void reset()
	{
		generation++;
		questKey = null;
		loadout = null;
		combatNotes = null;
		phase = Phase.UNRESOLVED;
		pendingAnswer.set(null);
		gearDone = false;
		gearAttempts = 0;
		styleDone = false;
		supplied = false;
		bankAttempts = 0;
		restocks = 0;
		lastConsumeMs = 0;
	}

	/**
	 * Resolves the quest's loadout (saved, or asked once) and wears the chosen gear.
	 *
	 * @return true when this tick was used and the script should not act further
	 */
	public boolean beginStep(QuestHelper quest)
	{
		String key = quest.getQuest().name();
		if (!key.equals(questKey))
		{
			reset();
			questKey = key;
		}

		switch (phase)
		{
			case NONE:
				return false;
			case PROMPTING:
				return awaitAnswer(quest);
			case UNRESOLVED:
				return resolve(quest);
			default:
				break;
		}

		QuestCombatLoadout current = loadout;
		if (current == null || current.isSkip() || gearDone || current.getGearSetup().isEmpty())
		{
			return false;
		}
		return wearGear(current);
	}

	private boolean resolve(QuestHelper quest)
	{
		if (quest.getCurrentStep() == null)
		{
			return false;
		}
		if (!QuestCombatDetector.hasFights(quest))
		{
			phase = Phase.NONE;
			return false;
		}

		QuestCombatLoadout saved = QuestCombatStore.load(questKey);
		if (saved != null)
		{
			loadout = saved;
			phase = saved.isSkip() ? Phase.NONE : Phase.ACTIVE;
			return false;
		}
		if (!config.get().askCombatSetup())
		{
			phase = Phase.NONE;
			return false;
		}

		combatNotes = quest.getCombatRequirements();
		phase = Phase.PROMPTING;
		return awaitAnswer(quest);
	}

	private boolean awaitAnswer(QuestHelper quest)
	{
		Microbot.status = "Quest helper: choose your combat setup in the dialog";
		QuestCombatSetupDialog.Result answer = pendingAnswer.getAndSet(null);
		if (answer == null)
		{
			if (PROMPT_IN_FLIGHT.compareAndSet(false, true))
			{
				final int askedIn = generation;
				QuestCombatSetupDialog.showAsync(quest.getQuest().getName(), combatNotes, result ->
				{
					PROMPT_IN_FLIGHT.set(false);
					if (askedIn == generation)
					{
						pendingAnswer.set(result);
					}
				});
			}
			return true;
		}

		switch (answer.outcome)
		{
			case SAVE:
			case SKIP:
				QuestCombatStore.save(questKey, answer.loadout);
				loadout = answer.loadout;
				phase = answer.loadout.isSkip() ? Phase.NONE : Phase.ACTIVE;
				return false;
			default:
				stopper.accept("Combat setup cancelled for " + quest.getQuest().getName());
				phase = Phase.NONE;
				return true;
		}
	}

	private boolean wearGear(QuestCombatLoadout current)
	{
		List<String> known = QuestCombatSetupDialog.setupNames();
		if (known.stream().noneMatch(name -> name.equalsIgnoreCase(current.getGearSetup())))
		{
			Microbot.log("Quest helper: inventory setup '" + current.getGearSetup()
				+ "' not found, keeping current gear", Level.WARN);
			gearDone = true;
			return false;
		}

		Microbot.status = "Quest helper: wearing " + current.getGearSetup();
		boolean worn = new Rs2InventorySetup(current.getGearSetup(), scheduler.get()).loadEquipment();
		gearAttempts++;
		if (worn || gearAttempts >= 3)
		{
			if (!worn)
			{
				Microbot.log("Quest helper: could not load setup '" + current.getGearSetup()
					+ "', continuing with current gear", Level.WARN);
			}
			gearDone = true;
		}
		return true;
	}

	/**
	 * Before a kill step: set the magic autocast and withdraw food and potions from the bank.
	 *
	 * @param reservedSlots free slots to leave for quest items the step still needs
	 * @return true when this tick was used and the script should not act further
	 */
	public boolean prepareForKill(int reservedSlots)
	{
		QuestCombatLoadout current = loadout;
		if (phase != Phase.ACTIVE || current == null || current.isSkip() || Rs2Combat.inCombat())
		{
			return false;
		}

		if (!styleDone)
		{
			styleDone = true;
			if (current.getStyle() == QuestCombatLoadout.Style.MAGIC && !setAutocast())
			{
				Microbot.log("Quest helper: no castable combat spell found for autocast, "
					+ "set one manually", Level.WARN);
			}
		}

		if (!needsSupplies(current))
		{
			return false;
		}
		return restock(current, reservedSlots);
	}

	private boolean needsSupplies(QuestCombatLoadout current)
	{
		if (!current.wantsFood() && !current.wantsPotions())
		{
			return false;
		}
		if (!supplied)
		{
			return bankAttempts < MAX_BANK_ATTEMPTS;
		}
		boolean outOfFood = current.wantsFood() && foodCount(current) == 0;
		boolean outOfPotions = current.wantsPotions() && current.usesPrayer() && potionCount(current) == 0;
		return (outOfFood || outOfPotions) && restocks < MAX_RESTOCKS && bankAttempts < MAX_BANK_ATTEMPTS;
	}

	private boolean restock(QuestCombatLoadout current, int reservedSlots)
	{
		Microbot.status = "Quest helper: fetching combat supplies";
		if (!Rs2Bank.isOpen())
		{
			Rs2Bank.walkToBankAndUseBank();
			if (!sleepUntil(Rs2Bank::isOpen, 15_000))
			{
				bankAttempts++;
				return true;
			}
		}

		if (!Rs2Bank.setWithdrawAsItem())
		{
			Rs2Bank.closeBank();
			stopper.accept("Failed to set bank withdraw mode to Item. Toggle it manually and restart.");
			return true;
		}

		int potionNeed = 0;
		if (current.wantsPotions())
		{
			potionNeed = Math.max(0, current.getPotionQty() - potionCount(current));
		}

		if (current.wantsFood())
		{
			int held = foodCount(current);
			int target = carry(current.getFoodQty(), held, reservedSlots + potionNeed);
			withdraw(current.getFood(), true, target - held);
		}
		if (current.wantsPotions())
		{
			int held = potionCount(current);
			int target = carry(current.getPotionQty(), held, reservedSlots);
			String dosed = current.getPotion().getItemName() + "(4)";
			int missing = target - held;
			if (Rs2Bank.count(dosed, true) > 0)
			{
				withdraw(dosed, true, missing);
			}
			else
			{
				withdraw(current.getPotion().getItemName(), false, missing);
			}
		}

		boolean gotFood = !current.wantsFood() || foodCount(current) > 0;
		Rs2Bank.closeBank();
		sleepUntil(() -> !Rs2Bank.isOpen(), 3_000);

		if (supplied)
		{
			restocks++;
		}
		supplied = true;
		if (!gotFood)
		{
			stopper.accept("No " + current.getFood() + " in the bank for the combat setup. "
				+ "Stock some, or reset the combat setup and choose Skip.");
		}
		return true;
	}

	private static int carry(int wanted, int held, int reserved)
	{
		return Math.min(wanted, held + Math.max(0, Rs2Inventory.emptySlotCount() - reserved));
	}

	private void withdraw(String name, boolean exact, int amount)
	{
		int available = Rs2Bank.count(name, exact);
		int qty = Math.min(amount, available);
		if (qty <= 0)
		{
			return;
		}
		int before = Rs2Inventory.count(name, exact);
		Rs2Bank.withdrawX(name, qty, exact);
		if (!sleepUntil(() -> Rs2Inventory.count(name, exact) > before, 3_000))
		{
			Microbot.log("Quest helper: withdrawing " + name + " did not land in time", Level.WARN);
		}
	}

	private int foodCount(QuestCombatLoadout current)
	{
		return Rs2Inventory.count(current.getFood(), true);
	}

	private int potionCount(QuestCombatLoadout current)
	{
		return Rs2Inventory.count(current.getPotion().getItemName(), false);
	}

	private boolean setAutocast()
	{
		Rs2CombatSpells[] spells = Rs2CombatSpells.values();
		for (int i = spells.length - 1; i >= 0; i--)
		{
			if (Rs2Magic.canCast(spells[i].getMagicAction()))
			{
				return Rs2Combat.setAutoCastSpell(spells[i], false);
			}
		}
		return false;
	}

	/** Eat, drink and pray for the current fight. Runs on its own task, not the quest script loop. */
	public void fightTick(QuestStep activeStep)
	{
		QuestCombatLoadout current = loadout;
		if (phase != Phase.ACTIVE || current == null || current.isSkip())
		{
			return;
		}

		boolean fighting = Rs2Combat.inCombat();
		long now = System.currentTimeMillis();
		if (fighting)
		{
			lastInCombatMs = now;
		}

		if (!fighting)
		{
			if (prayerOnByUs && now - lastInCombatMs > PRAYER_OFF_DELAY_MS)
			{
				prayerOff(current);
			}
			return;
		}

		if (Rs2Player.getHealthPercentage() <= current.getEatAtPercent() && eat(current, now))
		{
			return;
		}
		if (current.usesPrayer())
		{
			managePrayer(current, now);
		}
	}

	private boolean eat(QuestCombatLoadout current, long now)
	{
		boolean hasNamed = current.wantsFood() && foodCount(current) > 0;
		boolean hasAny = !Rs2Inventory.getInventoryFood().isEmpty();
		if (!hasNamed && !hasAny)
		{
			if (current.wantsFood())
			{
				stopper.accept("Out of food at " + Math.round(Rs2Player.getHealthPercentage())
					+ "% HP during a fight. Stopping so you do not die.");
			}
			return false;
		}
		if (now - lastConsumeMs < CONSUME_COOLDOWN_MS)
		{
			return true;
		}
		lastConsumeMs = now;
		return hasNamed ? Rs2Inventory.interact(current.getFood(), "Eat", true) : Rs2Player.useFood();
	}

	private void managePrayer(QuestCombatLoadout current, long now)
	{
		if (Rs2Player.getBoostedSkillLevel(Skill.PRAYER) <= current.getDrinkAtPrayer()
			&& current.wantsPotions() && now - lastConsumeMs >= CONSUME_COOLDOWN_MS)
		{
			if (Rs2Player.drinkPrayerPotionAt(current.getDrinkAtPrayer()))
			{
				lastConsumeMs = now;
				return;
			}
		}
		if (Rs2Prayer.isOutOfPrayer())
		{
			prayerOnByUs = false;
			return;
		}

		if (current.getPrayer() == QuestCombatLoadout.Prayer.QUICK)
		{
			if (!Rs2Prayer.isQuickPrayerEnabled() && Rs2Prayer.toggleQuickPrayer(true))
			{
				prayerOnByUs = true;
			}
			return;
		}

		Rs2PrayerEnum overhead = overhead(current.getPrayer());
		if (overhead == null || Rs2Player.getRealSkillLevel(Skill.PRAYER) < overhead.getLevel())
		{
			return;
		}
		if (!Rs2Prayer.isPrayerActive(overhead) && Rs2Prayer.toggle(overhead, true))
		{
			prayerOnByUs = true;
		}
	}

	private void prayerOff(QuestCombatLoadout current)
	{
		boolean off;
		if (current.getPrayer() == QuestCombatLoadout.Prayer.QUICK)
		{
			off = Rs2Prayer.toggleQuickPrayer(false);
		}
		else
		{
			Rs2PrayerEnum overhead = overhead(current.getPrayer());
			off = overhead == null || Rs2Prayer.toggle(overhead, false);
		}
		if (off)
		{
			prayerOnByUs = false;
		}
	}

	private static Rs2PrayerEnum overhead(QuestCombatLoadout.Prayer prayer)
	{
		switch (prayer)
		{
			case PROTECT_MELEE:
				return Rs2PrayerEnum.PROTECT_MELEE;
			case PROTECT_RANGE:
				return Rs2PrayerEnum.PROTECT_RANGE;
			case PROTECT_MAGIC:
				return Rs2PrayerEnum.PROTECT_MAGIC;
			default:
				return null;
		}
	}
}
