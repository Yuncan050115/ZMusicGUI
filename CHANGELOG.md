# Changelog

Release notes for the plugin jar. Tags match the versions below.

## v3.1.0 — 2026-07-06

- Standalone Kotlin implementation: the ZMusic CE dependency is gone entirely.
  Lyrics are read straight from `MusicPlayer.getState`, so PlaceholderAPI is no
  longer required either.
- Requires Paper/Purpur 1.21.4+; CalVer server builds (26.x) are supported.

## v3.0.3 — 2026-07-03

- Fixed `playtoall` not accepting commands.

## v3.0.2 — 2026-07-01

- Added lands/dominion plugin support.

## v3.0.1 — 2026-06-28

- Removed the per-player `MusicPlayer` BossBar; boss bar rendering is now owned by
  `LyricDisplayManager` alone, which fixes duplicate bars stacking on screen.

## v3.0.0 — 2026-06-28

- Removed the account-login module and the QQ source. Three free sources only.

## v2.5.0 — 2026-06

- Rewritten in Kotlin as an independent implementation; Kugou/Kuwo VIP login dropped.
