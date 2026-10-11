package com.fireworkshaders.registry;

import com.fireworkshaders.FireworkShaders;
import com.fireworkshaders.common.item.ChemicalItem;
import com.fireworkshaders.common.item.CustomFireworkItem;
import com.fireworkshaders.common.item.RemoteIgniterItem;
import com.fireworkshaders.pyro.Chemical;
import com.fireworkshaders.pyro.FireworkShadersId;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class FSItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, FireworkShaders.MOD_ID);

    private static final Map<Chemical, RegistryObject<Item>> CHEMICALS =
            Collections.synchronizedMap(new EnumMap<>(Chemical.class));

    /** The designed shell. Its {@code RocketDesign} lives in the stack's NBT. */
    public static final RegistryObject<Item> CUSTOM_FIREWORK = ITEMS.register("custom_firework",
            () -> new CustomFireworkItem(new Item.Properties().stacksTo(16)));

    public static final RegistryObject<Item> REMOTE_IGNITER = ITEMS.register("remote_igniter",
            () -> new RemoteIgniterItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    public static final RegistryObject<Item> PYROTECHNIC_WORKSTATION = ITEMS.register(
            "pyrotechnic_workstation", () -> new BlockItem(FSBlocks.PYROTECHNIC_WORKSTATION.get(),
                    new Item.Properties()));

    public static final RegistryObject<Item> LAUNCH_PAD = ITEMS.register("launch_pad",
            () -> new BlockItem(FSBlocks.LAUNCH_PAD.get(), new Item.Properties()));

    static {
        for (Chemical chemical : Chemical.values()) {
            RegistryObject<Item> item = ITEMS.register(chemical.itemName(),
                    () -> new ChemicalItem(chemical, new Item.Properties()));
            CHEMICALS.put(chemical, item);
            // Inverts the dependency: the pyro model asks for its item, the registry binds it.
            FireworkShadersId.bindItem(chemical, item);
        }
    }

    public static RegistryObject<Item> chemical(Chemical chemical) {
        RegistryObject<Item> item = CHEMICALS.get(chemical);
        if (item == null) {
            throw new IllegalStateException("Chemical item not bound: " + chemical);
        }
        return item;
    }

    public static Map<Chemical, RegistryObject<Item>> chemicals() {
        return CHEMICALS;
    }

    private FSItems() {}
}
