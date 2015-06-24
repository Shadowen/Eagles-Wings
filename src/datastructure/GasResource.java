package datastructure;

import bwapi.Unit;

public class GasResource extends Resource {

	public GasResource(Unit u) {
		super(u);
	}

	public boolean gasTaken() {
		return unit.getType().isRefinery();
	}
}