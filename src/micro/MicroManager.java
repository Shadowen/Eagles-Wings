package micro;

import java.awt.Point;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import base.Base;
import base.BaseManager;
import base.Worker;
import bwapi.Color;
import bwapi.Position;
import bwapi.PositionOrUnit;
import bwapi.Unit;
import bwapi.UnitType;
import bwta.BWTA;
import gamestructure.GameHandler;
import gamestructure.debug.DebugManager;
import gamestructure.debug.DrawEngine;
import pathing.NoPathFoundException;
import pathing.PathFinder;

public final class MicroManager {
	/** The width of the map in build tiles */
	private static int mapWidth;
	/** The height of the map in build tiles */
	private static int mapHeight;
	public static double[][] targetMap;
	public static double[][] threatMap;

	private static Map<UnitType, Set<UnitAgent>> unitsByType;
	private static Map<Unit, UnitAgent> unitAgents;

	public static void init() {
		System.out.print("Starting MicroManager... ");
		mapWidth = GameHandler.getMapWidth();
		mapHeight = GameHandler.getMapHeight();
		targetMap = new double[mapWidth + 1][mapHeight + 1];
		threatMap = new double[mapWidth + 1][mapHeight + 1];

		unitAgents = new HashMap<Unit, UnitAgent>();
		unitsByType = new HashMap<UnitType, Set<UnitAgent>>();

		registerDebugFunctions();
		System.out.println("Success!");
	}

	/** This constructor should never be called. */
	private MicroManager() {
	}

	public static void onFrame() {
		updateMap();
		// Move units
		for (UnitAgent ua : unitAgents.values()) {
			ua.act();
		}
	}

	private static void updateMap() {
		// Update threat map
		// Reset target and threat counter
		for (int x = 0; x < mapWidth; x++) {
			for (int y = 0; y < mapHeight; y++) {
				targetMap[x][y] = 0;
				threatMap[x][y] = 0;
			}
		}

		// Loop through enemy units
		for (Unit u : GameHandler.getEnemyUnits()) {
			UnitType unitType = u.getType();
			// Get the x and y grid point coordinates
			int x = u.getX() / 32;
			int y = u.getY() / 32;

			// Update target map
			double targetValue = 0;
			if (unitType.isWorker()) {
				targetValue = 1;
			}
			/** Attack range in build tiles */
			int radius = 10;
			int startX = Math.max(x - radius, 0);
			int endX = Math.min(x + radius, mapWidth);
			for (int cx = startX; cx < endX; cx++) {
				int remainingRadius = radius - Math.abs(cx - x);
				int startY = Math.max(y - remainingRadius, 0);
				int endY = Math.min(y + remainingRadius, mapHeight);
				for (int cy = startY; cy < endY; cy++) {
					targetMap[cx][cy] += targetValue / (Point.distance(x, y, cx, cy) + 1);
				}
			}

			// Update threat map
			double threatValue = 20;
			radius = u.getType().airWeapon().maxRange() / 32 + 10;
			startX = Math.max(x - radius, 0);
			endX = Math.min(x + radius, mapWidth);
			for (int cx = startX; cx < endX; cx++) {
				int remainingRadius = radius - Math.abs(cx - x);
				int startY = Math.max(y - remainingRadius, 0);
				int endY = Math.min(y + remainingRadius, mapHeight);
				for (int cy = startY; cy < endY; cy++) {
					threatMap[cx][cy] += threatValue * Math.max(1 - Point.distance(x, y, cx, cy) / radius, 0);
				}
			}
		}
	}

	public static Set<UnitAgent> getUnitsByType(UnitType type) {
		return unitsByType.getOrDefault(type, Collections.emptySet());
	}

	public static UnitAgent getAgentForUnit(Unit u) {
		return unitAgents.get(u);
	}

	public static Position getScoutingTarget(Unit requestor) {
		Base target = null;
		for (Base b : BaseManager.getBases()) {
			if (b.getLocation().isStartLocation() && b.getPlayer() == GameHandler.getNeutralPlayer()
					&& (target == null || b.getLastScouted() < target.getLastScouted())) {
				if (!requestor.getType().isFlyer()
						&& !BWTA.isConnected(requestor.getTilePosition(), b.getLocation().getTilePosition())) {
					System.out.println("Pruned a scouting target due to unreachability");
					continue;
				}
				target = b;
			}
		}
		if (target == null) {
			for (Base b : BaseManager.getBases()) {
				if (target == null || b.getLastScouted() < target.getLastScouted()) {
					if (!requestor.getType().isFlyer()
							&& !BWTA.isConnected(requestor.getTilePosition(), b.getLocation().getTilePosition())) {
						System.out.println("Pruned a scouting target due to unreachability");
						continue;
					}
					target = b;
				}
			}
		}
		if (target != null) {
			return target.getLocation().getPosition();
		}
		return null;
	}

	public static void unitCreated(Unit unit) throws UnrecognizedUnitTypeException {
		if (!unitAgents.containsKey(unit)) {
			final UnitType type = unit.getType();
			UnitAgent ua;
			// TODO is there a way to clean up this if statement?
			if (type.isWorker()) {
				ua = new Worker(unit);
			} else if (type == UnitType.Terran_Marine || type == UnitType.Terran_Vulture) {
				ua = new RangedAgent(unit);
			} else if (type == UnitType.Terran_Wraith) {
				ua = new WraithAgent(unit);
			} else {
				throw new UnrecognizedUnitTypeException(unit);
			}
			unitsByType.putIfAbsent(type, new HashSet<UnitAgent>());
			unitsByType.get(type).add(ua);
			unitAgents.put(unit, ua);
		} else {
			System.err.println("Duplicated unit found!");
		}
	}

