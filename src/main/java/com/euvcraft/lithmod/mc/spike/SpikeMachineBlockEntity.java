package com.euvcraft.lithmod.mc.spike;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import org.jspecify.annotations.Nullable;

/**
 * Minimal single-block machine used only by the M1-02s spike game tests.
 *
 * <p>It exists to prove on a real server level that in NeoForge 26.3
 * <ul>
 *   <li>a block registered through {@code DeferredRegister.Blocks.registerBlock} does not hit
 *       "Block id not set", because the factory receives the
 *       {@code BlockBehaviour.Properties} the helper already stamped with the id;</li>
 *   <li>an energy buffer is exposed on every side through
 *       {@code RegisterCapabilitiesEvent.registerBlockEntity} with
 *       {@code Capabilities.Energy.BLOCK};</li>
 *   <li>the buffer accepts energy, refuses to hand any back, and no item or fluid capability
 *       exists on any side;</li>
 *   <li>stored energy and the slot contents survive a {@code saveAdditional} /
 *       {@code loadAdditional} round trip through the 26.3 value input/output API;</li>
 *   <li>the menu type registered with {@code IMenuTypeExtension.create} opens server-side.</li>
 * </ul>
 *
 * <p>Every pattern is taken from Arcforge or Encoded Logistics; the file each one came from
 * is listed in {@code docs/reports/M1-02s_energy-spike.md}. This is spike scaffolding, not
 * the machine framework itself.</p>
 */
public final class SpikeMachineBlockEntity extends BlockEntity implements MenuProvider {

    /** Capacity of the spike buffer, in FE. */
    public static final int CAPACITY = 10_000;
    /** Largest single insert the spike buffer accepts. */
    public static final int MAX_INSERT = 500;
    /** Always zero: the spike buffer refuses to give energy out. */
    public static final int MAX_EXTRACT = 0;
    /** Slot count of the spike machine. */
    public static final int SLOT_COUNT = 1;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private final TrackedEnergy energy = new TrackedEnergy(this);

    public SpikeMachineBlockEntity(BlockPos pos, BlockState state) {
        super(SpikeRegistries.BLOCK_ENTITY.get(), pos, state);
    }

    /**
     * An energy buffer that accepts from every side and never gives energy back.
     *
     * <p>{@code SimpleEnergyHandler(capacity, maxInsert, maxExtract, energy)} is the 26.3
     * constructor. A {@code maxExtract} of zero is all it takes to refuse extraction, so no
     * wrapper is needed. {@code onEnergyChanged} is the documented hook for calling
     * {@code setChanged()}.</p>
     */
    public static final class TrackedEnergy extends SimpleEnergyHandler {
        private final BlockEntity owner;

        TrackedEnergy(BlockEntity owner) {
            super(CAPACITY, MAX_INSERT, MAX_EXTRACT);
            this.owner = owner;
        }

        @Override
        protected void onEnergyChanged(int previousAmount) {
            this.owner.setChanged();
        }
    }

    /** The handler handed to {@code Capabilities.Energy.BLOCK}. */
    public EnergyHandler energy() {
        return this.energy;
    }

    /**
     * Overwrites the stored amount outright.
     *
     * <p>{@code SimpleEnergyHandler.set(int)} is concrete-type only, so the spike test needs a
     * way to wipe the buffer that does not go through a transaction. The value output API has
     * no "clear" either, so this exists purely to set up the save/load round trip.</p>
     */
    public void setStoredEnergy(int amount) {
        this.energy.set(amount);
    }

    /** The single slot, so the game test can put an item in and read it back. */
    public NonNullList<ItemStack> items() {
        return this.items;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ContainerHelper.loadAllItems(input, this.items);
        this.energy.deserialize(input);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.items);
        this.energy.serialize(output);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.lithmod.spike_machine");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new SpikeMachineMenu(containerId, playerInventory, this);
    }
}
