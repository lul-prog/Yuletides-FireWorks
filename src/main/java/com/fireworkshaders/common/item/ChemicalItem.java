package com.fireworkshaders.common.item;

import com.fireworkshaders.pyro.Chemical;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A chemical reagent. The tooltip carries the real chemistry (formula, burn temperature and the
 * emission line for colour producers) because that is the fastest way to teach a player why the
 * green one needs barium and why the sodium one ruins everything it is mixed with.
 */
public class ChemicalItem extends Item {

    private final Chemical chemical;

    public ChemicalItem(Chemical chemical, Properties properties) {
        super(properties);
        this.chemical = chemical;
    }

    public Chemical chemical() {
        return this.chemical;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.literal(this.chemical.formula() + "  \u00b7  "
                        + String.format("%,d \u00b0C", this.chemical.burnTempC()))
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal(this.chemical.note()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("chemical." + com.fireworkshaders.pyro.FireworkShadersId.NS
                        + ".role." + this.chemical.role().name().toLowerCase(java.util.Locale.ROOT))
                .withStyle(ChatFormatting.DARK_AQUA));
    }
}
