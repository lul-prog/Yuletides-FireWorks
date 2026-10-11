# Yuletide Fireworks

Advanced, physically-motivated fireworks and pyrotechnics for **Minecraft 1.20.1 / Forge**, built
from the ground up for **shader packs (Oculus/Iris + Rubidium/Embeddium)** and **dynamic lighting**.

Not "a firework that makes a bigger bang": every shell is a small simulation. The break geometry is
generated per explosion from a design object, the sparks carry gravity, drag and wind, the smoke
lingers and dissipates over seconds, the sound arrives *after* the light with air absorption and
echoes, and the camera reacts to the pressure wave when it reaches you, not when the shell breaks.

---

## What is in the box

### 1. Five pre-configured premium shells

Each is a real design assembled from the same modules players have access to, so the crafting system
can *explain* them:

| Shell | What it does |
| --- | --- |
| **Stardust Willow** | Golden weeping-willow canopy: stars reach the top of the break, hang, then droop for eight seconds. Trails + drape + lingering embers. |
| **Meteor Shower** | Multi-break: the shell breaks, then splits into **8 sub-projectiles** that arc outward and burst again, each one lower than the last. |
| **Sonic Boom** | Very fast ascent (2.4× lift) with a **shrieking** trail, a blinding white core flash, an expanding **pressure shell + ground ring**, and the heavy camera shake that goes with it. |
| **Dragon's Breath** | A ring of fire standing on the terrain, throwing embers that burn for seconds and **light the ground below** with real light pulses. |
| **Prismatic Cascade** | A waterfall that sweeps the **entire RGB spectrum** as it falls — violet at the top, crimson at the bottom — using a per-particle hue ramp so no two streamers are in phase. |

Plus three starter shells (Red Peony, Golden Chrysanthemum, Emerald Ring) for players learning the
workstation.

### 2. Pyrotechnic Workstation

A custom block with a custom GUI. It is not a recipe grid — it is a *design bench*:

* **Reagent slots (4)** — real chemistry. Strontium nitrate burns 606 nm crimson, barium nitrate
  524 nm green, copper(I) chloride 455 nm azure, sodium nitrate washes everything out amber,
  magnesium and aluminium are the brighteners, potassium perchlorate is the oxidiser. The colour a
  shell produces is the emission colour of the salts you put in it, and the palette of your design
  is literally the salts in the hopper.
* **Propellant slots (2)** + paper — gunpowder is consumed in proportion to the shell's mass, which
  is derived from caliber and star count.
* **Shape modules** — 13 break geometries: peony, chrysanthemum, sphere, willow, ring, double ring,
  cascade, palm, crossette, saturn, heart, star, comet.
* **Modifiers** — 14 of them, each a priced change to the simulation: trails, crackle, strobe, heavy
  smoke, whistler, shriek, flash core, shockwave, multi-break, willow drape, spectrum, ground light,
  lingering embers, metallic dust.
* **Caliber and fuse** — 0.6" to 3.0", 0.6 s to 4 s of lift, with star count, burn time, light
  output, audio weight and particle budget all derived from them.
* **Name it** — a 40-character name that travels with the shell in item NBT, and **nine memory
  slots** so a pyrotechnician can keep a repertoire.

The GUI updates live: a real preview of the break geometry, the exact cost of the shell you have
built, and a manifest of anything you are still missing.

### 3. Show Director

* **Remote Igniter** — links up to 16 launch pads. Right-click a pad to link, sneak-right-click to
  unlink, right-click the air to run the current mode (FIRE / ARM / SAFE / TEST), sneak-right-click
  the air to cycle mode.
* **Launch Pad** — nine mortar tubes in a 3×3, fanned so a salvo from one pad spreads instead of
  stacking into a column. Load a designed shell by right-clicking with it in hand; empty-handed
  right-click opens the **show programmer**.
* **The cue timeline** — each cue binds a tube to an absolute offset from T-0, so two pads fired by
  the same igniter press stay frame-locked for the whole show regardless of latency. Lead-in is
  settable, cues can be disabled and re-timed, and a demo program ships for testing.
