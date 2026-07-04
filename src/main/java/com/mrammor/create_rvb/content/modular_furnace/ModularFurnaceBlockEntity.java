package com.mrammor.create_rvb.content.modular_furnace;

import com.mrammor.create_rvb.registry.ModBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * BlockEntity for modular furnace blocks.
 * Stores reference to master block of the multiblock structure.
 */
public class ModularFurnaceBlockEntity extends BlockEntity {

    private BlockPos masterPos = null;

    public ModularFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.MODULAR_FURNACE.get(), pos, state);
    }

    /**
     * Tick method for furnace logic.
     * Called every game tick for active furnaces.
     */
    public static void tick(Level level, BlockPos pos, BlockState state, ModularFurnaceBlockEntity blockEntity) {
        // TODO: Implement furnace smelting logic here
    }

    /**
     * Get the master BlockEntity for this structure.
     * The master is responsible for coordinating the entire multiblock.
     * 
     * @return Master BlockEntity, or this block if no master exists
     */
    public ModularFurnaceBlockEntity getMaster() {
        if (level != null && hasMaster()) {
            BlockEntity be = level.getBlockEntity(masterPos);
            if (be instanceof ModularFurnaceBlockEntity masterBE) {
                return masterBE;
            }
        }
        return this; // Fallback: this block is its own master
    }

    /**
     * Set the master block for this furnace.
     * Called when structure is formed or broken.
     * 
     * @param pos Position of master block, or null to isolate this block
     */
    public void setMaster(BlockPos pos) {
        if (java.util.Objects.equals(this.masterPos, pos)) {
            return; // No change
        }
        
        this.masterPos = pos;
        setChanged();
        
        if (level != null) {
            // Sync with client
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            
            // Request model update on client side
            if (level.isClientSide) {
                requestModelDataUpdate();
                // Notify neighbors to update their render state
                level.setBlocksDirty(worldPosition, getBlockState(), getBlockState());
            }
        }
    }

    /**
     * Get the position of the master block.
     * 
     * @return Master block position, or this block's position if no master
     */
    public BlockPos getMasterPos() {
        return masterPos != null ? masterPos : this.worldPosition;
    }

    /**
     * Check if this block has a master in a structure.
     * 
     * @return true if this block is part of a multiblock, false if isolated
     */
    public boolean hasMaster() {
        return masterPos != null;
    }

    // --- SYNCHRONIZATION WITH CLIENT ---

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (masterPos != null) {
            tag.put("MasterPos", NbtUtils.writeBlockPos(masterPos));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("MasterPos")) {
            this.masterPos = NbtUtils.readBlockPos(tag, "MasterPos").orElse(null);
        } else {
            this.masterPos = null;
        }
        
        // Request model update on client side
        if (level != null && level.isClientSide) {
            requestModelDataUpdate();
        }
    }
}
