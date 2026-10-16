package com.euvcraft.lithmod.mc.machine;

import com.euvcraft.lithmod.core.EnergySource;
import com.euvcraft.lithmod.core.MachineDefinition;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.data.MachineDefinitionLoader;
import com.euvcraft.lithmod.mc.data.MachineRecipeLoader;
import com.euvcraft.lithmod.mc.recipe.MachineRecipe;
import com.euvcraft.lithmod.mc.recipe.MachineRecipeRunner;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Base class for single-block fab machines.
 *
 * <p>Holds an energy buffer, a fixed set of input and output slots and a
 * {@link MachineRecipeRunner}. Subclasses supply the machine id, the slot counts and the
 * menu; everything else lives here.</p>
 *
 * <p><b>Energy.</b> Two objects, on purpose:</p>
 * <ul>
 *   <li>{@code buffer} is a real {@link SimpleEnergyHandler} the machine spends from, so a
 *       running recipe actually costs the player FE;</li>
 *   <li>{@code exposed} is what goes to {@code Capabilities.Energy.BLOCK}. It accepts from
 *       every side and its {@code extract} always returns zero, which is what "extract none"
 *       means without also making the machine free to run.</li>
 * </ul>
 *
 * <p>No item handler and no fluid capability are registered for this block entity type, so
 * {@code Level.getCapability} returns {@code null} for both on every side.</p>
 */
public abstract class FabMachineBlockEntity extends BlockEntity implements MenuProvider {

    /** Synced to the menu: stored FE. */
    public static final int DATA_ENERGY = 0;
    /** Synced to the menu: capacity in FE. */
    public static final int DATA_CAPACITY = 1;
    /** Synced to the menu: ticks completed. */
    public static final int DATA_PROGRESS = 2;
    /** Synced to the menu: ticks needed. */
    public static final int DATA_TICKS = 3;
    /** How many values are synced. */
    public static final int DATA_COUNT = 4;

    private final MachineEnergy buffer;
    private final EnergyHandler exposed;
    private final NonNullList<ItemStack> items;
    private final ContainerData data = new SimpleContainerData(DATA_COUNT);
    private final int inputSlotCount;
    private final MachineRecipeRunner runner;