	public static void unitDestroyed(Unit unit) {
		UnitAgent ua = unitAgents.remove(unit);
		if (ua != null) {
			Set<UnitAgent> typeSet = unitsByType.get(unit.getType());
			if (typeSet != null) {
				typeSet.remove(ua);
			}
		}
	}

	public static void registerDebugFunctions() {
		// Static D
		DebugManager.createDebugModule("staticd").setDraw(() -> {
			for (Unit u : GameHandler.getEnemyUnits().stream()
					.filter(u -> u.getType() == UnitType.Protoss_Photon_Cannon
							|| u.getType() == UnitType.Zerg_Sunken_Colony || u.getType() == UnitType.Zerg_Spore_Colony)
					.collect(Collectors.toList())) {
				DrawEngine.drawCircleMap(u.getX(), u.getY(), u.getType().groundWeapon().maxRange(), Color.Red, false);
				DrawEngine.drawCircleMap(u.getX(), u.getY(), u.getType().airWeapon().maxRange(), Color.Red, false);
			}
		}).setActive(true);
		// Weapon cooldown bars
		DebugManager.createDebugModule("cooldowns").setDraw(() -> {
			for (UnitAgent ua : unitAgents.values()) {
				Unit u = ua.unit;
				UnitType unitType = u.getType();
				int cooldownBarSize = 20;
				int cooldownRemaining = u.getGroundWeaponCooldown();
				int maxCooldown = unitType.groundWeapon().damageCooldown();
				if (maxCooldown > 0) {
					DrawEngine.drawLineMap(u.getX(), u.getY(), u.getX() + cooldownBarSize, u.getY(), Color.Green);
					DrawEngine.drawLineMap(u.getX(), u.getY(),
							u.getX() + cooldownRemaining * cooldownBarSize / maxCooldown, u.getY(), Color.Red);
				}
			}
		}).setActive(true);
		// Pathing
		DebugManager.createDebugModule("pathing").setDraw(() -> {
			for (UnitAgent ua : unitAgents.values()) {
				DrawEngine.drawTextMap(ua.unit.getX(), ua.unit.getY() + 15,
						"Path: " + ua.path.size() + "/" + ua.pathOriginalSize + " (" + ua.pathStartFrame + ")");

				final Iterator<Position> it = ua.getPath().iterator();
				Position previous = it.hasNext() ? it.next() : null;
				while (it.hasNext()) {
					final Position current = it.next();
					DrawEngine.drawArrowMap(previous.getX(), previous.getY(), current.getX(), current.getY(),
							Color.Yellow);
					previous = current;
				}
				if (previous != null && ua.pathTarget != null) {
					DrawEngine.drawArrowMap(previous.getX(), previous.getY(), ua.pathTarget.getX(),
							ua.pathTarget.getY(), Color.Yellow);
				}
			}
		}).setActive(true);
		// Unit Agents
		DebugManager.createDebugModule("agents").setDraw(() -> {
			for (UnitAgent ua : unitAgents.values()) {
				DrawEngine.drawTextMap(ua.unit.getX(), ua.unit.getY() - 15, ua.getClass().getSimpleName());
			}
		}).setActive(true);
		// Tasks
		DebugManager.createDebugModule("tasks").setDraw(() -> {
			for (UnitAgent ua : unitAgents.values()) {
				switch (ua.task) {
				case CONSTRUCTING:
					DrawEngine.drawTextMap(ua.unit.getX(), ua.unit.getY(), "Construction");
					break;
				case GAS:
					DrawEngine.drawTextMap(ua.unit.getX(), ua.unit.getY(), "Gas");
					DrawEngine.drawLineMap(ua.unit.getX(), ua.unit.getY(), ((Worker) ua).getCurrentResource().getX(),
							((Worker) ua).getCurrentResource().getY(), Color.Green);
					break;
				case MINERALS:
					DrawEngine.drawTextMap(ua.unit.getX(), ua.unit.getY(), "Minerals");
					DrawEngine.drawLineMap(ua.unit.getX(), ua.unit.getY(), ((Worker) ua).getCurrentResource().getX(),
							((Worker) ua).getCurrentResource().getY(), Color.Blue);
					break;
				case SCOUTING:
					DrawEngine.drawTextMap(ua.unit.getX(), ua.unit.getY(), "Scouting");
					break;
				case ATTACK_RUN:
					DrawEngine.drawTextMap(ua.unit.getX(), ua.unit.getY(), "Attack run");
					break;
				case FIRING:
					DrawEngine.drawTextMap(ua.unit.getX(), ua.unit.getY(), "Firing");
					break;
				case IDLE:
					DrawEngine.drawTextMap(ua.unit.getX(), ua.unit.getY(), "Idle");
					break;
				case RETREATING:
					DrawEngine.drawTextMap(ua.unit.getX(), ua.unit.getY(), "Retreating");
					break;
				default:
					DrawEngine.drawTextMap(ua.unit.getX(), ua.unit.getY(), "Unknown");
					break;
				}
			}
		}).setActive(true);
	}
}
