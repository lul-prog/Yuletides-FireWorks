package com.fireworkshaders.common.show;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A show script: which rocket in which slot, at what absolute offset from the start of the show.
 *
 * <p>Offsets are absolute ticks from T-0 (not "N ticks after the previous cue"), because that is
 * what makes a multi-pad show work: every pad is told the same absolute start tick from the server
 * clock, so two pads a kilometre apart fire their cues within one tick of each other even if the
 * packets arrive at different times. Full fireworks displays are mostly about <em>timing</em>, and
 * a show that smears by half a second per pad looks amateur.
 */
public final class ShowProgram {

    /** One rocket, at one time. */
    public record Cue(int slot, int tick, boolean enabled) implements Comparable<Cue> {
        @Override
        public int compareTo(Cue other) {
            int byTick = Integer.compare(this.tick, other.tick);
            return byTick != 0 ? byTick : Integer.compare(this.slot, other.slot);
        }
    }

    public static final int MAX_CUES = 64;
    public static final int MAX_TICK = 20 * 300;   // five minutes
    public static final int DEFAULT_LEAD_IN = 100; // five seconds of T-minus

    private final List<Cue> cues = new ArrayList<>();
    private int leadInTicks = DEFAULT_LEAD_IN;
    private String name = "Show";

    public List<Cue> cues() {
        return List.copyOf(this.cues);
    }

    public int leadInTicks() {
        return this.leadInTicks;
    }

    public void setLeadInTicks(int ticks) {
        this.leadInTicks = Math.max(20, Math.min(20 * 60, ticks));
    }

    public String name() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name == null ? "Show" : name.replace("\u00a7", "").trim();
        if (this.name.length() > 32) {
            this.name = this.name.substring(0, 32);
        }
    }

    /** Sorted, immutable view: the order cues actually fire in. */
    public List<Cue> timeline() {
        List<Cue> sorted = new ArrayList<>(this.cues);
        sorted.sort(Comparator.naturalOrder());
        return sorted;
    }

    public long enabledCount() {
        return this.cues.stream().filter(Cue::enabled).count();
    }

    public int durationTicks() {
        int max = 0;
        for (Cue cue : this.cues) {
            max = Math.max(max, cue.tick());
        }
        return max;
    }

    public void clear() {
        this.cues.clear();
    }

    public boolean add(int slot, int tick, boolean enabled) {
        if (this.cues.size() >= MAX_CUES) {
            return false;
        }
        this.cues.add(new Cue(Math.max(0, slot), clampTick(tick), enabled));
        return true;
    }

    public void remove(int index) {
        if (index >= 0 && index < this.cues.size()) {
            this.cues.remove(index);
        }
    }

    public void setTick(int index, int tick) {
        if (index >= 0 && index < this.cues.size()) {
            Cue cue = this.cues.get(index);
            this.cues.set(index, new Cue(cue.slot(), clampTick(tick), cue.enabled()));
        }
    }

    public void nudgeTick(int index, int delta) {
        if (index >= 0 && index < this.cues.size()) {
            setTick(index, this.cues.get(index).tick() + delta);
        }
    }

    public void toggle(int index) {
        if (index >= 0 && index < this.cues.size()) {
            Cue cue = this.cues.get(index);
            this.cues.set(index, new Cue(cue.slot(), cue.tick(), !cue.enabled()));
        }
    }

    public void setSlot(int index, int slot) {
        if (index >= 0 && index < this.cues.size()) {
            Cue cue = this.cues.get(index);
            this.cues.set(index, new Cue(Math.max(0, slot), cue.tick(), cue.enabled()));
        }
    }

    /** Cues that should fire at this elapsed tick, given the previous elapsed tick. */
    public List<Cue> due(int elapsedTicks, int previousElapsedTicks) {
        List<Cue> due = new ArrayList<>();
        for (Cue cue : this.cues) {
            if (!cue.enabled()) {
                continue;
            }
            if (cue.tick() > previousElapsedTicks && cue.tick() <= elapsedTicks) {
                due.add(cue);
            }
        }
        return due;
    }

    private static int clampTick(int tick) {
        return Math.max(0, Math.min(MAX_TICK, tick));
    }

    // ------------------------------------------------------------------ persistence

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", this.name);
        tag.putInt("LeadIn", this.leadInTicks);
        ListTag list = new ListTag();
        for (Cue cue : this.cues) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("Slot", cue.slot());
            entry.putInt("Tick", cue.tick());
            entry.putBoolean("On", cue.enabled());
            list.add(entry);
        }
        tag.put("Cues", list);
        return tag;
    }

    public static ShowProgram fromTag(CompoundTag tag) {
        ShowProgram program = new ShowProgram();
        program.name = tag.getString("Name");
        if (program.name.isEmpty()) {
            program.name = "Show";
        }
        program.leadInTicks = tag.contains("LeadIn") ? tag.getInt("LeadIn") : DEFAULT_LEAD_IN;
        ListTag list = tag.getList("Cues", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size() && i < MAX_CUES; i++) {
            CompoundTag entry = list.getCompound(i);
            program.cues.add(new Cue(entry.getInt("Slot"), clampTick(entry.getInt("Tick")),
                    !entry.contains("On") || entry.getBoolean("On")));
        }
        return program;
    }

    public static ShowProgram demo() {
        ShowProgram program = new ShowProgram();
        program.setName("Demo");
        program.add(0, 0, true);
        program.add(1, 30, true);
        program.add(2, 30, true);
        program.add(3, 70, true);
        program.add(4, 100, true);
        program.add(5, 100, true);
        program.add(6, 140, true);
        program.add(7, 170, true);
        program.add(8, 200, true);
        return program;
    }
}
