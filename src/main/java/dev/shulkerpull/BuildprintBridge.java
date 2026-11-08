package dev.shulkerpull;

import com.buildprint.api.BuildprintPlacements;

import java.util.HashMap;
import java.util.Map;

/**
 * The only class that touches BuildPrint. Uses only the public, stable API in com.buildprint.api,
 * and is only loaded when BuildPrint is installed.
 */
final class BuildprintBridge {
    private BuildprintBridge() {}

    /** Item id -> how many are still needed, for builds shown in this dimension that the player is close to. */
    static Map<String, Integer> stillNeeded(String dimension, double px, double py, double pz, double range) {
        Map<String, Integer> out = new HashMap<>();
        if (!BuildprintPlacements.isAvailable()) return out;
        for (int id : BuildprintPlacements.ids()) {
            if (!BuildprintPlacements.exists(id) || !BuildprintPlacements.visible(id)) continue;
            if (!dimension.equals(BuildprintPlacements.dimension(id))) continue;
            int[] b = BuildprintPlacements.bounds(id); // {minX, minY, minZ, maxX, maxY, maxZ}
            if (b == null || b.length < 6) continue;
            if (px < b[0] - range || px > b[3] + range) continue;
            if (py < b[1] - range || py > b[4] + range) continue;
            if (pz < b[2] - range || pz > b[5] + range) continue;
            Map<String, int[]> mats = BuildprintPlacements.materials(id); // item id -> {required, placed, stillNeeded}
            if (mats == null) continue;
            for (Map.Entry<String, int[]> e : mats.entrySet()) {
                int[] v = e.getValue();
                if (v == null || v.length < 3 || v[2] <= 0) continue;
                out.merge(e.getKey(), v[2], Integer::sum);
            }
        }
        return out;
    }
}
