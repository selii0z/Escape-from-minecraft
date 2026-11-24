# Escape from Minecraft — V0.8.1 raid regression checklist

Target: NeoForge 1.21.1, solo and duo, both Black Forest and Industrial Zone.

## Preflight
- Back up world and player data.
- Install the JAR from the latest successful GitHub Actions run.
- Confirm no mod or gamerule overrides vanilla death drops (keepInventory must be false).
- Use disposable test equipment; do not test with valuable inventories.

## Solo
- [ ] Enter with known inventory; extract; verify exactly one copy of each item.
- [ ] Die in raid; respawn outside and no active raid token.
- [ ] Start another raid after death without stale return-data lock.
- [ ] Disconnect during raid; reconnect; check return to safe location.
- [ ] Restart server during raid; reconnect; old crates and corpses inaccessible.
- [ ] Extract then restart raid; old enemies, ground drops, and crates not reused.

## Duo
- [ ] First player extracts; second still loots and extracts.
- [ ] First player dies; survivor recovers dropped equipment once, without duplication.
- [ ] First disconnects; survivor continues.
- [ ] Both disconnect; session expires and old loot cannot be accessed.
- [ ] Both die; no leftover session remains.
- [ ] New duo raid works after interrupted session.

## Both maps
- [ ] Relay cannot be activated by nonparticipant.
- [ ] Exit and fallback teleport work.
- [ ] Temporary entities removed when last participant leaves.
- [ ] Restart does not restore old session ID.

## Known limitations before release
- Player death loot uses standard Minecraft drops, not a dedicated player corpse.
- Disconnect currently does not confiscate items held in the player's saved inventory. Define a raid-loss policy before claiming extraction-only loot persistence.
- Server restart intentionally invalidates any in-progress session.
- Successful Gradle compilation does not verify gameplay or full modpack compatibility.
