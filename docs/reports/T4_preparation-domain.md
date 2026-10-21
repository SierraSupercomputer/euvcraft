# T4 preparation domain — draft

Separate preparation generation follows the immutable wafer lot, independently of substrate
purity. Old constructors and absent save fields default to generation one; explicit invalid
generations fail closed. G2 lots must contain pure wafers. Preparing is allowed only before
lithography, while processing preserves the generation. Appending wafers to prepared lots is
refused, and removing the final wafer resets preparation. Public projection includes generation
but continues to omit private rolls, flags, calibration and lot identity; network reconstruction
retains its untrusted client marker.

The new pure panel matcher accepts 2×2×3 and 3×3×5 shapes by dimensions, any lower-front
controller column and four horizontal facings, required panel above, one or two power ports,
and casing elsewhere. Existing matchers are unchanged.

Nemotron's interrupted draft is preserved at `ab603ab75a642b6bb480bdb98f2ce77356d9fb8c`.
Repeated provider timeouts prevented a completed worker result. The coordinator repaired the
missing five-argument constructor, enforced pure/nonempty prepared state, fixed shape value
comparison and wrote provenance, codec and exhaustive missing-member geometry checks.

The repaired source compiled. Its first unit run executed 764 tests with four failures:
an old four-field public-projection expectation, two old packet fixtures missing the new public
generation byte, and a translation supplied by the separate content branch. All nine new test
methods passed. The projection expectation is updated here; packet fixtures and shared language
will be reconciled in the combined content task. This is not final acceptance or a runtime result.
