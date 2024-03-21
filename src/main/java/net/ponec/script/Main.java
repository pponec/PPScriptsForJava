package net.ponec.script;


import java.lang.reflect.InvocationTargetException;
import java.util.Set;

/** Common runner */
public class Main {

	private static final Set<Class<?>> classes = Set.of(
			DirectoryBookmarks.class,
			Mp3PlayerGenerator.class,
			PPUtils.class,
			SqlExecutor.class
	);

	public static void main(String[] arguments) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
		var args = PPUtils.Array.of(arguments);
		var mainClassName = args.getFirst().orElse("");
		var clazz = classes.stream()
				.filter(t -> mainClassName.equals(t.getSimpleName()))
				.findFirst()
				.orElseThrow(Main::illegalArgument);
		var mainMethod = clazz.getMethod("main", String[].class);
		mainMethod.invoke(null, args.subArray(1).toArray());
	}

	private static IllegalArgumentException illegalArgument() {
		var message = "Use some of the class %s".formatted(
				classes.stream().map(Class::getSimpleName).toList());
		return new IllegalArgumentException(message);
	}

}
