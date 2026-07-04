package com.mrammor.create_rvb.content.modular_furnace;

import com.mrammor.create_rvb.CreateRebuildVanillaBlocks;
import com.mrammor.create_rvb.util.StructureValidator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

/**
 * Unified event handler for modular furnace multiblock structure management.
 * Handles both block placement and breaking with optimized caching.
 */
@EventBusSubscriber(modid = CreateRebuildVanillaBlocks.MODID)
public class ModularFurnaceCommonEvents {

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof Level level && !level.isClientSide()) {
            BlockPos pos = event.getPos();
            
            if (level.getBlockState(pos).is(Blocks.FURNACE)) {
                // Invalidate cache for this position
                StructureValidator.invalidateCache(pos);
                
                // Try to form a valid structure
                formStructure(level, pos);
            }
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof Level level && !level.isClientSide()) {
            BlockPos pos = event.getPos();
            
            if (level.getBlockState(pos).is(Blocks.FURNACE)) {
                // Invalidate cache for this position and neighbors
                StructureValidator.invalidateCache(pos);
                
                // Break the structure and isolate all connected blocks
                splitStructure(level, pos);
            }
        }
    }

    /**
     * Attempt to form a valid multiblock structure at the given position.
     * If valid structure is found, designates a master block.
     * Otherwise, isolates the block as a single unit.
     */
    private static void formStructure(Level level, BlockPos triggerPos) {
        Set<BlockPos> structure = StructureValidator.findStructure(level, triggerPos);

        if (structure != null && !structure.isEmpty()) {
            // Find and set master block
            BlockPos masterPos = StructureValidator.findMasterPos(structure);
            if (masterPos != null) {
                applyStructure(level, structure, masterPos);
            }
        } else {
            // No valid structure, isolate this block
            if (level.getBlockEntity(triggerPos) instanceof ModularFurnaceBlockEntity be) {
                be.setMaster(null);
            }
        }
    }

    /**
     * Apply master block designation to all blocks in structure.
     */
    private static void applyStructure(Level level, Set<BlockPos> structure, BlockPos masterPos) {
        for (BlockPos pos : structure) {
            if (level.getBlockEntity(pos) instanceof ModularFurnaceBlockEntity be) {
                be.setMaster(masterPos);
                // Flag 3: update block on client and server
                level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
            }
        }
    }

    /**
     * Break the multiblock structure at the given position.
     * This isolates all connected furnace blocks.
     */
    private static void splitStructure(Level level, BlockPos brokenPos) {
        // First, isolate all connected blocks
        isolateConnectedBlocks(level, brokenPos);
        
        // Then, attempt to reform structures from neighboring blocks
        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = brokenPos.relative(dir);
            if (level.getBlockState(neighborPos).is(Blocks.FURNACE)) {
                StructureValidator.invalidateCache(neighborPos);
                formStructure(level, neighborPos);
            }
        }
    }

    /**
     * Isolate all furnace blocks connected to the given position.
     * This is a BFS that resets master pointers for all connected furnaces.
     */
    private static void isolateConnectedBlocks(Level level, BlockPos start) {
        Queue<BlockPos> queue = new LinkedList<>();
        Set<BlockPos> visited = new java.util.HashSet<>();
        
        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            if (level.getBlockEntity(current) instanceof ModularFurnaceBlockEntity be) {
                be.setMaster(null);
                level.sendBlockUpdated(current, level.getBlockState(current), level.getBlockState(current), 3);
            }
            
            for (Direction dir : Direction.values()) {
                BlockPos neighbor = current.relative(dir);
                if (!visited.contains(neighbor) && level.getBlockState(neighbor).is(Blocks.FURNACE)) {
                    visited.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }
    }
}