* A countdown HUD appears when you are within 48 blocks of an armed pad.

### 4. Physics and sensory modelling

* **Ballistics** — shells have axial lift with drag, gravity and a fuse; sub-shells are ballistic and
  burst on their own timers; every burst is a seeded point cloud, so two "rings" are never identical.
* **Wind and air** — sparks decelerate against drag, embers drift, smoke rises and spreads.
* **Smoke** — lingering, additive-free (it is lit by the world, not by itself), dissipating over
  seconds, tinted by the shell's own palette.
* **Sound** — a layered mixer: the high crack arrives first, the body two ticks later, sub-bass only
  for shells that deserve it, then 2–4 echoes at increasing spacing and falling pitch, then crackle
  pops. Everything is delayed by `distance / 343 m·s⁻¹` when the speed-of-sound option is on, and
  every layer is **positioned** at the break, so a show plays in stereo around you.
* **Camera** — shake that carries a radial bias along the blast→camera direction and arrives *with
  the sound*, plus a whiteout whose intensity follows `1/d^1.6` scaled by how close the burst was to
  your view axis. Deliberately applied through `ComputeCameraAngles` / `ComputeFov`, so it composes
  with third-person cameras and with shader packs instead of fighting them.

---

## The shader decision (read this before touching rendering)

The single most important engineering choice in this mod: **it never writes a custom GLSL program,
and never touches a `RenderStateShard`.**

Shader packs work by replacing the shader programs vanilla already uses. A hand-rolled program is not
one of those: no pack can apply its bloom, tonemapping, exposure or fog to it, which would make the
one element that is supposed to glow the only flat-looking thing on screen. So every particle and
every shell renders through **`GameRenderer#getParticleShader` / `RenderType.entityTranslucentEmissive`**
against the vanilla particle atlas, and the emissive look is assembled from three things a pack
*does* see:

1. a **fully-bright lightmap** (`0xF000F0`) so no world lighting is multiplied in,
2. **additive blending** so overlapping sparks sum instead of overwriting,
3. **bright, soft, white-cored textures** so additive stacking pushes the core past the pack's bloom
   threshold.

Embers are the deliberate exception: they use alpha blending, because an ember cloud hundreds strong
stacks to pure white under additive blending and every coal becomes the same flat blob.

The mod also ships no mixins and no coremods. The optional sub-projectile, shockwave ring and
multi-break behaviour are entities and particles, not engine patches.

---

## Optional: drop in real recordings

The mod ships without `.ogg` files on purpose — a convincing firework recording is a licence and a
microphone, not a line of code. It is fully playable without them: the mixer layers vanilla's own
blast events with distance, air absorption and echoes, which is where most of the perceived quality
comes from anyway.

To use your own audio, see `src/main/resources/assets/fireworkshaders/sounds.json.template`: drop
files into `sounds/fireworks/`, rename the template to `sounds.json`, and the mixer switches over
automatically. The probe is a single resource lookup, cached for the session.

---

## Commands

All under `/fireworkshaders` (op level 2):

```
/fireworkshaders test <preset>    fire a named shell above you
/fireworkshaders give <preset>    spawn a pre-built shell item
/fireworkshaders show arm|go|safe|status    drive the nearest pad
/fireworkshaders info             diagnostics: particle budget, audio probe, adaptive budget
/fireworkshaders modes            list every shape and modifier
```

---

## Building

```
./gradlew build            # mod jar -> build/libs
./gradlew runClient        # dev client
./gradlew runClient -Pshaders=true   # dev client with Embeddium + Oculus + Chloride runtime-only
```

Requires JDK 17. The shader mods are **runtime-only** test dependencies, gated behind `-Pshaders=true`;
they are never compiled against, and the mod has no hard dependencies at all.

---

## Repository layout

