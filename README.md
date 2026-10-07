# ChessKraft

Private chess coach. Offline-first, zero permissions, no account, no ads, no tracking.

- **Engine:** own 0x88 engine (PVS + quiescence + TT + null-move + LMR, Texel-tuned PST).
  Pluggable `Engine` interface — Stockfish can drop in later as the Pro slot without a rewrite.
- **UX:** one dark board, legal dots, plain-English check/mate/stalemate, unlimited undo vs AI.
- **Foundation:** built from `kraft-foundation/templates/android-app`, consumes
  `com.kraft:kraft-ui` + `com.kraft:kraft-core`, gated by `kraft-lint` in CI.
- **Status:** v0.1.0 scaffold. Board + engine land next, perft-green before any search work.

## Permissions

None. The manifest declares zero `uses-permission`. If one ever appears, this line is false
and a test must fail.

## Credits

Piece art by Colin M.L. Burnett (Cburnett), BSD-licensed — see NOTICE.
