package com.mrammor.create_rvb.config;

/**
 * Centralized configuration for multiblock structure validation.
 * Allows easy scaling and customization without code changes.
 */
public class StructureConfig {
    
    // Base dimensions - can be easily adjusted for different structure types
    public static final int MIN_BASE_SIZE = 1;           // 1x1 minimum
    public static final int MAX_BASE_SIZE = 3;           // 3x3 maximum
    public static final int MAX_HEIGHT = 9;              // 9 blocks high
    
    // Performance tuning
    public static final int STRUCTURE_CACHE_SIZE = 256;  // Cache up to 256 structures
    public static final long CACHE_TTL_MILLIS = 60000;   // Cache valid for 60 seconds
    public static final int MAX_BLOCKS_PER_STRUCTURE = MAX_BASE_SIZE * MAX_BASE_SIZE * MAX_HEIGHT;  // 81 blocks
    
    // Early termination thresholds to prevent lag
    public static final int EARLY_TERMINATION_BLOCK_COUNT = MAX_BLOCKS_PER_STRUCTURE + 10;
    
    /**
     * Check if a base size is valid.
     * Only allows square bases: 1x1, 2x2, 3x3
     */
    public static boolean isValidBaseSize(int sizeX, int sizeZ) {
        return sizeX == sizeZ && sizeX >= MIN_BASE_SIZE && sizeX <= MAX_BASE_SIZE;
    }
    
    /**
     * Check if height is within limits
     */
    public static boolean isValidHeight(int height) {
        return height >= MIN_BASE_SIZE && height <= MAX_HEIGHT;
    }
    
    /**
     * Get maximum expected blocks for a structure
     */
    public static int getMaxBlocksForSize(int baseSize, int height) {
        return baseSize * baseSize * height;
    }
}
