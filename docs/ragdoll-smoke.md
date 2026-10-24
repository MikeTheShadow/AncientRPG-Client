# Bounded corpse ragdoll — 0.5.0-dev

The approved cosmetic ragdoll drives the six existing skinned parts (torso, head,
arms, legs) with angular velocities, gravity, damped joint limits and coarse
sphere/AABB terrain contacts. It retains skin layers and visible equipment. The
corpse anchor, interaction volume, claim windows and ownership stay server owned.
Only F / your Interact binding opens loot; mouse clicks do not.

At most six visible bodies simulate. Each gets at most four 1/60-second substeps
and four constraint iterations per render frame. Simulation ends by the two-second
window; a finite terrain-clearance pass freezes a face-up rest on the next render
frame. Settled bodies have no continuing integration or collision queries. Extra
bodies, late arrivals and frame stalls use the bounded animated/resting fallback.
Body displacement stays anchored within the interaction footprint. Corpses at an
edge remain anchored; they do not fall into another gameplay location.

## Real-client verification

Packaged Minecraft26.2/Fabric on Linux verified native death with the player's skin
and equipped sword, independent limb motion, face-up rest, safe respawn, preserved
third-person camera and F opening the authoritative corpse menu after respawn.
Synthetic client-only fixtures also displayed paired held weapons and multiple
vanilla default skins, slab/stair/wall/edge contacts, late arrival at age3.5s and an
injected300ms frame stall. No renderer/mixin exceptions occurred.

See [motion video](images/ragdoll-six.mp4), [motion frame](images/ragdoll-motion.png),
[native rest](images/ragdoll-native-rest.png), [loot after respawn](images/ragdoll-native-loot.png),
[stairs](images/ragdoll-stairs.png), [wall](images/ragdoll-wall.png),
[edge](images/ragdoll-edge.png) and [late arrival](images/ragdoll-late.png).

The fixture bodies are cosmetic test entities, not real remote users or server
loot. They measure presentation only. The native death/respawn/loot check used the
actual local server. Temporary probe code/JARs were excluded from deliverables.

## Measured cost

Five-second, warmed samples in an isolated1200×800 window (Xvfb1280×900, Java25,
Mesa OpenGL4.6, render distance4, configured maxFPS120). This is one host and scene,
not a hardware-independent performance guarantee. Simulation time directly measures
the bounded batch, including terrain queries, during its active window. Full frame
time also includes ordinary body rendering and the rest of Minecraft.

| Visible bodies | Peak simulating | Median physics/frame | P95 physics/frame | Median whole frame |
| --- | --- | --- | --- | --- |
| 0 | 0 | 0 ms | 0 ms | 10.409 ms |
| 6 | 6 | 0.096 ms | 0.125 ms | 12.275 ms |
| 20 | 6 | 0.097 ms | 0.167 ms | 17.377 ms |

All samples recorded zero collision queries after settling. Late arrival and the
injected stall recorded zero physics queries throughout. The first cold warm-up
peaked at1.364ms of batch simulation; steady samples above exclude warm-up. Frame
stalls select fallback rather than spending unbounded catch-up work. Rendering
additional frozen bodies still costs more even though they have no physics work.
Raw [zero](performance/ragdoll-zero.csv), [six](performance/ragdoll-six.csv) and
[twenty](performance/ragdoll-twenty.csv) samples are included.

Both Gradle builds/format checks, registry probe and packaging passed. Automated
checks cover seeded independent motion, joint attachment, bounded work, duplicate
registration, exact duration, invalid terrain, terrain contacts and frozen-state
behavior, alongside existing authoritative gameplay/input tests.

Still unverified: two authenticated real-client observers, external account skins,
Windows, armor variants unavailable in this slice and broad hardware/terrain
coverage. Coarse collision is a presentation approximation, not full rigid-body
physics or a guarantee against every mesh corner clipping complex geometry.
