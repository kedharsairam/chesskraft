# ChessKraft v1 — frozen spec

Locked 2026-10-07 with Kedhar. No Stockfish in v1. OwnEngine + pluggable slot.

## Non-negotiables

- Offline-first, zero permissions, no accounts, no ads, no tracking.
- Foundation-native: tokens/theme/motion/components from kraft-foundation, `KraftResult` on
  domain paths, `kraft-lint` green, 60fps on Realme RMX3998 (6GB), cold start → tappable board <1s.
- Never-fork: read FOSS for ideas, write all code from scratch. Perft counts are facts (test
  vectors); engine code is ours.
- Dark-only like sibling Kraft apps. Accent: tournament brass (`0xFFD8B45A`) — wood + brass
  clocks, unlike cyan/amber/blue-violet siblings.

## Engine v1 (pure Kotlin, `:engine`, no Android imports)

- Board: 0x88 `IntArray(128)`, packed-Int moves, copy-on-write apply/undo, Zobrist history stack.
- Rules: castling (rights + transit attacks), en passant (incl. pin), promotion (all 4),
  check/mate/stalemate, fifty-move, threefold (key history), insufficient material, FEN in/out.
  PGN moves-only later.
- Gate: perft depths 1–4 green before search work —
  startpos 20/400/8902/197281, Kiwipete 48/2039/97862/4085603, + Pos3/Pos4/Pos5/rook-mirror.
  Depth-4 startpos <2s on budget phone or movegen allocates too much.
- Search IN: negamax/PVS + alpha-beta + MVV-LVA + killers/history + captures-only quiescence
  + iterative deepening + hard time cap + mate-distance + draw adjudication.
  OUT: NNUE, opening book, tablebase, multi-thread, UCI protocol.
- Eval: Michniewski values (P100 N320 B330 R500 Q900 K20000) + one PST per piece + tapered king
  (midgame cornered / endgame centralize) + bishop-pair +10. No floats in search.
- Difficulties (names only, no Elo in UI):
  RELAXED depth1 + top-5/80cp + 15% random (never in check),
  CASUAL depth2 300–600ms + top-3/40cp + 5% blunder,
  SHARP depth3 1000–1500ms + best + 8cp jitter,
  TOUGH depth3+ fully tuned + ordering + 1500ms cap.
  Golden rule: never blunder mate-in-1 (prune from random pool).
- Battery: single background thread, deadline flag, time check every 1024 nodes, no pondering,
  `stop()` on pause. Caps: 200/600/1500/1500ms.
- Interface: `Engine.findBestMove(fen, limits): UciMove`. v2 StockfishEngine implements the same.

## App v1 (4 screens)

1. Home/New Game: Play + difficulty (Relaxed/Casual/Sharp/Tough) + White/Black/Random + flip toggle.
2. Game: Canvas board (tap-tap + drag), dots vs capture rings, last-move wash, check red+ring,
   coordinates always on, captured strip, plain-English status, toolbar Undo/Hint/New/Resign/Flip/Sound.
   No clocks. Unlimited round-trip undo vs AI. Resign confirms.
3. Game-Over sheet: plain words + reason + Rematch (swapped) / New / Review. No rating/XP/confetti.
4. Review (basic): move list + step back/forward + flip. No eval bar in v1.

Feel: Cburnett-style set, desaturated squares, 120–180ms slide + 80ms capture fade (ReduceMotion
gated), tick/thock/double-tick haptics + one wood tap (toggleable), illegal tap = silent nudge.
Accessibility: 64-square TalkBack ("e4, white knight"), turn + last-move announcements,
grayscale-distinguishable states, ≥48dp controls, 8dp miss tolerance.

## Quality gates (before any tag)

- perft 1–4 all 6 FENs, EP/castle/promo edge FENs, ViewModel tap/drag/undo/flip tests.
- `kraft-lint` green, `testDebugUnitTest` green, `lintDebug` green, release APK installs.
- Device: Realme primary (cold start, full game, rotation, offline cold start, font extremes),
  TalkBack linear swipe a8→h1, Accessibility Scanner, 0 FATAL at close.
- Zero-permission proof: manifest has no `uses-permission`; OS query test.

## v2 (reserved, not started)

Stockfish small-net as Master/Pro behind the same interface, ~200 CC0 mate-in-1/2/3 puzzles,
FEN in/out + moves-only PGN, optional casual clock.

## Never

Accounts, ratings, ads, online matchmaking, chat/social, premove-live, cloud engine,
tracking/analytics, theme picker (v1), eval graph (v1), clocks (v1).
