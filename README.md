# ChessKraft

Private chess coach. Offline-first, no account, no ads, no tracking, and nothing
to grant.

- **Engine:** own 0x88 engine (PVS + quiescence + transposition table + null-move
  + late move reductions, Texel-tuned piece-square tables). Behind a pluggable
  `Engine` interface, so a stronger engine can drop in later without a rewrite.
- **Opponents:** four named bots — Bertie, Wren, Ines, Halvard — whose taglines
  are true of what each one actually searches.
- **Teaching:** every move is graded by the engine after the game, with a
  per-move evaluation, a verdict and an accuracy figure, and one plain sentence
  about any move that cost something. In-game, it names the opening and says what
  a costly move actually gave away.
- **Review:** a board you can step through move by move, with the move you played
  arrowed and the engine's better move ringed.
- **Board:** single Canvas, tap or drag, felt squares, legal-move dots,
  last-move arrow, Cburnett art, coordinates, 64-square TalkBack overlay,
  legal check / mate / stalemate in plain words, unlimited undo including the
  computer's reply.
- **Play:** two colours or random, no clock or 5 / 10 / 15 minutes, draw offers,
  flip the board, hints, resignation.
- **Record:** finished games are kept on the device and shown; an unfinished game
  survives a force-stop and a rotation.
- **Foundation:** built from `kraft-foundation/templates/android-app`, consumes
  `com.kraft:kraft-ui` + `com.kraft:kraft-core`, gated by `kraft-lint` and by
  tests in CI.

## Permissions

The app's own manifest declares none, and the built APK declares exactly one
entry: `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, the signature-level permission
AndroidX merges in so an app's own non-exported receivers are safe. It prompts for
nothing and grants nothing to anyone else.

This is checked, not promised. `PermissionManifestTest` fails if the source
manifest gains a permission or the merged manifest gains anything beyond that one
entry, and CI reads the built APK with `aapt2` and fails on anything unexpected.
The same claims are stated in the app's own About screen, so they have to be true
in two places.

## Status

v0.1.0. The engine is perft-green and the app is complete against
`docs/V1-SPEC.md`. 280 unit tests and 21 instrumented tests, kraft-lint clean
across 103 files.
No puzzles: they were deliberately left out of v1.

## Credits

Piece art by Colin M.L. Burnett (Cburnett), used under the 3-clause BSD licence —
see NOTICE, and the About screen inside the app. Everything else here is written
from scratch for this project.

MIT licensed. See LICENSE.