```
src/main/java/com/fireworkshaders/
  FireworkShaders.java          mod entry: registries, network, config, client bootstrap
  FSConfig.java                 COMMON (broadcast/particle caps/dynamic light) + CLIENT (sensory) config
  FSCommands.java               /fireworkshaders
  pyro/                         Forge-free domain model — no Minecraft imports, unit-testable
    Chemical, EffectModule, ShellShape, RocketDesign, BurstPatterns, BurstPlanner, BurstPlan,
    ShellPresets, PyroMath, PyroFlags
  common/
    entity/                     rocket + ballistic sub-shell entities
    burst/                      BurstExecution (the single server-side break path), PyroLightPulse
    net/                        six packets, no more
    block/                      the workstation and the pad, block + block entity
    item/                       chemicals, designed shells, the Remote Igniter
    menu/                       menus + the server-authoritative editors behind them
    show/                       ShowProgram: cues, absolute ticks, NBT
    fx/FxBus.java               the only common↔client seam (audio/camera hooks, no-op on a server)
  client/
    FSClientSetup.java          all client wiring in one place
    particle/                   the particle zoo (spark, glitter, ember, smoke, flash, shockwave, dust)
    render/                     render types + the shell renderer
    fx/                         ClientBurst (break execution), ScreenFx (shake/flash/FOV)
    audio/                      the layered mixer
    state/                      client mirrors of server-owned state
    gui/                        the two screens, the preview renderer, the HUD
src/main/resources/assets/fireworkshaders/
  textures/ block/ entity/ gui/ item/ particle/
  particles/ blockstates/ models/ lang/
src/main/resources/data/fireworkshaders/
  recipes/ loot_tables/
tools/
  generate_assets.py            regenerates every texture and JSON in this mod (Pillow)
  audit_sources.py              static cross-file audit: brackets, symbols, arity, inheritance
  audit_resources.py            cross-checks registries against shipped assets and lang keys
  check_vanilla.py              verifies referenced vanilla/Forge members exist in 1.20.1
  check_overrides.py            verifies every @Override against the real supertype chain
```

### Why the tools exist

There is no way to compile against Minecraft/Forge in a sandbox without the game jars, and a mod of
this size written against an API from memory *will* contain mismatches. So the `tools/` scripts do
the job of a compiler for the classes of mistake that actually happen:

```bash
python3 tools/generate_assets.py     # rebuild textures + models + lang + recipes
python3 tools/audit_sources.py       # 0 bracket problems / 0 dangling refs / 0 arity mismatches
python3 tools/audit_resources.py     # 0 problems (registries vs assets vs lang)
python3 tools/check_vanilla.py       # every referenced vanilla member exists in 1.20.1
python3 tools/check_overrides.py     # every @Override exists in the real supertype chain
```

`check_vanilla.py` and `check_overrides.py` fetch decompiled 1.20.1 sources over the GitHub API and
cache them in `/tmp/mcref2`, so they need network access the first time they run.

---

## Design notes

**Server-authoritative editing.** Both GUIs send *actions* ("the player pressed Shape Next"), never
designs: `FSAction` ids for discrete edits, and a 64-character string for the one thing an int cannot
carry, the name. A modified client cannot invent a 3000-star shell, and the cost is always computed
by the server from the same object the server consumes.

**Bursts are generated, not transmitted.** One `S2CBurstPacket` per break carries the design and a
seed; the client regenerates the identical point cloud and clamps it with its *own* particle budget,
so a player who turns their settings down gets a smaller burst instead of a stutter, and the network
cost of a finale is one packet per shell.

**Nothing degrades into a freeze.** A global soft cap (14 000 by default) is enforced inside the
particle providers: over budget, a provider returns `null` and the burst thins out gracefully.
Priority particles (flash, shockwave) bypass the cap so a break never loses its core.

**Light is real light.** Ground-glow and break illumination place actual `minecraft:light` blocks
(full brightness 15) at scattered offsets around the break and remove them on a tick task, so
dynamic-lighting mods and shader packs both see genuine world illumination rather than a fake decal.

---

## Licence and credits

Mod code: see the repository licence. Minecraft is a trademark of Mojang Studios; this project is not
affiliated with or endorsed by Mojang or Microsoft.
