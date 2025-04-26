package net.ponec.script;


import java.lang.reflect.InvocationTargetException;
import java.util.Set;

/** Common runner */
public class Main {

	private static final Set<Class<?>> classes = Set.of(
			DirectoryBookmarks.class,
			Mp3Player.class,
			PPUtils.class,
			SqlExecutor.class
	);

	public static void main(String[] arguments) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
		var args = PPUtils.List.of(arguments);
		var mainClassName = args.getFirst("");
		var clazz = classes.stream()
				.filter(t -> mainClassName.equals(t.getSimpleName()))
				.findFirst()
				.orElseThrow(Main::illegalArgument);
		var mainMethod = clazz.getMethod("main", String[].class);
		var reflectionArgs = new Object[] { args.subList(1).toArray() };
		mainMethod.invoke(null, reflectionArgs);
	}

	private static IllegalArgumentException illegalArgument() {
		var message = "Use some of the class %s".formatted(
				classes.stream().map(Class::getSimpleName).toList());
		return new IllegalArgumentException(message);
	}

}
