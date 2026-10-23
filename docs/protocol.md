# AncientRPG Protocol v2

All body integers are big-endian fixed-width signed values except the unsigned
one-byte release length and response status/boolean. Channel identifiers supply the
message type; there is no type byte inside the body. Unknown AncientRPG channels
are rejected by the server transport adapter.

## Compatibility

`ancientrpg:compatibility`: Minecraft protocol (int32), wire version (int32),
release length (uint8), release bytes (ASCII), manifest SHA-256 (32 raw bytes).
Release identifiers contain 1–64 characters matching `[A-Za-z0-9][A-Za-z0-9._+-]*`.
Versions must be nonnegative for Minecraft and positive for the custom protocol.
No trailing bytes are allowed. The transport body cap is 1 KiB; valid bodies use
42–105 bytes. The same codec may represent the server's expected identity and the
client's response; the server accepts one response during configuration.

The adapter must authenticate the account first, start the 15-second monotonic
deadline when requesting compatibility, and send no custom world content before
acceptance. A matching declaration is not client attestation. Exact identities
must match; a session object is never a substitute for account authentication.

## Interaction and result

`ancientrpg:interaction`: nonnegative sequence (int64), x/y/z (three int32 values).
Exactly 20 bytes; transport cap 256 bytes. No desired result or damage value is sent.
Sequence numbers increase per connection; dropped queue-full requests consume their
sequence. A rate-limited request may be retried with the same sequence if it was never
accepted. Reconnection starts a new session.

`ancientrpg:interaction_result`: sequence (int64), status (uint8: 0 applied, 1 denied),
active (uint8: 0 or 1). Exactly 10 bytes; transport cap 1 KiB. Denied results always
use `active=0` and do not disclose the target's current state. Authoritative world
updates synchronize admitted observers separately.

Decoders reject malformed, truncated, trailing, oversized, invalid-enum, and
negative-sequence inputs. Adapters must check body bounds before allocating arrays.

## Golden fixtures

These deliberately use test Minecraft protocol `1`, not the production protocol:

- Compatibility `(1,1,"test",zero hash)`: `00000001000000010474657374` followed by 64 zero hex digits.
- Interaction `(1,-1,2,3)`: `0000000000000001ffffffff0000000200000003`.
- Applied active result `(1,APPLIED,true)`: `00000000000000010001`.
- Denied result `(1,DENIED,false)`: `00000000000000010100`.

## Public content manifest

SHA-256 input: ASCII `AncientRPG-content-v1\n`, int32 entry count, then entries sorted
by ASCII path. Each entry is int32 path-byte length, ASCII path bytes, int32 content
length, and the exact content bytes. All lengths are big-endian. Paths are relative,
lowercase, slash-separated, and must not contain `..`. No absolute filesystem paths
or platform timestamps are hashed. Hash output is lowercase hex; wire representation
is the 32 raw bytes.

The future content build must supply an explicit allowlist of public registry/state
definitions and client assets. The hash utility does not discover files or include
private content. The implemented content allowlist and runtime mappings are described
below; an empty manifest hash is only a test fixture, never a release fingerprint.

## Initial Setup Content Bundle

The implemented allowlist is `PublicContent`: public blockstates, block/item models,
item definition, English names, and `ancientrpg/content.json`. `registry/runtime.txt`
is synthesized from the actual block ID, active/inactive state IDs, and item ID in
that order with LF-terminated ASCII lines. The separate `ancientrpg/release.txt`
resource supplies the exact release identifier. Assets are shared through the nested
protocol JAR so both sides hash identical bytes. The client always reports its own
runtime identity, never an echoed server fingerprint.

For the current pinned data baseline, Minecraft protocol is 776, block ID 1196,
active/inactive state IDs 32366/32367, and item ID 1537. The fingerprint also includes
all allowlisted asset bytes, so these numeric values alone cannot establish a match.

Minestom's outer packet decoder first bounds and decodes a Minecraft custom payload.
The server adapter then applies the stricter AncientRPG body limits before contract
decoding or gameplay queueing. The Fabric codec checks its readable body length
before making a body copy. Unknown AncientRPG channels and messages in the wrong
phase disconnect; other Minecraft/Fabric control traffic retains upstream handling.

Interactions are applied in the authoritative instance tick, including requests
from players in adjacent chunks. A request targets only the demo block; its current
reach, line of sight, mode, life state, and instance are validated at application
time. The demo does not accept client-authored inventory or resulting block state.

## Sword skills (wire v3, release 0.3.0-dev)

The original switch payloads retain their layouts. Skill-use, unlock and switch
requests share one monotonically increasing sequence per connection, one token
bucket and one bounded intent queue. All values below are big-endian. Enum/flag
bytes are unsigned. No trailing bytes are accepted. Maximum server body: 1 KiB.

Skill IDs: 0 Quick Cut, 1 Sweeping Arc, 2 Piercing Thrust, 3 Twin Fang,
4 Crescent Finish. Unknown IDs are invalid; these are the only five skills.

