package net.runelite.client.plugins.microbot.questhelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Pure helpers for the quest driver's dialogue and shop decisions (kept free of client state so they can be tested). */
public final class QuestDialogueRules {
	private static final Pattern TAGS = Pattern.compile("<[^>]*>");
	private static final Pattern LEADING_INDEX = Pattern.compile("^\\[\\d+\\]\\s*");
	private static final Pattern WORD = Pattern.compile("[a-z']{4,}");
	private static final Pattern BAD_OPTION = Pattern.compile(
		"^(?:no|nope|no,? thanks?(?: you)?|goodbye|bye|nothing|not really|never ?mind|maybe later|cancel)\\W*$");
	/** Declining or leaving lines: "No. I think you've had enough.", "I'd rather not.", "Not now." */
	private static final Pattern DECLINE = Pattern.compile(
		"^(?:no|nope)(?:[.,!?]|\\s|$).*|.*\\bhad enough\\b.*|^i'?d rather not.*|^not (?:now|today|yet).*|^leave me alone.*");
	/** Lines that move a conversation on: "Alright, here you go.", "Yes.", "I need your help ...". */
	private static final Pattern AFFIRM = Pattern.compile(
		"^(?:yes|yeah|yep|ok|okay|alright|all right|sure|please|accept|i need|i would|i'?d like|i want)\\b.*|.*\\bhere you go\\b.*");
	private static final Pattern BUY_TEXT = Pattern.compile("(?i)(?<![a-z])(?:buy|purchase)(?![a-z])");
	private static final Set<String> STOP_WORDS = new HashSet<>(List.of(
		"talk", "speak", "with", "that", "this", "them", "then", "from", "into", "your", "have", "about", "there",
		"where", "when", "what", "will", "need", "again", "after", "before", "near", "next", "back", "the", "and"));

	private QuestDialogueRules() {
	}

	/** Lower-case, tag-stripped, "[n]"-stripped, whitespace-collapsed text. */
	public static String normalize(String text) {
		if (text == null) {
			return "";
		}
		String plain = TAGS.matcher(text).replaceAll("").replace((char) 160, ' ');
		plain = LEADING_INDEX.matcher(plain.trim()).replaceFirst("");
		return plain.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
	}

	/** An expected previous line matches when the last seen dialogue text contains it (tag and case insensitive). */
	public static boolean previousLineMatches(String expected, String lastLine) {
		if (expected == null) {
			return true;
		}
		if (lastLine == null) {
			return false;
		}
		return normalize(lastLine).contains(normalize(expected));
	}

	/** Whether an option leaves or declines the conversation. */
	static boolean isRefusal(String option) {
		String plain = normalize(option);
		return plain.isEmpty() || BAD_OPTION.matcher(plain).matches() || DECLINE.matcher(plain).matches();
	}

	/**
	 * Picks a dialogue option when none of the step's configured choices matched. Prefers the option sharing the most
	 * words with the step text / choices; otherwise the first option that is not a refusal ("Goodbye", "No", ...).
	 *
	 * @return the chosen option's text as given, or null when there are no options
	 */
	public static String pickFallbackOption(List<String> options, List<String> hintTexts) {
		return pickFallbackOption(options, hintTexts, 0);
	}

	/**
	 * As {@link #pickFallbackOption(List, List)}, for the {@code attempt}-th try of the same menu: attempt 0 is the best
	 * option, 1 the next best, and so on round the options, so a conversation that loops without progress tries
	 * something else. Refusals are never picked.
	 */
	public static String pickFallbackOption(List<String> options, List<String> hintTexts, int attempt) {
		List<String> ranked = rankOptions(options, hintTexts);
		return ranked.isEmpty() ? null : ranked.get(Math.floorMod(attempt, ranked.size()));
	}

	/** The non-refusal options, best first: hint words weigh most, then a go-ahead wording, then the menu order. */
	public static List<String> rankOptions(List<String> options, List<String> hintTexts) {
		List<String> ranked = new ArrayList<>();
		if (options == null || options.isEmpty()) {
			return ranked;
		}
		Set<String> hints = new HashSet<>();
		if (hintTexts != null) {
			for (String hint : hintTexts) {
				var matcher = WORD.matcher(normalize(hint));
				while (matcher.find()) {
					if (!STOP_WORDS.contains(matcher.group())) {
						hints.add(matcher.group());
					}
				}
			}
		}
		List<Integer> scores = new ArrayList<>();
		for (String option : options) {
			if (option == null || isRefusal(option)) {
				continue;
			}
			String plain = normalize(option);
			int score = AFFIRM.matcher(plain).matches() ? 1 : 0;
			for (String hint : hints) {
				if (plain.contains(hint)) {
					score += 3;
				}
			}
			int at = 0;
			while (at < ranked.size() && scores.get(at) >= score) {
				at++;
			}
			ranked.add(at, option);
			scores.add(at, score);
		}
		return ranked;
	}

	/** Step text asks to buy something. */
	public static boolean isBuyText(List<String> stepText) {
		if (stepText == null) {
			return false;
		}
		for (String line : stepText) {
			if (line != null && BUY_TEXT.matcher(TAGS.matcher(line).replaceAll("")).find()) {
				return true;
			}
		}
		return false;
	}

	/** The NPC's "Trade" style action when this is a shop-purchase step, else null. */
	public static String preferTradeAction(boolean shopStep, String[] npcActions) {
		if (!shopStep || npcActions == null) {
			return null;
		}
		for (String action : npcActions) {
			if (action != null && action.toLowerCase(Locale.ROOT).startsWith("trade")) {
				return action;
			}
		}
		return null;
	}

	/** Splits a quantity into the shop's fixed buy amounts, largest first. */
	public static List<Integer> shopBuyChunks(int quantity) {
		List<Integer> chunks = new ArrayList<>();
		int remaining = quantity;
		for (int allowed : new int[]{50, 10, 5, 1}) {
			while (remaining >= allowed) {
				chunks.add(allowed);
				remaining -= allowed;
			}
		}
		return chunks;
	}
}
