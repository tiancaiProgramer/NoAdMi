package io.github.miclean.module;

import java.lang.reflect.*;

/** Narrow reflection helpers; no dependency on the legacy Xposed helpers/runtime. */
final class Reflect {
    static Class<?> findClass(String name, ClassLoader loader) {
        try { return Class.forName(name, false, loader); }
        catch (ClassNotFoundException e) { throw new IllegalArgumentException(name, e); }
    }
    static Class<?> findClassIfExists(String name, ClassLoader loader) {
        try { return Class.forName(name, false, loader); }
        catch (ClassNotFoundException e) { return null; }
    }
    static void findAndHookMethod(String cls, ClassLoader loader, String name, Object... args) {
        findAndHookMethod(findClass(cls, loader), name, args);
    }
    static void findAndHookMethod(Class<?> cls, String name, Object... args) {
        try {
            Class<?>[] parameters = new Class<?>[args.length - 1];
            for (int i = 0; i < parameters.length; i++) parameters[i] = (Class<?>) args[i];
            HookRuntime.hookMethod(cls.getDeclaredMethod(name, parameters), (MethodHook) args[args.length - 1]);
        } catch (NoSuchMethodException e) { throw new IllegalArgumentException(name, e); }
    }
    private static Field field(Class<?> cls, String name) {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            try { Field f = c.getDeclaredField(name); f.setAccessible(true); return f; }
            catch (NoSuchFieldException ignored) {}
        }
        throw new IllegalArgumentException("Field absent: " + name);
    }
    static Object getObjectField(Object object, String name) {
        try { return field(object.getClass(), name).get(object); }
        catch (IllegalAccessException e) { throw new IllegalStateException(e); }
    }
    static Object getStaticObjectField(Class<?> cls, String name) {
        try { return field(cls, name).get(null); }
        catch (IllegalAccessException e) { throw new IllegalStateException(e); }
    }
    private static Class<?> boxed(Class<?> type) {
        if (type == int.class) return Integer.class;
        if (type == boolean.class) return Boolean.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == char.class) return Character.class;
        return type;
    }
    static Object callMethod(Object object, String name, Object... args) {
        Method selected = null;
        for (Class<?> c = object.getClass(); c != null && selected == null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (!name.equals(m.getName()) || m.getParameterTypes().length != args.length) continue;
                boolean matches = true;
                Class<?>[] types = m.getParameterTypes();
                for (int i = 0; i < types.length; i++)
                    if (args[i] == null ? types[i].isPrimitive() : !boxed(types[i]).isInstance(args[i])) matches = false;
                if (!matches) continue;
                if (selected != null) throw new IllegalArgumentException("Ambiguous method: " + name);
                selected = m;
            }
        }
        if (selected == null) throw new IllegalArgumentException("Method absent: " + name);
        try { selected.setAccessible(true); return selected.invoke(object, args); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
}
