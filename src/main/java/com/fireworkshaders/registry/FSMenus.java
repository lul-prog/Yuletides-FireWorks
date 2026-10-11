package com.fireworkshaders.registry;

import com.fireworkshaders.FireworkShaders;
import com.fireworkshaders.common.menu.LaunchPadMenu;
import com.fireworkshaders.common.menu.PyrotechnicWorkstationMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class FSMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, FireworkShaders.MOD_ID);

    /**
     * Both menus are addressed by block position, which is what the trailing packet reader on each
     * factory is for: Forge's {@code IForgeMenuType} pairs with {@code NetworkHooks.openScreen} to
     * send the position alongside the open-screen packet, so the client can find its block entity
     * and mirror the menu's contents.
     */
    public static final RegistryObject<MenuType<PyrotechnicWorkstationMenu>> PYROTECHNIC_WORKSTATION =
            MENUS.register("pyrotechnic_workstation",
                    () -> IForgeMenuType.create((windowId, inventory, data) ->
                            new PyrotechnicWorkstationMenu(windowId, inventory, data.readBlockPos())));

    public static final RegistryObject<MenuType<LaunchPadMenu>> LAUNCH_PAD =
            MENUS.register("launch_pad",
                    () -> IForgeMenuType.create((windowId, inventory, data) ->
                            new LaunchPadMenu(windowId, inventory, data.readBlockPos())));

    private FSMenus() {}
}
