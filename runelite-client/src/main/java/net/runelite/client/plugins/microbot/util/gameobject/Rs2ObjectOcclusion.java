package net.runelite.client.plugins.microbot.util.gameobject;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.misc.VisibleClickPoint;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * Finds where an object can really be clicked. A real player cannot click through a tree that stands in
 * front of the target, but a menu entry injected by the bot goes through anyway, so the click point has
 * to be chosen where the target is visible and on top.
 *
 * <p>Occlusion rule: a {@link GameObject} within {@link #SEARCH_RADIUS_TILES} tiles that offers an action,
 * stands beside (not on) the target's footprint, has its nearest edge closer to the camera and whose clickbox
 * overlaps the target's is drawn in front of it, so its clickbox is cut out of the target's. If nothing is
 * left, the camera is turned; if the object stays covered the click is refused and a caller that may move the
 * player can ask {@link #findClearViewTile} where to stand.
 */
@Slf4j
public final class Rs2ObjectOcclusion {

	static final int SEARCH_RADIUS_TILES = 4;
	/** Camera turns tried in order, relative to the yaw at the start (2048 units = 360 degrees). */
	private static final int[] YAW_STEPS = {300, -300, 700};
	private static final int FULL_TURN = 2048;
	private static final int YAW_SETTLED_UNITS = 16;
	private static final int YAW_SETTLE_TIMEOUT_MS = 2000;
	private static final int CLEAR_VIEW_SEARCH_TILES = 5;
	private static final int CLEAR_VIEW_MAX_TARGET_DISTANCE = 7;
	private static final long COVERED_REPORT_MAX_AGE_MS = 15_000;

	/** The last object a click refused because it stayed covered. */
	private static volatile CoverReport lastCovered;

	private Rs2ObjectOcclusion() {
	}

	/** What a client-thread look at the screen found. */
	private static final class Scan {
		final Area onScreen;
		final List<Shape> occluders;
		/** Centres of the occluders relative to the target centre, in tiles. */
		final List<Point2D.Double> offsets;
		/** The target centre in world tiles. */
		final Point2D.Double targetCentre;
		final int plane;

		Scan(Area onScreen, List<Shape> occluders, List<Point2D.Double> offsets, Point2D.Double targetCentre, int plane) {
			this.onScreen = onScreen;
			this.occluders = occluders;
			this.offsets = offsets;
			this.targetCentre = targetCentre;
			this.plane = plane;
		}
	}

	private static final class CoverReport {
		final TileObject object;
		final Set<Point2D.Double> offsets;
		final Point2D.Double targetCentre;
		final int plane;
		final long at = System.currentTimeMillis();

		CoverReport(TileObject object, Set<Point2D.Double> offsets, Point2D.Double targetCentre, int plane) {
			this.object = object;
			this.offsets = offsets;
			this.targetCentre = targetCentre;
			this.plane = plane;
		}
	}

	/**
	 * The rectangle to click for {@code object}: its normal clickbox bounds when nothing covers it, a small
	 * rectangle on its visible part when something does, or null when it stays covered after turning the
	 * camera. Call from a script thread.
	 */
	public static Rectangle findClickArea(TileObject object) {
		Scan scan = scan(object);
		Rectangle area = clickAreaOf(object, scan);
		if (area != null) {
			return area;
		}
		log.info("Object {} is covered by {} nearer object(s), turning the camera", object.getId(), scan.occluders.size());
		Set<Point2D.Double> offsets = new LinkedHashSet<>(scan.offsets);
		int startYaw = Rs2Camera.getYaw();
		for (int step : YAW_STEPS) {
			turnCameraTo(Math.floorMod(startYaw + step, FULL_TURN));
			Scan turned = scan(object);
			area = clickAreaOf(object, turned);
			if (area != null) {
				log.info("Object {} is clear after turning the camera {} units", object.getId(), step);
				return area;
			}
			offsets.addAll(turned.offsets);
		}
		log.warn("Object {} is still covered after turning the camera", object.getId());
		lastCovered = new CoverReport(object, offsets, scan.targetCentre, scan.plane);
		return null;
	}

	/** The rectangle to click for {@code object} right now, without turning the camera; null when it is covered. */
	public static Rectangle visibleClickArea(TileObject object) {
		return clickAreaOf(object, scan(object));
	}

	/** Whether a click at {@code point} lands on a visible part of {@code object}. */
	public static boolean visibleAt(TileObject object, java.awt.Point point) {
		Scan scan = scan(object);
		if (scan.occluders.isEmpty()) {
			Rectangle plain = Rs2UiHelper.getObjectClickbox(object);
			Rectangle visible = Rs2UiHelper.isFallbackRectangle(plain) ? null : Rs2UiHelper.clipToCanvas(plain);
			return visible == null || visible.contains(point);
		}
		return VisibleClickPoint.isClickable(VisibleClickPoint.visibleArea(scan.onScreen, scan.occluders), point);
	}

	/**
	 * The nearest reachable tile, within a few tiles of the player, that sees {@code object} from at least a
	 * right angle away from everything that covered it, or null when {@code object} was not recently refused as
	 * covered or no such tile exists. Walking there is up to the caller.
	 */
	public static WorldPoint findClearViewTile(TileObject object) {
		CoverReport report = lastCovered;
		WorldPoint player = Rs2Player.getWorldLocation();
		if (report == null || report.object != object || player == null
				|| System.currentTimeMillis() - report.at > COVERED_REPORT_MAX_AGE_MS) {
			return null;
		}
		WorldPoint best = null;
		int bestDistance = Integer.MAX_VALUE;
		for (Map.Entry<WorldPoint, Integer> reachable : Rs2Tile.getReachableTilesFromTile(player, CLEAR_VIEW_SEARCH_TILES).entrySet()) {
			WorldPoint tile = reachable.getKey();
			Point2D.Double tileCentre = new Point2D.Double(tile.getX() + 0.5, tile.getY() + 0.5);
			double away = Math.max(Math.abs(tileCentre.x - report.targetCentre.x), Math.abs(tileCentre.y - report.targetCentre.y));
			if (tile.getPlane() == report.plane && away >= 1 && away <= CLEAR_VIEW_MAX_TARGET_DISTANCE
					&& reachable.getValue() > 0 && reachable.getValue() < bestDistance
					&& VisibleClickPoint.looksPastAll(tileCentre, report.targetCentre, report.offsets)) {
				best = tile;
				bestDistance = reachable.getValue();
			}
		}
		return best;
	}

	/** The click rectangle for what {@code scan} saw: the plain clickbox, a rectangle on the visible part, or null if covered. */
	private static Rectangle clickAreaOf(TileObject object, Scan scan) {
		if (scan.occluders.isEmpty()) {
			return Rs2UiHelper.getObjectClickbox(object);
		}
		Optional<Point> point = VisibleClickPoint.pick(scan.onScreen, scan.occluders, ThreadLocalRandom.current());
		return point.map(p -> new Rectangle(p.x - VisibleClickPoint.MARGIN, p.y - VisibleClickPoint.MARGIN,
				2 * VisibleClickPoint.MARGIN + 1, 2 * VisibleClickPoint.MARGIN + 1)).orElse(null);
	}

	private static Scan scan(TileObject object) {
		Scan scan = Microbot.getClientThread().runOnClientThreadOptional(() -> scanOnClientThread(object)).orElse(null);
		return scan != null ? scan : uncovered();
	}

	private static Scan uncovered() {
		return new Scan(new Area(), Collections.emptyList(), Collections.emptyList(), new Point2D.Double(), 0);
	}

	private static Scan scanOnClientThread(TileObject object) {
		Client client = Microbot.getClient();
		WorldView worldView = object.getWorldView();
		Shape clickbox = object.getClickbox();
		LocalPoint local = object.getLocalLocation();
		WorldPoint world = object.getWorldLocation();
		// boats and other worldviews have their own camera space, so their clicks stay as they were
		if (worldView == null || clickbox == null || local == null || world == null
				|| worldView.getId() != client.getTopLevelWorldView().getId()) {
			return uncovered();
		}

		Rectangle bounds = clickbox.getBounds().intersection(new Rectangle(client.getViewportXOffset(), client.getViewportYOffset(),
				client.getViewportWidth(), client.getViewportHeight()));
		List<Shape> occluders = new ArrayList<>();
		List<Point2D.Double> offsets = new ArrayList<>();
		double centreX = local.getX() / (double) Perspective.LOCAL_TILE_SIZE;
		double centreY = local.getY() / (double) Perspective.LOCAL_TILE_SIZE;
		if (!bounds.isEmpty()) {
			collectNearerClickboxes(client, worldView, object, local, bounds, occluders, offsets);
		}
		if (occluders.isEmpty()) {
			return uncovered();
		}
		Area onScreen = new Area(clickbox);
		onScreen.intersect(new Area(bounds));
		// local coordinates are scene tiles times the tile size, so this puts the centre in world tiles
		Point2D.Double targetCentre = new Point2D.Double(world.getX() - local.getSceneX() + centreX, world.getY() - local.getSceneY() + centreY);
		return new Scan(onScreen, occluders, offsets, targetCentre, world.getPlane());
	}

	/** Clickboxes of the named, actionable game objects near {@code target} that stand in front of it and overlap {@code bounds}. */
	private static void collectNearerClickboxes(Client client, WorldView worldView, TileObject target, LocalPoint targetLocal,
												Rectangle bounds, List<Shape> occluders, List<Point2D.Double> offsets) {
		Scene scene = worldView.getScene();
		Tile[][] plane = scene == null ? null : scene.getTiles()[target.getPlane()];
		if (plane == null) {
			return;
		}
		Rectangle2D targetFootprint = footprint(target, targetLocal);
		int targetX = targetLocal.getSceneX();
		int targetY = targetLocal.getSceneY();
		Set<GameObject> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		for (int x = Math.max(0, targetX - SEARCH_RADIUS_TILES); x <= Math.min(plane.length - 1, targetX + SEARCH_RADIUS_TILES); x++) {
			for (int y = Math.max(0, targetY - SEARCH_RADIUS_TILES); y <= Math.min(plane[x].length - 1, targetY + SEARCH_RADIUS_TILES); y++) {
				Tile tile = plane[x][y];
				if (tile == null) {
					continue;
				}
				for (GameObject other : tile.getGameObjects()) {
					// cheap checks first; the clickbox is only built for what survives them
					if (other == null || other == target || !seen.add(other)) {
						continue;
					}
					LocalPoint otherLocal = other.getLocalLocation();
					if (otherLocal == null
							|| !VisibleClickPoint.isInFront(footprint(other, otherLocal), targetFootprint, client.getCameraX(), client.getCameraY())
							|| !offersAction(client, other)) {
						continue;
					}
					Shape otherBox = other.getClickbox();
					if (otherBox == null || !otherBox.getBounds().intersects(bounds)) {
						continue;
					}
					occluders.add(otherBox);
					offsets.add(new Point2D.Double((otherLocal.getX() - targetLocal.getX()) / (double) Perspective.LOCAL_TILE_SIZE,
							(otherLocal.getY() - targetLocal.getY()) / (double) Perspective.LOCAL_TILE_SIZE));
				}
			}
		}
	}

	/** The tiles an object stands on, in local units. */
	private static Rectangle2D footprint(TileObject object, LocalPoint local) {
		int tile = Perspective.LOCAL_TILE_SIZE;
		if (object instanceof GameObject) {
			GameObject gameObject = (GameObject) object;
			var min = gameObject.getSceneMinLocation();
			return new Rectangle2D.Double(min.getX() * tile, min.getY() * tile, gameObject.sizeX() * tile, gameObject.sizeY() * tile);
		}
		return new Rectangle2D.Double(local.getSceneX() * tile, local.getSceneY() * tile, tile, tile);
	}

	/** Scenery with no action other than Examine offers nothing a click could take away from the target. */
	private static boolean offersAction(Client client, GameObject object) {
		ObjectComposition composition = client.getObjectDefinition(object.getId());
		if (composition != null && composition.getImpostorIds() != null) {
			composition = composition.getImpostor();
		}
		String[] actions = composition == null ? null : composition.getActions();
		if (actions == null) {
			return false;
		}
		for (String action : actions) {
			if (action != null && !action.equalsIgnoreCase("Examine")) {
				return true;
			}
		}
		return false;
	}

	private static void turnCameraTo(int yaw) {
		Rs2Camera.setYaw(yaw);
		boolean settled = sleepUntil(() -> {
			int gap = Math.abs(Rs2Camera.getYaw() - yaw);
			return Math.min(gap, FULL_TURN - gap) <= YAW_SETTLED_UNITS;
		}, YAW_SETTLE_TIMEOUT_MS);
		if (!settled) {
			log.warn("Camera did not settle on yaw {} within {} ms", yaw, YAW_SETTLE_TIMEOUT_MS);
		}
	}
}
