package eaglesWings.datastructure;

import java.awt.Point;
import java.util.HashMap;
import java.util.Map.Entry;

import eaglesWings.gamestructure.DebugEngine;
import eaglesWings.gamestructure.DebugModule;
import eaglesWings.gamestructure.GameHandler;
import javabot.model.*;
import javabot.util.BWColor;

public class Base {
	private GameHandler game;

	public java.util.Map<Integer, MineralResource> minerals;
	public java.util.Map<Integer, GasResource> gas;
	public java.util.Map<Integer, Worker> workers;
	public Unit commandCenter;
	private BaseLocation location;

	private BaseStatus status;
	// When the base was last scouted, in game frames
	private long lastScouted;

	public Base(GameHandler g, BaseLocation l) {
		game = g;

		workers = new HashMap<Integer, Worker>();
		location = l;

		minerals = new HashMap<Integer, MineralResource>();
		gas = new HashMap<Integer, GasResource>();

		status = BaseStatus.UNOCCUPIED;
		lastScouted = 0;
	}

	public int getX() {
		return location.getX();
	}

	public int getY() {
		return location.getY();
	}

	public int getMineralCount() {
		return minerals.size();
	}

	public void gatherResources() {
		// Idle workers
		for (Entry<Integer, Worker> entry : workers.entrySet()) {
			Worker worker = entry.getValue();
			WorkerTask currentTask = worker.getTask();
			if (worker.isIdle()
					&& (currentTask == WorkerTask.Mining_Minerals || currentTask == WorkerTask.Mining_Gas)) {
				game.sendText("Idle worker detected!");
				// Get back to work
				if (worker.getCurrentResource() != null) {
					worker.gather(worker.getCurrentResource());
					continue;
				}

				// Try to assign one worker to each mineral first
				Resource mineral = null;
				double distance = 0;

				// This variable is the loop counter
				// It only allows maxMiners to gather each resource patch each
				// loop.
				int maxMiners = 1;
				boolean workerAssigned = false;
				while (!workerAssigned) {
					if (maxMiners > 3) {
						// supersaturated - can't find a suitable resource patch
						throw new StackOverflowError();
					}

					for (Entry<Integer, MineralResource> m : minerals
							.entrySet()) {
						if (m.getValue().getNumGatherers() < maxMiners) {
							// Find closest mineral patch
							double newDistance = Point.distance(entry
									.getValue().getX(),
									entry.getValue().getY(), m.getValue()
											.getX(), m.getValue().getY());
							if (mineral == null || newDistance < distance) {
								mineral = m.getValue();
								distance = newDistance;
								workerAssigned = true;
							}
						}
					}

					maxMiners++;
				}

				// Actually issue the order
				entry.getValue().gather(mineral);
			}
		}
	}

	public Worker getBuilder() {
		Worker u = null;
		for (Entry<Integer, Worker> w : workers.entrySet()) {
			if (w.getValue().getTask() == WorkerTask.Mining_Minerals) {
				u = w.getValue();
			}
		}
		return u;
	}

	public int getWorkerCount() {
		return workers.size();
	}

	public int getMineralWorkerCount() {
		int i = 0;
		for (Entry<Integer, Worker> w : workers.entrySet()) {
			if (w.getValue().getTask() == WorkerTask.Mining_Minerals) {
				i++;
			}
		}
		return i;
	}

	public void addWorker(int unitID, Unit unit) {
		Worker w = new Worker(game, unit);
		w.setBase(this);
		w.setTask(WorkerTask.Mining_Minerals, null);
		workers.put(unitID, w);
	}

	public boolean removeWorker(int unitID) {
		// That worker actually does belong to this base!
		if (workers.containsKey(unitID)) {
			workers.get(unitID).unitDestroyed();
			workers.remove(unitID);
			return true;
		}
		// That worker does not belong to this base
		return false;
	}

	public BaseLocation getLocation() {
		return location;
	}

	public void setStatus(BaseStatus s) {
		status = s;
		lastScouted = game.getFrameCount();
	}

	public BaseStatus getStatus() {
		return status;
	}

	public long getLastScouted() {
		return lastScouted;
	}
}