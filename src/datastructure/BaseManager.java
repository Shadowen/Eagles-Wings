package datastructure;

import java.awt.Point;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;

import bwapi.Color;
import bwapi.Position;
import bwapi.Unit;
import bwapi.UnitType;
import bwta.BWTA;
import bwta.BaseLocation;
import gamestructure.GameHandler;
import gamestructure.debug.DebugEngine;
import gamestructure.debug.DebugModule;
import gamestructure.debug.Debuggable;
import gamestructure.debug.ShapeOverflowException;

public class BaseManager implements Iterable<Base>, Debuggable {
	private GameHandler game;
	private Set<Base> bases;
	public Base main;
	/**
	 * The radius the bot looks around a potential base location to determine if
	 * it is occupied
	 **/
	private int baseRadius = 300;

	public BaseManager(GameHandler g, DebugEngine debugEngine) {
		game = g;
		bases = new HashSet<Base>();
		registerDebugFunctions(debugEngine);
		for (BaseLocation location : BWTA.getBaseLocations()) {
			Base b = new Base(game, location);
			bases.add(b);
		}
		for (Unit u : game.getNeutralUnits()) {
			UnitType type = u.getType();
			Base closestBase = getClosestBase(u.getPosition(), baseRadius);
			if (type.isMineralField()) {
				closestBase.minerals.add(new MineralResource(u));
			} else if (type.equals(UnitType.Resource_Vespene_Geyser)) {
				closestBase.gas.add(new GasResource(u));
			}
		}
	}

	public void gatherResources() {
		for (Base b : bases) {
			b.gatherResources();
		}
	}

	public Set<Base> getMyBases() {
		Set<Base> myBases = new HashSet<Base>();
		for (Base b : bases) {
			if (b.getPlayer() == game.getSelfPlayer()) {
				myBases.add(b);
			}
		}
		return myBases;
	}

	// Gets the closest base from a given (x, y) position
	public Base getClosestBase(int x, int y, int maxDistance) {
		Base closest = null;
		double closestDistance = Double.MAX_VALUE;
		for (Base b : bases) {
			double distance = Point.distance(x, y, b.getX(), b.getY());
			if (distance < closestDistance && distance < maxDistance) {
				closestDistance = distance;
				closest = b;
			}
		}
		return closest;
	}

	private Base getClosestBase(Position position, int maxDistance) {
		return getClosestBase(position.getX(), position.getY(), maxDistance);
	}

	public Worker getBuilder() {
		for (Base b : bases) {
			Worker u = b.getBuilder();
			if (u != null) {
				return u;
			}
		}
		return null;
	}

	public void unitDestroyed(Unit unit) {
		UnitType type = unit.getType();
		if (type == UnitType.Terran_SCV) {
			for (Base b : bases) {
				if (b.removeWorker(unit)) {
					break;
				}
			}
		} else if (type.isMineralField()) {
			for (Base b : bases) {
				if (b.minerals.remove(unit)) {
					break;
				}
			}
		} else if (type.equals(UnitType.Resource_Vespene_Geyser)
				|| type.isRefinery()) {
			for (Base b : bases) {
				if (b.gas.remove(unit)) {
					break;
				}
			}
		}
	}

	// Returns an iterator over all the bases
	@Override
	public Iterator<Base> iterator() {
		return bases.iterator();
	}

	public Worker getWorker(Unit u) {
		// Null check
		if (u == null) {
			return null;
		}

		// Find the right worker
		int uid = u.getID();
		for (Base b : bases) {
			Worker w = b.workers.get(uid);
			if (w != null) {
				return w;
			}
		}
		return null;
	}

	public void refineryComplete(Unit u) {
		getClosestBase(u.getPosition(), baseRadius).gas.add(new GasResource(u));
	}

	public Resource getResource(Unit u) {
		for (Base b : bases) {
			for (MineralResource mineral : b.minerals) {
				if (mineral.getUnit() == u) {
					return mineral;
				}
			}
			for (GasResource gas : b.gas) {
				if (gas.getUnit() == u) {
					return gas;
				}
			}
		}
		return null;
	}

	public void resourceDepotShown(Unit unit) {
		Base b = getClosestBase(unit.getPosition(), baseRadius);
		b.commandCenter = Optional.of(unit);
		b.setPlayer(unit.getPlayer());
	}

	public void resourceDepotDestroyed(Unit unit) {
		Base b = getClosestBase(unit.getPosition(), baseRadius);
		// TODO find the next closest building?
		b.commandCenter = null;
		b.setPlayer(game.getNeutralPlayer());
	}

	public void resourceDepotHidden(Unit unit) {
		getClosestBase(unit.getPosition(), baseRadius).setLastScouted();
	}

	public void workerComplete(Unit unit) {
		getClosestBase(unit.getPosition(), baseRadius).addWorker(unit);
	}

	@Override
	public void registerDebugFunctions(DebugEngine debugEngine) {
		debugEngine
				.createDebugModule("bases")
				.setDraw(engine -> {
					if (main != null) {
						engine.drawTextMap(main.getX(), main.getY(), "Main");
					}

					for (Base b : bases) {
						// Status
						engine.drawCircleMap(b.getX(), b.getY(), 100,
								Color.Teal, false);
						engine.drawTextMap(b.getX() + 5, b.getY() + 5,
								"Status: " + b.getPlayer().toString() + " @ "
										+ b.getLastScouted());

						// Command center
						if (b.commandCenter.isPresent()) {
							Unit c = b.commandCenter.get();
							int tx = c.getTilePosition().getX();
							int ty = c.getTilePosition().getY();
							engine.drawBoxMap(tx * 32, ty * 32, (tx + 4) * 32,
									(ty + 3) * 32, Color.Teal, false);
						}

						// Minerals
						for (MineralResource r : b.minerals) {
							engine.drawTextMap(r.getX() - 8, r.getY() - 8,
									String.valueOf(r.getNumGatherers()));
						}
						// Gas
						for (GasResource r : b.gas) {
							if (!r.gasTaken()) {
								engine.drawTextMap(r.getX(), r.getY(), "Geyser");
							} else {
								engine.drawTextMap(r.getX(), r.getY(),
										"Refinery");
							}
						}

						// Miner counts
						engine.drawTextMap(b.getX() + 5, b.getY() + 15,
								"Mineral Miners: " + b.getMineralWorkerCount());
						engine.drawTextMap(b.getX() + 5, b.getY() + 25,
								"Mineral Fields: " + b.minerals.size());

						// Workers
						for (Worker w : b.workers) {
							if (w.getTask() == WorkerTask.Mining_Minerals) {
								engine.drawCircleMap(w.getX(), w.getY(), 3,
										Color.Blue, true);
							} else if (w.getTask() == WorkerTask.Mining_Gas) {
								engine.drawCircleMap(w.getX(), w.getY(), 3,
										Color.Green, true);
							} else if (w.getTask() == WorkerTask.Constructing_Building) {
								engine.drawCircleMap(w.getX(), w.getY(), 3,
										Color.Orange, true);
							}
						}
					}
				});
	}
}
