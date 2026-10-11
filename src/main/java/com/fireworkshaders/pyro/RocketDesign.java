package com.fireworkshaders.pyro;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * A complete firework, as data. This object is:
 *
 * <ul>
 *   <li>what the Pyrotechnic Workstation GUI edits,</li>
 *   <li>what is written into the rocket {@link ItemStack}'s NBT when it is assembled,</li>
 *   <li>what is synced to the client on the rocket entity,</li>
 *   <li>the only input (plus a seed) the burst generator needs.</li>
 * </ul>
 *
 * <p>Immutable on purpose. The GUI doesn't mutate a design, it produces a new one via
 * {@link #toBuilder()} — which means the entity can hand the same instance to every player in
 * range, and a "did the design change?" check is an {@code equals} call instead of a diff.
 *
 * <p>{@code payload} is the recursive part: a shell-of-shells carries a second design that its
 * children use when they break. Depth is capped at one (a shell inside a shell inside a shell is
 * 64 simultaneous explosions, which is a server-owner's nightmare and looks like mush anyway).
 */
public final class RocketDesign {

    public static final int MAX_PALETTE = 4;
    public static final int FORMAT = 1;
    public static final String TAG_ROOT = "YuletideDesign";
    public static final int MAX_PAYLOAD_DEPTH = 1;
    public static final int MAX_NAME_LENGTH = 40;

    public static final float MIN_CALIBER = 0.6F;
    public static final float MAX_CALIBER = 3.0F;

    private final List<Chemical> palette;
    private final ShellShape shape;
    private final EnumSet<EffectModule> modules;
    private final float caliber;
    private final int fuse;
    private final String name;
    @Nullable
    private final RocketDesign payload;

    private final int cachedHash;

    private RocketDesign(List<Chemical> palette, ShellShape shape, EnumSet<EffectModule> modules,
                         float caliber, int fuse, String name, @Nullable RocketDesign payload) {
        this.palette = Collections.unmodifiableList(new ArrayList<>(palette));
        this.shape = shape;
        this.modules = modules.clone();
        this.caliber = caliber;
        this.fuse = fuse;
        this.name = name;
        this.payload = payload;
        this.cachedHash = Objects.hash(this.palette, this.shape, this.modules, this.caliber, this.fuse,
                this.name.toLowerCase(Locale.ROOT), this.payload);
    }

    // ------------------------------------------------------------------ construction

    public static Builder builder() {
        return new Builder();
    }

    /** The design a fresh workstation starts on: a one-colour peony that is cheap and safe. */
    public static RocketDesign defaultDesign() {
        return builder()
                .palette(List.of(Chemical.STRONTIUM_SALT))
                .shape(ShellShape.PEONY)
                .caliber(1.0F)
                .name("")
                .build();
    }

    public Builder toBuilder() {
        Builder b = new Builder();
        b.palette = new ArrayList<>(this.palette);
        b.shape = this.shape;
        b.modules = this.modules.clone();
        b.caliber = this.caliber;
        b.fuse = this.fuse;
        b.name = this.name;
        b.payload = this.payload;
        return b;
    }

    public static final class Builder {
        private List<Chemical> palette = new ArrayList<>(List.of(Chemical.STRONTIUM_SALT));
        private ShellShape shape = ShellShape.PEONY;
        private EnumSet<EffectModule> modules = EnumSet.noneOf(EffectModule.class);
        private float caliber = 1.0F;
        private int fuse = -1;
        private String name = "";
        @Nullable
        private RocketDesign payload;

        public Builder palette(List<Chemical> chemicals) {
            List<Chemical> cleaned = new ArrayList<>();
            for (Chemical c : chemicals) {
                if (c != null && !cleaned.contains(c) && cleaned.size() < MAX_PALETTE) {
                    cleaned.add(c);
                }
            }
            if (cleaned.isEmpty()) {
                cleaned.add(Chemical.STRONTIUM_SALT);
            }
            this.palette = cleaned;
            return this;
        }

        public Builder addColour(Chemical chemical) {
            if (chemical != null && !this.palette.contains(chemical) && this.palette.size() < MAX_PALETTE) {
                this.palette.add(chemical);
            }
            return this;
        }

        public Builder clearColours() {
            this.palette.clear();
            return this;
        }

        public Builder shape(ShellShape shape) {
            this.shape = shape == null ? ShellShape.PEONY : shape;
            return this;
        }

        public Builder modules(EnumSet<EffectModule> modules) {
            this.modules = modules == null ? EnumSet.noneOf(EffectModule.class) : modules.clone();
            return this;
        }

        public Builder toggle(EffectModule module) {
            if (module == null) {
                return this;
            }
            if (!this.modules.remove(module)) {
                this.modules.add(module);
            }
            return this;
        }

        public Builder with(EffectModule module, boolean enabled) {
            if (module != null) {
                if (enabled) {
                    this.modules.add(module);
                } else {
                    this.modules.remove(module);
                }
            }
            return this;
        }

        public Builder caliber(float caliber) {
            this.caliber = PyroMath.clamp01((caliber - MIN_CALIBER) / (MAX_CALIBER - MIN_CALIBER))
                    * (MAX_CALIBER - MIN_CALIBER) + MIN_CALIBER;
            this.fuse = -1;
            return this;
        }

        /** Ascent time in ticks. Passing anything below 1 restores the caliber-derived default. */
        public Builder fuse(int ticks) {
            this.fuse = ticks;
            return this;
        }

        public Builder name(String name) {
            String clean = name == null ? "" : name.trim();
            if (clean.length() > MAX_NAME_LENGTH) {
                clean = clean.substring(0, MAX_NAME_LENGTH);
            }
            // Strip formatting: a firework name must not be able to inject a colour code into the
            // hotbar, and §-codes in synced NBT are a (small) griefing vector.
            this.name = clean.replace("\u00a7", "").replace("\n", " ");
            return this;
        }

        public Builder payload(@Nullable RocketDesign design) {
            this.payload = design;
            return this;
        }

        public Builder copyPayloadFrom(RocketDesign other) {
            this.payload = other.payload;
            return this;
        }

        public RocketDesign build() {
            float cal = Float.isFinite(this.caliber)
                    ? (float) PyroMath.clamp(this.caliber, MIN_CALIBER, MAX_CALIBER) : MIN_CALIBER;
            int fuseTicks = this.fuse > 0 ? PyroMath.clampInt(this.fuse, 8, 260) : defaultFuse(cal);
            RocketDesign child = this.payload;
            if (child != null && child.payload != null) {
                // Enforce the documented one-level payload limit for designs built through APIs as
                // well as designs loaded from item NBT.
                child = child.toBuilder().payload(null).build();
            }
            return new RocketDesign(this.palette, this.shape, this.modules, cal, fuseTicks, this.name,
                    child);
        }
    }

    public static int defaultFuse(float caliber) {
        return PyroMath.clampInt(Math.round(38.0F + caliber * 15.0F), 8, 260);
    }

    // ------------------------------------------------------------------ accessors

    public List<Chemical> palette() {
        return this.palette;
    }

    public Chemical primaryColour() {
        return this.palette.get(0);
    }

    public ShellShape shape() {
        return this.shape;
    }

    public EnumSet<EffectModule> modules() {
        return this.modules.clone();
    }

    public boolean has(EffectModule module) {
        return this.modules.contains(module);
    }

    public float caliber() {
        return this.caliber;
    }

    public int fuse() {
        return this.fuse;
    }

    public String rawName() {
        return this.name;
    }

    @Nullable
    public RocketDesign payload() {
        return this.payload;
    }

    public boolean hasPayload() {
        return this.payload != null;
    }

    /** The index of a colour inside the palette, wrapping. Alternating stars use this. */
    public int colorRgb(int index) {
        return this.palette.get(Math.floorMod(index, this.palette.size())).effectiveRgb();
    }

    /** Blended palette lookup — used by DUST, SPECTRUM and the canopy fade. */
    public int colorAt(float t) {
        if (this.palette.size() == 1) {
            return this.palette.get(0).effectiveRgb();
        }
        float x = PyroMath.clamp01(t) * (this.palette.size() - 1);
        int i = (int) x;
        float f = x - i;
        int a = this.palette.get(i).effectiveRgb();
        int b = this.palette.get(Math.min(i + 1, this.palette.size() - 1)).effectiveRgb();
        return PyroMath.lerpColor(a, b, f);
    }

    /** Average brightness contribution of the palette, used to price and to scale the flash. */
    public float intensity() {
        float sum = 0.0F;
        for (Chemical c : this.palette) {
            sum += c.intensity();
        }
        return sum / this.palette.size();
    }

    // ------------------------------------------------------------------ derived pyrotechnics

    /** Star count before the server-side budget clamp. Grows faster than linear with caliber. */
    public int starCount() {
        float n = 128.0F * this.shape.countMultiplier() * (float) Math.pow(this.caliber, 1.72);
        if (has(EffectModule.DUST)) {
            n *= 1.45F;
        }
        if (has(EffectModule.SHELL_OF_SHELLS)) {
            // The main break is deliberately sparse: the eight children are the payload.
            n *= 0.5F;
        }
        if (has(EffectModule.SPECTRUM)) {
            n *= 0.85F;
        }
        return PyroMath.clampInt(Math.round(n), 18, 3200);
    }

    /** Peak radius in blocks. */
    public double burstRadius() {
        return burstSpeed() * burnTicks();
    }

    /** Outward star velocity, blocks/tick. A 3" shell really does open at ~40 m/s. */
    public double burstSpeed() {
        return (1.02 + 0.34 * this.caliber) * (has(EffectModule.DUST) ? 0.82 : 1.0);
    }

    /** Lift velocity, blocks/tick. */
    public double liftSpeed() {
        double v = 0.74 + 0.19 * this.caliber;
        if (has(EffectModule.SHRIEK)) {
            v *= 2.45;
        }
        if (has(EffectModule.WHISTLE)) {
            v *= 1.06;
        }
        return v;
    }

    /** Star burn time in ticks. */
    public int burnTicks() {
        float t = 52.0F + 20.0F * this.caliber;
        switch (this.shape) {
            case WILLOW, CASCADE, SPHERE -> t *= 1.42F;
            case CROSSETTE, COMET -> t *= 0.72F;
            case CHRYSANTHEMUM -> t *= 1.15F;
            default -> {
            }
        }
        if (has(EffectModule.DRAPE)) {
            t *= 1.35F;
        }
        if (has(EffectModule.STROBE)) {
            t *= 0.85F;
        }
        return PyroMath.clampInt(Math.round(t), 12, 420);
    }

    /** Slow heavy embers ("LINGER"), or the Dragon's Breath ember rain. */
    public int emberCount() {
        if (!has(EffectModule.LINGER) && !has(EffectModule.GROUND_GLOW)) {
            return 0;
        }
        return PyroMath.clampInt(Math.round(starCount() * 0.22F), 4, 240);
    }

    /** A single scalar that the camera, the audio mixer and the light pulses all scale off. */
    public float power() {
        float p = (float) Math.pow(this.caliber, 1.5);
        p *= 1.0F + this.modules.size() * 0.055F;
        if (has(EffectModule.FLASH)) {
            p *= 1.18F;
        }
        if (has(EffectModule.SHOCKWAVE)) {
            p *= 1.35F;
        }
        return p;
    }

    /** How much smoke this shell leaves. */
    public float smokeAmount() {
        float s = 0.55F * this.caliber;
        if (has(EffectModule.SMOKE)) {
            s *= 2.15F;
        }
        switch (this.shape) {
            case WILLOW, CASCADE, SPHERE -> s *= 1.25F;
            case COMET -> s *= 1.6F;
            default -> {
            }
        }
        if (this.palette.contains(Chemical.CHARCOAL) || this.palette.contains(Chemical.IRON_FILINGS)) {
            s *= 1.4F;
        }
        return s;
    }

    /** Behaviour bits handed to every spark this design spawns. */
    public int flags() {
        int f = PyroFlags.FULLBRIGHT | PyroFlags.WIND;
        if (has(EffectModule.TRAIL)) {
            f |= PyroFlags.TRAIL;
        }
        if (has(EffectModule.CRACKLE)) {
            f |= PyroFlags.CRACKLE;
        }
        if (has(EffectModule.STROBE)) {
            f |= PyroFlags.STROBE;
        }
        if (has(EffectModule.DRAPE)) {
            f |= PyroFlags.DRAPE;
        }
        if (has(EffectModule.SPECTRUM)) {
            f |= PyroFlags.SPECTRUM;
        }
        if (has(EffectModule.GROUND_GLOW)) {
            f |= PyroFlags.GROUND_GLOW;
        }
        if (has(EffectModule.LINGER)) {
            f |= PyroFlags.SMOKY;
        }
        return f;
    }

    // ------------------------------------------------------------------ cost

    /**
     * Total propellant/binder in "grains" (1 grain == 1 gunpowder). The workstation consumes
     * exactly this when the shell is assembled, so the price of a design is always visible in the
     * GUI before you commit to it.
     */
    public int grainCost() {
        int grains = Math.round(6.0F * this.caliber * this.palette.size());
        grains += EffectModule.totalCost(this.modules);
        if (this.payload != null) {
            grains += Math.round(this.payload.grainCost() * 0.6F) + 4;
        }
        return grains;
    }

    /** Everything the workstation needs to pull out of its ingredient slots to build this shell. */
    public List<ItemStack> ingredientCost() {
        List<ItemStack> cost = new ArrayList<>();
        for (Chemical c : this.palette) {
            ItemStack stack = c.asStack(Math.max(1, Math.round(2.0F * this.caliber)));
            if (stack != null) {
                cost.add(stack);
            }
        }
        ItemStack grains = Chemical.POTASSIUM_PERCHLORATE.asStack(grainCost() / 2 + 1);
        if (grains != null) {
            cost.add(grains);
        }
        ItemStack binder = Chemical.DEXTRIN.asStack(1 + this.modules.size() / 3);
        if (binder != null) {
            cost.add(binder);
        }
        if (this.payload != null) {
            cost.addAll(this.payload.ingredientCost());
        }
        return cost;
    }

    // ------------------------------------------------------------------ presentation

    /** The item's hover name: the player's own name for the shell, or a generated one. */
    public Component displayName() {
        if (!this.name.isEmpty()) {
            return Component.literal(this.name);
        }
        return Component.translatable("design." + FireworkShadersId.NS + ".auto_name",
                this.primaryColour().nameComponent(),
                Component.translatable(this.shape.translationKey()));
    }

    /** A one-line summary for tooltips and the workstation header. */
    public Component summary() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < this.palette.size(); i++) {
            if (i > 0) {
                sb.append('+');
            }
            Chemical c = this.palette.get(i);
            sb.append(c.displayName().replace(" Nitrate", "").replace(" Chloride", "")
                    .replace(" Powder", "").replace(" Sponge", "").replace(" Filings", ""));
        }
        sb.append(" ").append(prettyEnum(this.shape.name()));
        return Component.literal(sb.toString()).withStyle(ChatFormatting.GRAY);
    }

    public List<Component> tooltip() {
        List<Component> lines = new ArrayList<>();
        lines.add(summary());
        lines.add(Component.translatable("tooltip." + FireworkShadersId.NS + ".caliber",
                String.format(Locale.ROOT, "%.2f", this.caliber)).withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.translatable("tooltip." + FireworkShadersId.NS + ".stars",
                this.starCount()).withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.translatable("tooltip." + FireworkShadersId.NS + ".fuse",
                String.format(Locale.ROOT, "%.1f", this.fuse / 20.0F)).withStyle(ChatFormatting.DARK_GRAY));
        if (!this.modules.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (EffectModule m : this.modules) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(prettyEnum(m.name()));
            }
            lines.add(Component.literal(sb.toString()).withStyle(ChatFormatting.AQUA));
        }
        if (this.payload != null) {
            lines.add(Component.translatable("tooltip." + FireworkShadersId.NS + ".payload",
                    this.payload.summary()).withStyle(ChatFormatting.GOLD));
        }
        lines.add(Component.translatable("tooltip." + FireworkShadersId.NS + ".grains",
                this.grainCost()).withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    private static String prettyEnum(String enumName) {
        String[] parts = enumName.toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ serialisation

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("format", FORMAT);
        tag.putString("shape", this.shape.id());
        tag.putFloat("caliber", this.caliber);
        tag.putInt("fuse", this.fuse);
        tag.putInt("modules", EffectModule.toMask(this.modules));
        if (!this.name.isEmpty()) {
            tag.putString("name", this.name);
        }
        int[] colours = new int[this.palette.size()];
        for (int i = 0; i < this.palette.size(); i++) {
            colours[i] = this.palette.get(i).ordinal();
        }
        tag.putIntArray("palette", colours);
        if (this.payload != null) {
            tag.put("payload", this.payload.toTag());
        }
        return tag;
    }

    public static RocketDesign fromTag(CompoundTag tag) {
        return fromTag(tag, 0);
    }

    private static RocketDesign fromTag(CompoundTag tag, int depth) {
        Builder b = new Builder();
        if (tag.contains("palette")) {
            int[] colours = tag.getIntArray("palette");
            List<Chemical> list = new ArrayList<>();
            for (int value : colours) {
                list.add(Chemical.byOrdinal(value));
            }
            b.palette(list);
        }
        b.shape(ShellShape.byId(tag.getString("shape")));
        b.caliber(tag.contains("caliber") ? tag.getFloat("caliber") : 1.0F);
        b.fuse(tag.contains("fuse") ? tag.getInt("fuse") : -1);
        b.modules(EffectModule.fromMask(tag.getInt("modules")));
        b.name(tag.getString("name"));
        if (depth < MAX_PAYLOAD_DEPTH && tag.contains("payload")) {
            // Stop descending before reading a third design. Checking the decoded child afterwards
            // is too late to protect against maliciously deep NBT.
            b.payload(fromTag(tag.getCompound("payload"), depth + 1));
        }
        return b.build();
    }

    /** Reads a design off a rocket stack, or {@code null} when the stack is not ours. */
    @Nullable
    public static RocketDesign fromStack(ItemStack stack) {
        if (stack.isEmpty() || !stack.hasTag()) {
            return null;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_ROOT)) {
            return null;
        }
        try {
            return fromTag(tag.getCompound(TAG_ROOT));
        } catch (Exception e) {
            return null;
        }
    }

    public void writeTo(ItemStack stack) {
        stack.getOrCreateTag().put(TAG_ROOT, this.toTag());
    }

    public ItemStack createStack(net.minecraft.world.item.Item item, int count) {
        ItemStack stack = new ItemStack(item, count);
        writeTo(stack);
        if (!this.name.isEmpty()) {
            stack.setHoverName(this.displayName());
        }
        return stack;
    }

    // ------------------------------------------------------------------ identity

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RocketDesign other)) {
            return false;
        }
        return this.cachedHash == other.cachedHash
                && Float.compare(this.caliber, other.caliber) == 0
                && this.fuse == other.fuse
                && this.shape == other.shape
                && this.palette.equals(other.palette)
                && this.modules.equals(other.modules)
                && this.name.equals(other.name)
                && Objects.equals(this.payload, other.payload);
    }

    @Override
    public int hashCode() {
        return this.cachedHash;
    }

    @Override
    public String toString() {
        return "RocketDesign[" + this.palette + " " + this.shape + " cal=" + this.caliber
                + " fuse=" + this.fuse + " modules=" + this.modules + "]";
    }
}
