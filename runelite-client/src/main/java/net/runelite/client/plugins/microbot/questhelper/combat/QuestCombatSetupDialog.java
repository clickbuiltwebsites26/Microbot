package net.runelite.client.plugins.microbot.questhelper.combat;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.inventorysetups.MInventorySetupsPlugin;

/** Asks which gear, food, potions, prayer and style to use for a quest's fights. Swing, EDT only. */
public final class QuestCombatSetupDialog
{
	public static final String WEARING_NOW = "Use what I'm wearing now";

	public enum Outcome
	{
		SAVE, SKIP, CANCEL
	}

	public static final class Result
	{
		public final Outcome outcome;
		public final QuestCombatLoadout loadout;

		Result(Outcome outcome, QuestCombatLoadout loadout)
		{
			this.outcome = outcome;
			this.loadout = loadout;
		}
	}

	private QuestCombatSetupDialog()
	{
	}

	/** Names of the user's Inventory Setups; safe when the setups plugin has not loaded any. */
	public static List<String> setupNames()
	{
		List<String> names = new ArrayList<>();
		List<InventorySetup> setups = MInventorySetupsPlugin.getInventorySetups();
		if (setups != null)
		{
			for (InventorySetup setup : new ArrayList<>(setups))
			{
				if (setup != null && setup.getName() != null)
				{
					names.add(setup.getName());
				}
			}
		}
		return names;
	}

	/** Shows the dialog on the EDT and hands the answer to {@code callback}; never blocks the caller. */
	public static void showAsync(String questName, List<String> combatRequirements, Consumer<Result> callback)
	{
		SwingUtilities.invokeLater(() ->
		{
			Result result = new Result(Outcome.CANCEL, null);
			try
			{
				result = show(questName, combatRequirements);
			}
			finally
			{
				callback.accept(result);
			}
		});
	}

	private static Result show(String questName, List<String> combatRequirements)
	{
		JComboBox<String> gear = new JComboBox<>();
		gear.addItem(WEARING_NOW);
		setupNames().forEach(gear::addItem);

		JTextField food = new JTextField("Lobster", 14);
		JSpinner foodQty = new JSpinner(new SpinnerNumberModel(10, 0, 27, 1));
		JSpinner eatAt = new JSpinner(new SpinnerNumberModel(50, 1, 99, 5));
		JComboBox<QuestCombatLoadout.Potion> potion = new JComboBox<>(QuestCombatLoadout.Potion.values());
		JSpinner potionQty = new JSpinner(new SpinnerNumberModel(2, 0, 27, 1));
		JComboBox<QuestCombatLoadout.Prayer> prayer = new JComboBox<>(QuestCombatLoadout.Prayer.values());
		JSpinner drinkAt = new JSpinner(new SpinnerNumberModel(20, 1, 98, 1));
		JComboBox<QuestCombatLoadout.Style> style = new JComboBox<>(QuestCombatLoadout.Style.values());

		String notes = combatRequirements == null || combatRequirements.isEmpty()
			? "This quest lists no combat notes."
			: String.join("\n", combatRequirements);
		JTextArea notesArea = new JTextArea(notes, 4, 34);
		notesArea.setEditable(false);
		notesArea.setLineWrap(true);
		notesArea.setWrapStyleWord(true);

		JPanel panel = new JPanel(new GridBagLayout());
		int row = 0;
		row = addRow(panel, row, "Quest combat notes", new JScrollPane(notesArea));
		row = addRow(panel, row, "Gear (Inventory Setup)", gear);
		row = addRow(panel, row, "Food", food);
		row = addRow(panel, row, "Food quantity", foodQty);
		row = addRow(panel, row, "Eat at HP %", eatAt);
		row = addRow(panel, row, "Potions", potion);
		row = addRow(panel, row, "Potion quantity", potionQty);
		row = addRow(panel, row, "Prayer", prayer);
		row = addRow(panel, row, "Drink at prayer points", drinkAt);
		addRow(panel, row, "Combat style", style);

		Object[] options = {"Save & start", "Skip", "Cancel"};
		while (true)
		{
			int choice = JOptionPane.showOptionDialog(
				null,
				panel,
				questName + " - Combat setup",
				JOptionPane.DEFAULT_OPTION,
				JOptionPane.QUESTION_MESSAGE,
				null,
				options,
				options[0]);

			if (choice == 1)
			{
				return new Result(Outcome.SKIP, QuestCombatLoadout.skipped());
			}
			if (choice != 0)
			{
				return new Result(Outcome.CANCEL, null);
			}

			if (style.getSelectedItem() == QuestCombatLoadout.Style.MAGIC
				&& QuestCombatLoadout.mentionsMagicImmunity(combatRequirements))
			{
				int proceed = JOptionPane.showConfirmDialog(
					null,
					"The quest notes say magic does not work against these fights.\nUse Magic anyway?",
					"Magic may not work",
					JOptionPane.YES_NO_OPTION,
					JOptionPane.WARNING_MESSAGE);
				if (proceed != JOptionPane.YES_OPTION)
				{
					continue;
				}
			}

			QuestCombatLoadout loadout = new QuestCombatLoadout();
			String selectedGear = Objects.toString(gear.getSelectedItem(), WEARING_NOW);
			loadout.setGearSetup(WEARING_NOW.equals(selectedGear) ? "" : selectedGear);
			loadout.setFood(food.getText());
			loadout.setFoodQty((Integer) foodQty.getValue());
			loadout.setEatAtPercent((Integer) eatAt.getValue());
			loadout.setPotion((QuestCombatLoadout.Potion) potion.getSelectedItem());
			loadout.setPotionQty((Integer) potionQty.getValue());
			loadout.setPrayer((QuestCombatLoadout.Prayer) prayer.getSelectedItem());
			loadout.setDrinkAtPrayer((Integer) drinkAt.getValue());
			loadout.setStyle((QuestCombatLoadout.Style) style.getSelectedItem());
			return new Result(Outcome.SAVE, loadout.normalised());
		}
	}

	private static int addRow(JPanel panel, int row, String label, java.awt.Component field)
	{
		GridBagConstraints left = new GridBagConstraints();
		left.gridx = 0;
		left.gridy = row;
		left.anchor = GridBagConstraints.WEST;
		left.insets = new Insets(2, 2, 2, 8);
		panel.add(new JLabel(label), left);

		GridBagConstraints right = new GridBagConstraints();
		right.gridx = 1;
		right.gridy = row;
		right.fill = GridBagConstraints.HORIZONTAL;
		right.weightx = 1;
		right.insets = new Insets(2, 2, 2, 2);
		panel.add(field, right);
		return row + 1;
	}
}
