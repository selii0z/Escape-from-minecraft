# Black Forest regeneration (0.10.0 RC2)

The revised paths and trees are generated only in new chunks. Existing terrain does not update automatically.

## Before proceeding
- Finish all raids, including those of disconnected players. Return all players to the normal world.
- Stop Minecraft / the dedicated server completely.
- Download a full backup of the world before modifying any save files.
- Install the new mod on client and server before restarting.

The Black Forest covers block coordinates 12288 through 13187 on both axes. Its terrain, entities and POI files are isolated from the Industrial Zone, which starts at 16384. Only the four region coordinates (24,24), (24,25), (25,24), (25,25) are affected in the taczoperations:black_forest dimension. This removes all blocks inside those regions, including player/admin changes in the forest and unused border chunks. The main world, Industrial Zone and player data are not moved.

## Offline utility
Run Python 3 with the exact world folder (contains level.dat):

    python scripts/reset_black_forest.py "/path/to/world"

Preview the list. After checking the full world backup and stopping the server:

    python scripts/reset_black_forest.py "/path/to/world" --apply --server-stopped

The utility moves the selected files into a sibling backup directory and resets the forest preparation progress by moving its SavedData file. It does not delete files. The stop flag is a human confirmation, not a process-lock check.

On managed hosting without shell access: with server stopped, download the listed region files and preparation file, then remove those exact files via the host file manager. Never remove the entire dimension or playerdata.

Restart the server. ForestPreparation will regenerate forest chunks progressively. Existing industrial regions and player data are not touched. If necessary, stop the server and restore the original full world backup (safest rollback).