| Channel | Body |
| --- | --- |
| `ancientrpg:skill_use` | sequence int64, skill uint8 (exactly 9 bytes) |
| `ancientrpg:skill_unlock` | sequence int64, skill uint8 (exactly 9 bytes) |
| `ancientrpg:skill_state` | sequence int64, reason uint8, server tick int64, learned mask uint8, points uint8, five cooldown-end int64 values in skill-ID order, action-present uint8, optional action, queued-present uint8, optional queued sequence int64 and skill uint8 (61, 155 or 164 bytes) |
| `ancientrpg:skill_event` | server tick int64, actor entity ID int32, phase uint8, action, hit count uint8, zero to three hits (108–144 bytes) |

Action (94 bytes): globally increasing instance action ID int64, skill uint8,
start tick int64, captured normalized aim x/y/z as three float64 values. This aim
is server-observed presentation state, never input supplied by the skill request.
Then six float64 coordinates: origin x/y/z and current authoritative feet x/y/z;
locked horizontal yaw float32; flags uint8 (bit 0 blocked, bit 1 disabled; all
other bits invalid). Coordinates must be finite with absolute value <=2e9, yaw
finite. Queued state is owner-only, requires an action and nonnegative sequence.
Hit (12 bytes): target entity ID int32, actual damage int32, remaining health int32.
Damage is positive and <=24; health is 0–100. Duplicate target IDs within a strike
are invalid. Only HIT events can contain hits. All ticks/IDs are nonnegative;
state sequence alone permits -1 for unsolicited snapshots. Aim must be finite and
unit length within 1e-6. The learned mask is five bits and must contain the root;
points are 0–4. All five cooldown deadlines are nonnegative. Absence flag is 0,
presence is 1; no other value is valid. Action start cannot follow snapshot/event
time. Decoders reject malformed bounds before allocating variable collections.

Reason ordinals: 0 OK, 1 LOCKED, 2 PREREQUISITES, 3 POINTS, 4 ALREADY_LEARNED,
5 BUSY, 6 COOLDOWN, 7 INELIGIBLE, 8 QUEUE_PRESSURE, 9 DUPLICATE, 10 QUEUED, 11 BUFFER_FULL.
Phase ordinals: 0 START, 1 HIT, 2 END, 3 CANCEL, 4 SNAPSHOT, 5 REMOVE.

Use/unlock responses carry the request sequence and current authoritative state.
Periodic owner state is sent every five instance ticks. While an action is active,
currently tracking admitted observers and its owner receive tick snapshots;
leaving tracking sends REMOVE. Terminal events clear an action. Events reveal
only the active action, never a future encounter schedule. Clients advance visual
interpolation by at most one tick beyond the latest server sample; no displayed
cooldown or animation can authorize an action.

Queue-full requests consume their sequence. Rate-limited requests do not consume
a sequence until accepted, matching existing session rules. Queue/rate/replay
skill denials are coalesced in one per-session response slot, drained on the owning
instance tick; overload cannot create another unbounded queue. Clients time out
unconfirmed unlock UI after two seconds and may retry with a fresh sequence.
Disconnect clears requests, rejection slots and actions. Sustained abuse and
malformed/out-of-phase payloads retain existing disconnect behavior.

Request golden fixture `(sequence=1, Crescent Finish)`:
`000000000000000104` on either request channel. Channel determines use vs unlock.
A fresh state is `ffffffffffffffff00`, eight zero tick bytes, `0104`, forty zero
cooldown bytes, then `0000` (absent action and queued successor). Tests verify these literal fixtures,
all state/event round trips and malformed input handling using both Java buffers
and actual Minecraft/Fabric buffer adapters.

The manifest now additionally includes the training-sword item/model/texture,
updated public names/content metadata, and `ancientrpg:training_sword=1538` in
runtime mappings. Existing block IDs and demo item ID remain unchanged. The manifest also includes `ancientrpg/sword-motion.json` and canonical
`animation/runtime.txt` keyframes/motion samples. Clients
using older wire versions or the old manifest/release are rejected before gameplay.


Accepted skills lock movement and equipment through recovery. Camera look stays
free; current carry uses captured yaw and strikes use captured 3D aim. The server
moves before contact checks, sweeps the full body, steps ordinary stairs/slabs,
and stops carry at walls or unsupported edges. Lost support/equipment disables
remaining carry/hits without shortening recovery. Forced relocation, death,
instance/mode changes and disconnect cancel. The client predicts at most one tick
of accepted motion with local collision checks; server samples always win.
A single end-of-action position baseline requires the normal teleport acknowledgement;
there are no per-tick self teleports.

During the final three ticks, one fresh skill press may receive QUEUED. The first
wins; later presses receive BUFFER_FULL. At recovery end, the successor is
revalidated immediately with fresh observed aim; it receives OK or a correlated
denial and never waits for a cooldown. END for the previous action precedes START
for the successor. Completed IDs cannot resurrect client control locks; REMOVE
only ends observation and allows the same active action to be observed again.
Adjacent poses blend over two render ticks. Holding a key does not repeat casts.
