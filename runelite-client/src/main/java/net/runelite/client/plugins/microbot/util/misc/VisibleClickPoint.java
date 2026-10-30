package net.runelite.client.plugins.microbot.util.misc;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Area;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Picks a click point inside the part of a target's clickbox that no nearer clickbox covers.
 * Pure geometry: no client access, so it can be tested with plain {@link java.awt} shapes.
 */
public final class VisibleClickPoint {

	/** A visible sliver thinner than this (px) is not worth clicking: a player could not hit it. */
	static final int MIN_VISIBLE_SIZE = 6;
	/** Clear pixels kept on every side of the chosen point, so click jitter stays on the target. */
	public static final int MARGIN = 1;

	private static final int RANDOM_TRIES = 48;
	private static final int SCAN_STEP = 2;

	private VisibleClickPoint() {
	}

	/** The part of {@code target} that none of the {@code occluders} covers. */
	public static Area visibleArea(Shape target, Collection<? extends Shape> occluders) {
		Area visible = new Area(target);
		for (Shape occluder : occluders) {
			visible.subtract(new Area(occluder));
		}
		return visible;
	}

	/** True when at least one occluder actually covers part of the target. */
	public static boolean isOccluded(Shape target, Collection<? extends Shape> occluders) {
		return !visibleArea(target, occluders).equals(new Area(target));
	}

	/** True when the visible part is empty or too thin to click. */
	public static boolean isTooSmall(Area visible) {
		if (visible.isEmpty()) {
			return true;
		}
		Rectangle bounds = visible.getBounds();
		return bounds.width < MIN_VISIBLE_SIZE || bounds.height < MIN_VISIBLE_SIZE;
	}

	/**
	 * A random point inside the visible part of {@code target}, or empty when that part is too small to click.
	 * The point has {@link #MARGIN} px of visible area around it.
	 */
	public static Optional<Point> pick(Shape target, Collection<? extends Shape> occluders, Random random) {
		Area visible = visibleArea(target, occluders);
		if (isTooSmall(visible)) {
			return Optional.empty();
		}
		Rectangle bounds = visible.getBounds();
		for (int i = 0; i < RANDOM_TRIES; i++) {
			Point candidate = new Point(bounds.x + random.nextInt(bounds.width), bounds.y + random.nextInt(bounds.height));
			if (hasClearance(visible, candidate)) {
				return Optional.of(candidate);
			}
		}
		// an odd-shaped remainder can make random hits rare, so scan it instead
		List<Point> clear = new ArrayList<>();
		for (int y = bounds.y; y < bounds.y + bounds.height; y += SCAN_STEP) {
			for (int x = bounds.x; x < bounds.x + bounds.width; x += SCAN_STEP) {
				Point candidate = new Point(x, y);
				if (hasClearance(visible, candidate)) {
					clear.add(candidate);
				}
			}
		}
		return clear.isEmpty() ? Optional.empty() : Optional.of(clear.get(random.nextInt(clear.size())));
	}

	private static boolean hasClearance(Area visible, Point point) {
		return visible.contains(point.x - MARGIN, point.y - MARGIN, 2 * MARGIN + 1, 2 * MARGIN + 1);
	}
}
