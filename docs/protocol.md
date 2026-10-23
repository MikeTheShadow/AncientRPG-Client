# AncientRPG Protocol v1

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
