package com.fireworkshaders.pyro;

import net.minecraft.world.item.Item;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Inversion of the one dependency the pyrotechnic model would otherwise have on the registry
 * package: {@link Chemical} needs to hand out its own item, but the registry needs the chemical
 * enum to build that item. Binding a supplier here keeps the whole {@code pyro} package free of
 * registry imports, which is what makes the burst generator usable from a unit test with no
 * Bootstrap.
 */
public final class FireworkShadersId {

    /** Registry namespace — must match {@code FireworkShaders.MOD_ID}. */
    public static final String NS = "fireworkshaders";

    private static final Map<Chemical, Supplier<Item>> ITEM_BINDINGS = new EnumMap<>(Chemical.class);

    private FireworkShadersId() {}

    public static void bindItem(Chemical chemical, Supplier<Item> item) {
        ITEM_BINDINGS.put(chemical, item);
    }

    @Nullable
    public static Item itemFor(Chemical chemical) {
        Supplier<Item> supplier = ITEM_BINDINGS.get(chemical);
        return supplier == null ? null : supplier.get();
    }

    public static boolean isBound(Chemical chemical) {
        return ITEM_BINDINGS.containsKey(chemical);
    }
}
