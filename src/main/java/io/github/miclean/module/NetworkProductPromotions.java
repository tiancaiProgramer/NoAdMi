package io.github.miclean.module;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/** Filters Network Assistant's legacy cloud product feed before it builds the rows. */
final class NetworkProductPromotions {
    private static final String BEAN = "com.miui.networkassistant.ui.bean.";
    private static final Pattern DATA_SIM = Pattern.compile(
            "[0-9]{1,4}\\s*G(?:B)?\\s*(?:大|通用)?流量卡(?:\\s*[,、：:·].*)?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DATA_BUNDLE = Pattern.compile("特惠流量包(?:\\s*[,、：:·].*)?");

    static boolean matches(String title) {
        if (title == null) return false;
        String normalized = Normalizer.normalize(title, Normalizer.Form.NFKC).trim();
        return DATA_SIM.matcher(normalized).matches() || DATA_BUNDLE.matcher(normalized).matches();
    }

    interface Title {
        String of(Object product) throws ReflectiveOperationException;
    }

    static List<?> filter(List<?> slots, Title title) throws ReflectiveOperationException {
        List<Object> filtered = new ArrayList<>(slots);
        boolean changed = false;
        for (int i = 0; i < slots.size(); i++) {
            Object slot = slots.get(i);
            if (!(slot instanceof List<?>)) return slots;
            List<?> products = (List<?>) slot;
            List<Object> retained = new ArrayList<>(products.size());
            for (Object product : products) {
                if (product == null || !matches(title.of(product))) retained.add(product);
            }
            if (retained.size() != products.size()) {
                filtered.set(i, retained);
                changed = true;
            }
        }
        return changed ? filtered : slots;
    }

    static void install(ClassLoader loader, Class<?> activity) throws ReflectiveOperationException {
        Class<?> data = Class.forName(BEAN + "FunctionData", false, loader);
        Class<?> banner = Class.forName(BEAN + "FunctionBanner", false, loader);
        Class<?> secondary = Class.forName(BEAN + "SecondaryCardRec", false, loader);
        Class<?> product = Class.forName(BEAN + "Product", false, loader);
        Method deliver = method(activity, "showData", void.class, data, int.class);
        Method getData = method(data, "getData", banner);
        Method getCode = method(data, "getErrCode", int.class);
        Method getMessage = method(data, "getErrMsg", String.class);
        Method getRows = method(banner, "getProductList", List.class);
        Method getSecondary = method(banner, "getSecondaryCardRec", secondary);
        Method getTitle = method(product, "getProductTitle", String.class);
        Method copyBanner = method(banner, "copy", banner, List.class, secondary);
        Method copyData = method(data, "copy", data, banner, int.class, String.class);
        HookRuntime.hookMethod(deliver, new MethodHook() {
            private final AtomicBoolean logged = new AtomicBoolean();

            @Override protected void beforeHookedMethod(Param p) throws ReflectiveOperationException {
                Object response = p.args[0];
                if (response == null) return;
                Object payload = getData.invoke(response);
                if (payload == null) return;
                List<?> rows = (List<?>) getRows.invoke(payload);
                if (rows == null) return;
                List<?> filtered = filter(rows, row -> (String) getTitle.invoke(row));
                if (filtered == rows) return;
                Object secondaryCard = getSecondary.invoke(payload);
                Object message = getMessage.invoke(response);
                // The audited Kotlin copy methods reject null even when a JSON parser accepted it.
                // The narrowly scoped view fallback still handles that case.
                if (secondaryCard == null || message == null) return;
                Object newPayload = copyBanner.invoke(payload, filtered, secondaryCard);
                p.args[0] = copyData.invoke(response, newPayload, getCode.invoke(response), message);
                if (logged.compareAndSet(false, true))
                    HookEntry.report("hit system.network.product-feed");
            }
        });
        HookEntry.report("installed system.network.product-feed");
    }

    private static Method method(Class<?> owner, String name, Class<?> result, Class<?>... arguments)
            throws NoSuchMethodException {
        Method method = owner.getDeclaredMethod(name, arguments);
        if (method.getReturnType() != result || !Modifier.isPublic(method.getModifiers())
                || Modifier.isStatic(method.getModifiers()))
            throw new NoSuchMethodException(owner.getName() + "#" + name);
        return method;
    }

    private NetworkProductPromotions() {}
}
