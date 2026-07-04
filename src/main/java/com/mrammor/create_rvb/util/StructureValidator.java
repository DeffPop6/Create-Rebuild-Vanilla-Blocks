package com.mrammor.create_rvb.util;

import com.mrammor.create_rvb.config.StructureConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.util.*;

/**
 * Optimized multiblock structure validator.
 * Handles BFS-based structure detection with caching and early termination.
 * Replaces duplicate calculateTowerStructure implementations.
 * Thread-safe and designed for scalability.
 */
public class StructureValidator {
    
    private static final Map<Long, CachedStructure> STRUCTURE_CACHE = new LinkedHashMap<Long, CachedStructure>(StructureConfig.STRUCTURE_CACHE_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, CachedStructure> eldest) {
            return size() > StructureConfig.STRUCTURE_CACHE_SIZE || System.currentTimeMillis() - eldest.getValue().timestamp > StructureConfig.CACHE_TTL_MILLIS;
        }
    };
    
    private static final Object CACHE_LOCK = new Object();
    
    /**
     * Internal class to store cached structure with timestamp for TTL
     */
    private static class CachedStructure {
        final Set<BlockPos> blocks;
        final long timestamp;
        
        CachedStructure(Set<BlockPos> blocks) {
            this.blocks = blocks != null ? new HashSet<>(blocks) : null;
            this.timestamp = System.currentTimeMillis();
        }
    }
    
    /**
     * Validate and find multiblock structure starting from given position.
     * Uses BFS with early termination for performance.
     * Results are cached for 60 seconds to avoid redundant scans.
     * 
     * @param level The world
     * @param startPos Starting position for structure search
     * @return Set of all block positions in valid structure, or null if invalid
     */
    public static Set<BlockPos> findStructure(Level level, BlockPos startPos) {
        if (level == null || startPos == null) {
            return null;
        }
        
        // Check cache first
        long cacheKey = startPos.asLong();
        synchronized (CACHE_LOCK) {
            CachedStructure cached = STRUCTURE_CACHE.get(cacheKey);
            if (cached != null) {
                return cached.blocks != null ? new HashSet<>(cached.blocks) : null;
            }
        }
        
        // Perform actual validation
        Set<BlockPos> result = validateStructureInternal(level, startPos);
        
        // Cache the result
        synchronized (CACHE_LOCK) {
            STRUCTURE_CACHE.put(cacheKey, new CachedStructure(result));
        }
        
        return result;
    }
    
    /**
     * Invalidate cache for a specific position and its neighbors.
     * Call this after structure changes.
     */
    public static void invalidateCache(BlockPos pos) {
        if (pos == null) return;
        
        synchronized (CACHE_LOCK) {
            STRUCTURE_CACHE.remove(pos.asLong());
            // Also invalidate neighboring positions that might share structures
            for (Direction dir : Direction.values()) {
                STRUCTURE_CACHE.remove(pos.relative(dir).asLong());
            }
        }
    }
    
    /**
     * Clear entire cache. Use sparingly, only on major world changes.
     */
    public static void clearCache() {
        synchronized (CACHE_LOCK) {
            STRUCTURE_CACHE.clear();
        }
    }
    
    /**
     * Internal BFS-based structure validation.
     * Performs early termination if structure exceeds limits.
     */
    private static Set<BlockPos> validateStructureInternal(Level level, BlockPos startPos) {
        // Quick check: starting position must be a furnace
        if (!level.getBlockState(startPos).is(Blocks.FURNACE)) {
            return null;
        }
        
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();
        
        int minX = startPos.getX(), maxX = startPos.getX();
        int minY = startPos.getY(), maxY = startPos.getY();
        int minZ = startPos.getZ(), maxZ = startPos.getZ();
        
        queue.add(startPos);
        visited.add(startPos);
        
        // BFS with early termination
        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            
            // Update bounding box
            minX = Math.min(minX, current.getX());
            maxX = Math.max(maxX, current.getX());
            minY = Math.min(minY, current.getY());
            maxY = Math.max(maxY, current.getY());
            minZ = Math.min(minZ, current.getZ());
            maxZ = Math.max(maxZ, current.getZ());
            
            // Calculate current dimensions
            int sizeX = maxX - minX + 1;
            int sizeZ = maxZ - minZ + 1;
            int sizeY = maxY - minY + 1;
            
            // Early termination: structure is too large
            if (sizeX > StructureConfig.MAX_BASE_SIZE || 
                sizeZ > StructureConfig.MAX_BASE_SIZE || 
                sizeY > StructureConfig.MAX_HEIGHT ||
                visited.size() > StructureConfig.EARLY_TERMINATION_BLOCK_COUNT) {
                return null;
            }
            
            // Explore neighbors
            for (Direction dir : Direction.values()) {
                BlockPos neighbor = current.relative(dir);
                if (!visited.contains(neighbor) && level.getBlockState(neighbor).is(Blocks.FURNACE)) {
                    visited.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }
        
        // Final validation
        return validateFinalStructure(visited);
    }
    
    /**
     * Final structure validation after BFS completes.
     * Checks:
     * - Base is square (1x1, 2x2, or 3x3)
     * - Height is within limits
     * - Structure is monolithic (no gaps)
     */
    private static Set<BlockPos> validateFinalStructure(Set<BlockPos> visited) {
        if (visited.isEmpty()) {
            return null;
        }
        
        // Find bounds
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        
        for (BlockPos pos : visited) {
            minX = Math.min(minX, pos.getX());
            maxX = Math.max(maxX, pos.getX());
            minY = Math.min(minY, pos.getY());
            maxY = Math.max(maxY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        
        int sizeX = maxX - minX + 1;
        int sizeY = maxY - minY + 1;
        int sizeZ = maxZ - minZ + 1;
        
        // Validate dimensions
        if (!StructureConfig.isValidBaseSize(sizeX, sizeZ)) {
            return null;
        }
        if (!StructureConfig.isValidHeight(sizeY)) {
            return null;
        }
        
        // Validate structure is monolithic (no gaps)
        int expectedVolume = sizeX * sizeZ * sizeY;
        if (visited.size() != expectedVolume) {
            return null;
        }
        
        return visited;
    }
    
    /**
     * Find the master block position in a structure.
     * Master is the block with minimum asLong() value (X, then Y, then Z).
     */
    public static BlockPos findMasterPos(Set<BlockPos> structure) {
        if (structure == null || structure.isEmpty()) {
            return null;
        }
        
        BlockPos master = null;
        for (BlockPos pos : structure) {
            if (master == null || pos.asLong() < master.asLong()) {
                master = pos;
            }
        }
        return master;
    }
}
