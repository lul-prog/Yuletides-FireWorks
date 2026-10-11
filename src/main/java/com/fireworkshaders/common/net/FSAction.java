package com.fireworkshaders.common.net;

/**
 * Action ids for menu-button and packet driven edits.
 *
 * <p>Both GUIs are server-authoritative: the client sends "the player pressed Shape&nbsp;Next",
 * never "here is the new design". That means a modified client cannot inject arbitrary designs,
 * cannot spawn a 3000-star shell out of a hand-edited packet, and the ingredient cost is always
 * computed by the server from the same object the server will consume.
 *
 * <p>Free-form text (the rocket's name, the show's name) is the one thing that cannot be expressed
 * as an action id, so it travels as a string in a packet instead — and is sanitised on arrival.
 */
public final class FSAction {

    // ---- workstation -----------------------------------------------------------------
    public static final int WS_ASSEMBLE = 0;
    public static final int WS_COLOUR_CLEAR = 1;
    public static final int WS_SHAPE_NEXT = 2;
    public static final int WS_SHAPE_PREV = 3;
    public static final int WS_CALIBER_UP = 4;
    public static final int WS_CALIBER_DOWN = 5;
    public static final int WS_FUSE_UP = 6;
    public static final int WS_FUSE_DOWN = 7;
    public static final int WS_PRESET_NEXT = 8;
    public static final int WS_PRESET_PREV = 9;
    public static final int WS_PAYLOAD_CLEAR = 10;
    public static final int WS_PAYLOAD_FROM_SAVED = 11;
    public static final int WS_PAYLOAD_TOGGLE_SHELL = 12;
    /** Free text; travels in its own packet because an int cannot carry a name. */
    public static final int WS_SET_NAME = 13;
    public static final int WS_MEMORY_NEXT = 14;
    public static final int WS_MEMORY_PREV = 15;

    /** {@code WS_SAVE_BASE + slot} */
    public static final int WS_SAVE_BASE = 20;          // .. 20+8
    /** {@code WS_LOAD_BASE + slot} */
    public static final int WS_LOAD_BASE = 40;          // .. 40+8
    /** {@code WS_COLOUR_BASE + chemical ordinal} */
    public static final int WS_COLOUR_BASE = 60;        // .. 60 + Chemical.values().length
    /** {@code WS_MODULE_BASE + EffectModule ordinal} */
    public static final int WS_MODULE_BASE = 90;
    /** {@code WS_CUE_*} — pad programming, kept in the same namespace for convenience. */
    public static final int PAD_ADD_CUE = 0;
    public static final int PAD_REMOVE_CUE = 1;
    public static final int PAD_NEXT_CUE = 2;
    public static final int PAD_PREV_CUE = 3;
    public static final int PAD_CUE_LATER = 4;
    public static final int PAD_CUE_EARLIER = 5;
    public static final int PAD_CUE_TOGGLE = 6;
    public static final int PAD_LOAD_DEMO = 7;
    public static final int PAD_CLEAR = 8;
    public static final int PAD_LEAD_IN_UP = 9;
    public static final int PAD_LEAD_IN_DOWN = 10;
    public static final int PAD_ARM = 11;
    public static final int PAD_SAFE = 12;
    public static final int PAD_GO = 13;

    // ---- remote igniter ---------------------------------------------------------------
    public static final int IGNITER_ARM = 0;
    public static final int IGNITER_GO = 1;
    public static final int IGNITER_SAFE = 2;
    public static final int IGNITER_TEST = 3;
    public static final int IGNITER_UNLINK_ALL = 4;
    public static final int IGNITER_CYCLE_MODE = 5;

    private FSAction() {}
}
