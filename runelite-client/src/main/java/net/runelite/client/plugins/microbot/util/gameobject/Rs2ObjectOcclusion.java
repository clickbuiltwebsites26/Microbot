package net.runelite.client.plugins.microbot.util.gameobject;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.ObjectComposition;
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
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Area;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
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
 * <p>Occlusion rule: a named {@link GameObject} within {@link #SEARCH_RADIUS_TILES} tiles whose clickbox
 * overlaps the target's and that stands nearer the camera is drawn in front of it, so its clickbox is cut
 * out of the target's. If nothing is left, the camera is turned, and if that does not help the player steps
 * to a tile that sees the target from another side.
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

	private Rs2ObjectOcclusion() {
	}

	/** What a client-thread look at the screen found. */
	private static final class Probe {
		/** Where to click; null when the object is covered. */
		final Rectangle area;
		/** Scene-tile offsets from the target to each object covering it. */
		final List<Point> occluderOffsets;

		Probe(Rectangle area, List<Point> occluderOffsets) {
			this.area = area;
			this.occluderOffsets = occluderOffsets;
		}

		boolean covered() {
			return area == null;
		}
	}

	/**
	 * The rectangle to click for {@code object}: its normal clickbox bounds when nothing covers it, a small
	 * rectangle on its visible part when something does, or null when it stays covered after turning the
	 * camera and moving. Call from a script thread.
	 */
	public static Rectangle findClickArea(TileObject object, WorldPoint objectLocation) {
		Probe probe = probe(object);
		if (!probe.covered()) {
			return probe.area;
		}
		log.info("Object {} is covered by {} nearer object(s), turning the camera", object.getId(), probe.occluderOffsets.size());
		int startYaw = Rs2Camera.getYaw();
		for (int step : YAW_STEPS) {
			turnCameraTo(Math.floorMod(startYaw + step, FULL_TURN));
			probe = probe(object);
			if (!probe.covered()) {
				log.info("Object {} is clear after turning the camera {} units", object.getId(), step);
				return probe.area;
			}
		}

		WorldPoint spot = clearViewTile(objectLocation, probe.occluderOffsets);
		if (spot == null) {
			log.warn("Object {} is still covered and no reachable tile within {} tiles sees it from another side", object.getId(), CLEAR_VIEW_SEARCH_TILES);
			return null;
		}
		log.info("Object {} is still covered after turning the camera, walking to {}", object.getId(), spot);
		if (!Rs2Walker.walkTo(spot, 0)) {
			log.warn("Could not walk to {} for a clear view of object {}", spot, object.getId());
			return null;
		}
		Rs2Camera.turnTo(object);
		probe = probe(object);
		if (probe.covered()) {
			log.warn("Object {} is still covered from {}", object.getId(), spot);
			return null;
		}
		log.info("Object {} is clear from {}", object.getId(), spot);
		return probe.area;
	}

	/** The rectangle to click for {@code object} right now, without turning the camera or moving; null when it is covered. */
	public static Rectangle visibleClickArea(TileObject object) {
		return probe(object).area;
	}

	private static Probe probe(TileObject object) {
		Probe probe = Microbot.getClientThread().invoke(() -> probeOnClientThread(object));
		return probe != null ? probe : new Probe(Rs2UiHelper.getObjectClickbox(object), Collections.emptyList());
	}

	private static Probe probeOnClientThread(TileObject object) {
		Client client = Microbot.getClient();
		WorldView worldView = object.getWorldView();
		Shape clickbox = object.getClickbox();
		LocalPoint local = object.getLocalLocation();
		// boats and other worldviews have their own camera space, so their clicks stay as they were
		if (worldView == null || clickbox == null || local == null || worldView.getId() != client.getTopLevelWorldView().getId()) {
			return new Probe(Rs2UiHelper.getObjectClickbox(object), Collections.emptyList());
		}

		Area onScreen = new Area(clickbox);
		onScreen.intersect(new Area(new Rectangle(client.getViewportXOffset(), client.getViewportYOffset(),
				client.getViewportWidth(), client.getViewportHeight())));
		List<Point> offsets = new ArrayList<>();
		List<Shape> occluders = nearerClickboxes(client, worldView, object, local, onScreen.getBounds(), offsets);
		if (occluders.isEmpty() || !VisibleClickPoint.isOccluded(onScreen, occluders)) {
			return new Probe(Rs2UiHelper.getObjectClickbox(object), Collections.emptyList());
		}
		Optional<Point> point = VisibleClickPoint.pick(onScreen, occluders, ThreadLocalRandom.current());
		Rectangle area = point.map(p -> new Rectangle(p.x - VisibleClickPoint.MARGIN, p.y - VisibleClickPoint.MARGIN,
				2 * VisibleClickPoint.MARGIN + 1, 2 * VisibleClickPoint.MARGIN + 1)).orElse(null);
		return new Probe(area, offsets);
	}

	/** Clickboxes of the named game objects near {@code target} that overlap {@code bounds} and stand nearer the camera. */
	private static List<Shape> nearerClickboxes(Client client, WorldView worldView, TileObject target, LocalPoint targetLocal,
												Rectangle bounds, List<Point> offsets) {
		List<Shape> occluders = new ArrayList<>();
		Scene scene = worldView.getScene();
		Tile[][] plane = scene == null ? null : scene.getTiles()[target.getPlane()];
		if (plane == null || bounds.isEmpty()) {
			return occluders;
		}
		long targetDistance = squaredDistanceToCamera(client, targetLocal);
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
					if (other == null || other == target || !seen.add(other)) {
						continue;
					}
					LocalPoint otherLocal = other.getLocalLocation();
					Shape otherBox = other.getClickbox();
					if (otherLocal == null || otherBox == null || !otherBox.getBounds().intersects(bounds)
							|| squaredDistanceToCamera(client, otherLocal) >= targetDistance || !isClickable(client, other)) {
						continue;
					}
					occluders.add(otherBox);
					offsets.add(new Point(otherLocal.getSceneX() - targetX, otherLocal.getSceneY() - targetY));
				}
			}
		}
		return occluders;
	}

	/** Objects without a name draw but offer no menu entry, so they cannot take a click away from the target. */
	private static boolean isClickable(Client client, GameObject object) {
		ObjectComposition composition = client.getObjectDefinition(object.getId());
		if (composition != null && composition.getImpostorIds() != null) {
			composition = composition.getImpostor();
		}
		return composition != null && composition.getName() != null && !composition.getName().isEmpty() && !"null".equals(composition.getName());
	}

	private static long squaredDistanceToCamera(Client client, LocalPoint point) {
		long dx = point.getX() - client.getCameraX();
		long dy = point.getY() - client.getCameraY();
		return dx * dx + dy * dy;
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

	/**
	 * The nearest reachable tile that looks at the target from at least a right angle away from every
	 * covering object, so none of them stands between that tile and the target.
	 */
	private static WorldPoint clearViewTile(WorldPoint target, List<Point> occluderOffsets) {
		WorldPoint player = Rs2Player.getWorldLocation();
		if (target == null || player == null) {
			return null;
		}
		WorldPoint best = null;
		int bestDistance = Integer.MAX_VALUE;
		for (Map.Entry<WorldPoint, Integer> reachable : Rs2Tile.getReachableTilesFromTile(player, CLEAR_VIEW_SEARCH_TILES).entrySet()) {
			WorldPoint tile = reachable.getKey();
			int dx = tile.getX() - target.getX();
			int dy = tile.getY() - target.getY();
			int away = Math.max(Math.abs(dx), Math.abs(dy));
			if (tile.getPlane() != target.getPlane() || away == 0 || away > CLEAR_VIEW_MAX_TARGET_DISTANCE
					|| reachable.getValue() == 0 || reachable.getValue() >= bestDistance) {
				continue;
			}
			boolean clear = true;
			for (Point occluder : occluderOffsets) {
				// an occluder on the target's own tile sits on every line of sight, so it rules nothing out
				if ((occluder.x != 0 || occluder.y != 0) && dx * occluder.x + dy * occluder.y > 0) {
					clear = false;
					break;
				}
			}
			if (clear) {
				best = tile;
				bestDistance = reachable.getValue();
			}
		}
		return best;
	}
}