    protected FabMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state,
                                    int inputSlots, int outputSlots,
                                    int fallbackCapacity, int fallbackMaxReceive) {
        super(type, pos, state);
        this.inputSlotCount = inputSlots;
        this.items = NonNullList.withSize(inputSlots + outputSlots, ItemStack.EMPTY);
        this.buffer = new MachineEnergy(this, fallbackCapacity, fallbackMaxReceive);
        this.exposed = new WithdrawNothing(this.buffer);
        this.data.set(DATA_CAPACITY, fallbackCapacity);
        this.runner = new MachineRecipeRunner(machineId(), 0);
    }

    /**
     * The {@link MachineDefinition#id()} of this machine; it also selects its recipes.
     *
     * <p>Called from the constructor, so it must only return a constant.</p>
     */
    protected abstract String machineId();

    /** How many slots at the front of the inventory are inputs. */
    public final int inputSlotCount() {
        return this.inputSlotCount;
    }

    /** Total slot count, inputs plus outputs. */
    public final int slotCount() {
        return this.items.size();
    }

    /** The slot at {@code index}, for menu slot construction and for tests. */
    public final ItemStack slot(int index) {
        return this.items.get(index);
    }

    /**
     * Replaces the whole stack at {@code index}.
     *
     * <p>26.3 {@code ItemStack} is final with no {@code setItem}, so a different item cannot
     * be written into an existing stack in place; the list entry is replaced instead. Every
     * read goes back through {@link #slot(int)}, so the replacement is always seen.</p>
     */
    public final void setSlot(int index, ItemStack stack) {
        this.items.set(index, stack);
        setChanged();
    }

    /** Empties the slot at {@code index}. */
    public final void clearSlot(int index) {
        this.items.set(index, ItemStack.EMPTY);
        setChanged();
    }

    /**
     * Puts the product into the output slot.
     *
     * <p>An empty slot takes the product as-is; a slot already holding the same item and
     * components is grown in place. Anything else is refused by
     * {@code MachineRecipeRunner.hasRoomFor} before it gets here.</p>
     */
    public final void giveOutput(ItemStack product) {
        ItemStack existing = outputSlot();
        if (existing.isEmpty()) {
            this.items.set(this.inputSlotCount, product);
        } else {
            existing.setCount(existing.getCount() + product.getCount());
        }
        setChanged();
    }

    /** The input slots, in order. */
    public final List<ItemStack> inputSlots() {
        return new ArrayList<>(this.items.subList(0, this.inputSlotCount));
    }

    /** The first output slot, which the runner writes into. */
    public final ItemStack outputSlot() {
        return this.inputSlotCount < this.items.size() ? this.items.get(this.inputSlotCount) : ItemStack.EMPTY;
    }

    /**
     * The handler handed to {@code Capabilities.Energy.BLOCK}: accepts on every side, never
     * extracts.
     */
    public final EnergyHandler energy() {
        return this.exposed;
    }

    /**
     * The real buffer, which the machine spends from.
     *
     * <p>Exposed as the concrete type so a test can call {@code set(int)} to put the buffer
     * into a known state; the capability path only ever offers {@link #energy()}.</p>
     */
    public final MachineEnergy buffer() {
        return this.buffer;
    }

    /** Puts the buffer into a known state, clamped to capacity. Used by tests. */
    public void setStoredEnergy(int amount) {
        this.buffer.set(Math.max(0, Math.min(amount, this.buffer.getCapacityAsInt())));
        syncData();
    }

    public final MachineRecipeRunner runner() {
        return this.runner;
    }

    /** The values the menu reads. */
    public final ContainerData data() {
        return this.data;
    }

    /** The definition for this machine, empty when it is not loaded. */
    public final Optional<MachineDefinition> definition() {
        MachineDefinitionLoader.Registry registry = MachineDefinitionLoader.getRegistry();
        return registry == null ? Optional.empty() : registry.byId(machineId());
    }

    /** The machine's internal energy draw, as the runner sees it. */
    public final EnergySource energySource() {
        return amount -> {
            if (amount <= 0) {
                return 0;
            }
            try (Transaction transaction = Transaction.openRoot()) {
                int drawn = this.buffer.extract(amount, transaction);
                transaction.commit();
                return drawn;
            }
        };
    }

    /** The recipes available to this machine right now. */
    public final List<MachineRecipe> recipes() {
        MachineRecipeLoader.Registry registry = MachineRecipeLoader.getRegistry();
        return registry == null ? List.of() : registry.forMachine(machineId());
    }

    /** One server tick. Subclasses call this from their ticker. */
    public void serverTick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        definition().ifPresent(definition -> {
            this.data.set(DATA_CAPACITY, definition.energyCapacity());
            this.runner.setMachineEnergyPerTick(definition.energyPerTick(null, EUVCRAFTMod.settings()));
        });

        // Progress restored from a save is re-attached to whichever recipe now fits, rather
        // than starting the machine over from tick one.
        if (this.runner.hasRestoredProgress()) {
            MachineRecipe restored = MachineRecipeRunner.firstMatch(recipes(), inputSlots());
            if (restored != null) {
                this.runner.adopt(restored);
            } else {
                this.runner.reset();
            }
        }

        boolean completed = this.runner.tick(recipes(), inputSlots(), outputSlot(),
            this::giveOutput, energySource());
        syncData();
        if (completed || this.runner.isRunning()) {
            setChanged();
        }
    }

    private void syncData() {
        this.data.set(DATA_ENERGY, this.buffer.getAmountAsInt());
        this.data.set(DATA_PROGRESS, this.runner.progress());
        this.data.set(DATA_TICKS, this.runner.ticks());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ContainerHelper.loadAllItems(input, this.items);
        this.buffer.deserialize(input);
        this.runner.loadState(input.childOrEmpty("recipe"));
        this.data.set(DATA_CAPACITY, this.buffer.getCapacityAsInt());
        syncData();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.items);
        this.buffer.serialize(output);
        this.runner.saveState(output.child("recipe"));
    }

    /** Drops every stored item when the block is broken. */
    public void dropContents() {
        if (level == null || level.isClientSide()) {
            return;
        }
        for (ItemStack stack : this.items) {
            if (stack.isEmpty()) {
                continue;
            }
            net.minecraft.world.entity.item.ItemEntity entity = new net.minecraft.world.entity.item.ItemEntity(
                level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, stack.copy());
            level.addFreshEntity(entity);
            stack.setCount(0);
        }
        setChanged();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.lithmod." + machineId());
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return createMachineMenu(containerId, playerInventory, this);
    }

    /** Builds this machine's menu; every concrete machine supplies one. */
    protected abstract AbstractContainerMenu createMachineMenu(int containerId,
                                                                Inventory playerInventory,
                                                                FabMachineBlockEntity machine);

    /**
     * The machine's real energy storage.
     *
     * <p>{@code maxExtract} is {@link Integer#MAX_VALUE} here rather than zero, because this
     * is the buffer the machine itself spends from. Refusing external extraction is the
     * job of {@link WithdrawNothing}, which is what actually reaches the capability.</p>
     */
    public static final class MachineEnergy extends SimpleEnergyHandler {

        private final BlockEntity owner;

        MachineEnergy(BlockEntity owner, int capacity, int maxReceive) {
            super(capacity, maxReceive, Integer.MAX_VALUE);
            this.owner = owner;
        }

        @Override
        protected void onEnergyChanged(int previousAmount) {
            this.owner.setChanged();
        }
    }

    /**
     * The capability view: accepts on every side, refuses every extraction.
     *
     * <p>Without this, "refuse extraction" would have to be implemented by setting
     * {@code maxExtract = 0} on the real buffer, which would also stop the machine paying for
     * the work it does.</p>
     */
    public static final class WithdrawNothing implements EnergyHandler {

        private final EnergyHandler delegate;

        WithdrawNothing(EnergyHandler delegate) {
            this.delegate = delegate;
        }

        @Override
        public long getAmountAsLong() {
            return this.delegate.getAmountAsLong();
        }

        @Override
        public long getCapacityAsLong() {
            return this.delegate.getCapacityAsLong();
        }

        @Override
        public int insert(int amount, TransactionContext transaction) {
            return this.delegate.insert(amount, transaction);
        }

        @Override
        public int extract(int amount, TransactionContext transaction) {
            return 0;
        }
    }
}
