package com.fireworkshaders.registry;

import com.fireworkshaders.FireworkShaders;
import com.fireworkshaders.pyro.Chemical;
import com.fireworkshaders.pyro.ShellPresets;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * One tab, ordered the way a pyro technician thinks: machines first, then reagents, then the five
 * premium shells already assembled so a player can see what the workstation can produce before
 * they have built anything themselves.
 */
public final class FSCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, FireworkShaders.MOD_ID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + FireworkShaders.MOD_ID + ".main"))
                    .icon(() -> new ItemStack(FSItems.CUSTOM_FIREWORK.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(FSItems.PYROTECHNIC_WORKSTATION.get());
                        output.accept(FSItems.LAUNCH_PAD.get());
                        output.accept(FSItems.REMOTE_IGNITER.get());
                        output.accept(FSItems.CUSTOM_FIREWORK.get());
                        for (Chemical chemical : Chemical.values()) {
                            output.accept(FSItems.chemical(chemical).get());
                        }
                        // The premium shells, pre-assembled. Same RocketDesign objects the
                        // workstation presets use — no special items, no hidden effects.
                        for (ShellPresets.Preset preset : ShellPresets.all()) {
                            output.accept(preset.design().createStack(FSItems.CUSTOM_FIREWORK.get(), 1));
                        }
                    })
                    .build());

    private FSCreativeTabs() {}
}
