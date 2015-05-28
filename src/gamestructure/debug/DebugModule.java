package gamestructure.debug;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.sun.org.apache.xpath.internal.functions.Function2Args;

/**
 * A single module to be used in the {@link DebugEngine}. Modules should be
 * instantiated as abstract inner classes within the class it is intended to
 * debug for. Each module should independently display one pertinent piece of
 * information from its enclosing class.<br>
 * Modules must ultimately be registered to the DebugEngine using
 * {@link DebugEngine#createDebugModule(DebugModule)}.
 * 
 * @author wesley
 *
 */
public class DebugModule {
	/**
	 * The name of this DebugModule. It must be and can only be set during
	 * construction. This is used when parsing commands to the
	 * {@link #DebugEngine}.
	 */
	public final String name;
	protected final DebugEngine engine;
	/**
	 * Basic functionality for all DebugModules is activating and deactivating.
	 * When this is true, the DebugModule will paint. When this is false, it
	 * will not. This is checked in {@link #drawIfActive}.<br>
	 * This is toggled by {@link #onReceiveCommand} by default. This behaviour
	 * can be overridden.
	 */
	private boolean active = false;
	/**
	 * Commands to be executed if seen. Commands mapped to <b>null</b> will be
	 * executed if there are no commands following the name of the debug module.
	 */
	private Map<String, CommandFunction> commands = new HashMap<>();
	/**
	 * Any {@link DebugModule}s nested inside this one. Commands will be passed
	 * down the hierarchy.
	 */
	private Map<String, DebugModule> subModules = new HashMap<>();
	private String lastAdded;
	private int lastAddedTo = LAT_NONE;
	private static final int LAT_NONE = 0;
	private static final int LAT_COMMAND = 1;
	private static final int LAT_SUBMODULE = 2;

	private DrawFunction draw = (e) -> {
	};

	/**
	 * Constructs a DebugModule with the specified name.
	 * 
	 * @param iname
	 *            The name to use when referring to the module.
	 * @param debugEngine
	 *            the debugEngine to use when drawing everything
	 */
	DebugModule(String iname, DebugEngine debugEngine) {
		name = iname;
		engine = debugEngine;
		commands.put(null, (c, e) -> active = !active);
	}

	public DebugModule addCommand(String command, CommandFunction action) {
		if (commands.containsKey(command)) {
			throw new UnsupportedOperationException();
		}
		commands.put(command, action);
		lastAdded = command;
		lastAddedTo = LAT_COMMAND;
		return this;
	}

	public DebugModule addSubmodule(String name) {
		DebugModule debugModule = new DebugModule(name, engine);
		subModules.put(name, debugModule);
		lastAdded = name;
		lastAddedTo = LAT_SUBMODULE;
		return debugModule;
	}

	public DebugModule addAlias(String alias) {
		return addAlias(lastAdded);
	}

	protected DebugModule addAlias(String alias, String old) {
		switch (lastAddedTo) {
		case LAT_COMMAND:
			addCommand(alias, commands.get(old));
			break;
		case LAT_SUBMODULE:
			subModules.put(alias, subModules.get(old));
			break;
		default:
		}
		return this;

	}

	public DebugModule setDraw(DrawFunction c) {
		draw = c;
		return this;
	}

	/**
	 * Draws the DebugModule if and only if it is active by calling
	 * {@link #draw}.
	 * 
	 * @param engine
	 *            The engine to use to draw.
	 * @throws ShapeOverflowException
	 *             If the engine throws a ShapeOverflowException, it propagates
	 *             outward here.
	 */
	final void drawIfActive(DebugEngine engine) throws ShapeOverflowException {
		if (active) {
			for (DebugModule s : subModules.values()) {
				s.drawIfActive(engine);
			}
			draw.accept(engine);
		}
	}

	/**
	 * This function is called when a command prefixed with the module's name is
	 * received. This function should be overridden for additional control over
	 * commands.
	 * 
	 * @param command
	 *            The line of commands received. The line is separated by
	 *            whitespace into the array.
	 *            <ul>
	 *            <li><b>command[0]</b> is either the module's name or "all".</li>
	 *            </ul>
	 * @param engine
	 *            The engine to use to draw.
	 * @throws InvalidCommandException
	 *             if the command cannot be parsed
	 */
	final void onReceiveCommand(List<String> command, DebugEngine engine)
			throws InvalidCommandException {
		// A command in my list
		if (commands.containsKey(command.get(0))) {
			commands.get(command.get(0)).apply(command, engine);
			return;
		}
		// A submodule in my list
		if (subModules.containsKey(command.get(0))) {
			subModules.get(command.get(0)).onReceiveCommand(
					command.subList(1, command.size()), engine);
			return;
		}

		throw new InvalidCommandException();
	}
}
