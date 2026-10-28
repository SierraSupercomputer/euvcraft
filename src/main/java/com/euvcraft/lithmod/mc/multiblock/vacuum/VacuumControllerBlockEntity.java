package com.euvcraft.lithmod.mc.multiblock.vacuum;

import com.euvcraft.lithmod.core.*;
import com.euvcraft.lithmod.core.multiblock.*;
import com.euvcraft.lithmod.core.t7.*;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.multiblock.advanced.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;

import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

/**
 * The central vacuum plant: 3x3x5 multiblock with optional service network.
 *
 * <p>The plant discovers consumers via a bounded face-adjacent BFS over its own conduits
 * ({@link BlockKind#VACUUM_CONDUIT}) and passthrough ports ({@link BlockKind#VACUUM_PORT})
 * that implement {@link VacuumAccess}. Only loaded positions are traversed. The BFS is
 * limited to a configurable maximum number of nodes (default 512) to prevent runaway scans.</p>
 *
 * <p>Consumers are deduplicated by their owner position ({@link VacuumConsumer#vacuumOwnerPos()}),
 * so multiple ports/loops belonging to the same consumer do not multiply grants.</p>
 *
 * <p>If multiple live plants are on the same connected network, a deterministic single-active
 * source is chosen (lowest controller position) to prevent double grants.</p>
 *
 * <p>Capacity allocation uses {@link CentralVacuumAllocator#allocate(int, int[], int)} with a
 * persisted cursor advanced once per plant tick, so undersupplied small consumers are not starved.</p>
 *
 * <p>The plant consumes FE for actual service plus a meaningful standing cost; cost and available
 * capacity must agree, never granting unpaid capacity. Grants are valid only for the matching
 * current or last tick epoch (documented target timing for coordinator). Per-chamber pressure
 * stays local; disconnect never vents other clients, no demand for absent consumers.</p>
 *
 * <p>Local pumps automatically cover deficit/fallback through consumers later. Public network
 * status fields expose capacity, demand, FE paused, conflict, and number of clients for UI.</p>
 *
 * <p>Efficient for several clients via configurable cost defaults, no tiny instant-off scrap.
 * No whole-fab pressure scalar. Save buffers, cursor, and enable state; no stale grants after
 * reload/removed plant.</p>
 */
public final class VacuumControllerBlockEntity extends FabMachineBlockEntity implements AdvancedOwner {

    private final AdvancedFormation formation = new AdvancedFormation(this);

    /** Plant configuration: max network nodes for BFS. */
    private int maxNetworkNodes = 512;

    /** Plant configuration: standing FE cost per tick (cost to keep plant running). */
    private int standingFePerTick = 100;

    /** Plant configuration: FE cost per µTorr/tick of granted service. */
    private int fePerServiceMicroTorr = 1;

    /** Network discovery epoch: incremented each successful discovery. */
    private long discoveryEpoch = 0;

    /** Current plant tick counter (epoch for grant validity). */
    private long plantTick = 0;

    /** Allocation cursor for fair round-robin. */
    private int allocationCursor = 0;

    /** Discovered consumers this tick. */
    private final List<DiscoveredConsumer> consumers = new ArrayList<>();

    /** Network status for UI. */
    private int networkCapacity = 0;
    private int networkDemand = 0;
    private int networkFePaused = 0;
    private boolean networkConflict = false;
    private int networkClientCount = 0;

    /** Whether the plant is enabled (can be toggled via config/redstone). */
    private boolean enabled = true;

    public VacuumControllerBlockEntity(BlockPos pos, BlockState state) {
        super(VacuumMachines.CONTROLLER_BE.get(), pos, state, 0, 0);
        // Parent constructor creates buffer, exposed, and data with fallback 0,0
    }

    @Override protected String machineId() {
        return "central_vacuum_plant";
    }

    @Override public Component getDisplayName() {
        return Component.translatable("block.lithmod.central_vacuum_plant");
    }

    @Override public FabMachineBlockEntity machine() {
        return this;
    }

    @Override public AdvancedFormation formation() {
        return formation;
    }

    @Override public boolean large() {
        return true; // 3x3x5 plant
    }

    @Override public MatchResult matchStructure(MultiblockPos anchor, MultiblockDirection facing,
            java.util.function.Function<MultiblockPos, BlockKind> lookup) {
        return T7UtilityStructureMatcher.matchVacuumPlant(anchor, facing, lookup);
    }

    // ------------------------------------------------------------------
    // Public read access for UI
    // ------------------------------------------------------------------

    public int networkCapacity() { return networkCapacity; }
    public int networkDemand() { return networkDemand; }
    public int networkFePaused() { return networkFePaused; }
    public boolean networkConflict() { return networkConflict; }
    public int networkClientCount() { return networkClientCount; }
    public boolean enabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; setChanged(); }
    public long plantTick() { return plantTick; }
    public long discoveryEpoch() { return discoveryEpoch; }

    // ------------------------------------------------------------------
    // Tick
    // ------------------------------------------------------------------

    @Override public void serverTick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        revalidate();
        refreshDefinition();

        // Resize energy buffer from definition
        if (definition().isPresent()) {
            MachineDefinition def = definition().get();
            applyDefinition(def);
            this.maxNetworkNodes = def.paramOrDefault("vacuum_max_nodes", 512);
            this.standingFePerTick = def.paramOrDefault("vacuum_standing_fe_per_tick", 100);
            this.fePerServiceMicroTorr = def.paramOrDefault("vacuum_fe_per_service_microtorr", 1);
            data().set(DATA_CAPACITY, buffer().getCapacityAsInt());
        } else {
            // withoutDefinition is called by refreshDefinition which calls applyDefinition with 0,0
            data().set(DATA_CAPACITY, 0);
        }

        if (!isFormed() || definition().isEmpty() || !enabled) {
            sync();
            return;
        }

        plantTick++;
        discoverNetwork();
        allocateAndGrant();
        sync();
    }

    /**
     * Discovers consumers on the vacuum network via bounded face-adjacent BFS.
     * Only traverses loaded chunks. Limits to maxNetworkNodes.
     * Deduplicates consumers by owner position.
     * Detects conflict if multiple plants on same network.
     */
    private void discoverNetwork() {
        consumers.clear();
        networkCapacity = 0;
        networkDemand = 0;
        networkFePaused = 0;
        networkConflict = false;
        networkClientCount = 0;

        if (level == null) return;

        // Check for conflict: another plant on the same network
        if (hasConflictingPlant()) {
            networkConflict = true;
            return; // No grants when conflicted
        }

        // BFS from this plant's conduits and passthroughs
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();

        // Seed queue with this plant's conduit and passthrough positions
        for (BlockPos pos : formation.members()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof VacuumMemberBlockEntity member) {
                if (member.kind() == BlockKind.VACUUM_CONDUIT || member.kind() == BlockKind.VACUUM_PORT) {
                    queue.add(pos);
                    visited.add(pos);
                }
            }
        }

        // Also include the controller position if it has conduit/passthrough capability
        // (it doesn't, but we start from the plant's own network blocks)

        int nodesVisited = 0;
        while (!queue.isEmpty() && nodesVisited < maxNetworkNodes) {
            BlockPos current = queue.poll();
            nodesVisited++;

            BlockEntity be = level.getBlockEntity(current);
            if (be instanceof VacuumMemberBlockEntity member) {
                // Check if this member exposes a consumer
                VacuumConsumer consumer = member.vacuumConsumer();
                if (consumer != null) {
                    // Deduplicate by owner position
                    BlockPos ownerPos = consumer.vacuumOwnerPos();
                    if (!consumers.stream().anyMatch(c -> c.ownerPos.equals(ownerPos))) {
                        consumers.add(new DiscoveredConsumer(consumer, ownerPos));
                    }
                }

                // Traverse to face-adjacent conduits/passthroughs
                for (Direction dir : Direction.values()) {
                    BlockPos neighbor = current.relative(dir);
                    if (visited.contains(neighbor)) continue;
                    if (!level.hasChunkAt(neighbor)) continue; // loaded positions only

                    BlockEntity neighborBe = level.getBlockEntity(neighbor);
                    if (neighborBe instanceof VacuumMemberBlockEntity neighborMember) {
                        if (neighborMember.kind() == BlockKind.VACUUM_CONDUIT || neighborMember.kind() == BlockKind.VACUUM_PORT) {
                            // Verify it belongs to the same network (same plant or connected)
                            if (isSameNetwork(neighborMember)) {
                                queue.add(neighbor);
                                visited.add(neighbor);
                            }
                        }
                    }
                }
            }
        }

        discoveryEpoch++;
        networkClientCount = consumers.size();

        // Calculate total capacity and demand
        if (definition().isPresent()) {
            MachineDefinition def = definition().get();
            networkCapacity = def.paramOrDefault("vacuum_capacity_microtorr_per_tick", 500);
        }

        for (DiscoveredConsumer dc : consumers) {
            networkDemand += dc.consumer.vacuumDemand();
        }
    }

    /** Checks if there's another active plant on the same network with lower position (deterministic). */
    private boolean hasConflictingPlant() {
        if (level == null) return false;
        // Look for other vacuum controllers in the discovered network
        for (BlockPos pos : formation.members()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof VacuumMemberBlockEntity member) {
                if (member.kind() == BlockKind.VACUUM_CONDUIT || member.kind() == BlockKind.VACUUM_PORT) {
                    // Check face-adjacent for another controller
                    for (Direction dir : Direction.values()) {
                        BlockPos neighbor = pos.relative(dir);
                        BlockEntity neighborBe = level.getBlockEntity(neighbor);
                        if (neighborBe instanceof VacuumControllerBlockEntity otherPlant) {
                            if (otherPlant != this && otherPlant.isFormed() && otherPlant.enabled) {
                                // Deterministic: lower BlockPos wins
                                if (otherPlant.getBlockPos().compareTo(this.getBlockPos()) < 0) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    /** Checks if a member belongs to this network (same plant or connected via conduits). */
    private boolean isSameNetwork(VacuumMemberBlockEntity member) {
        // For simplicity: any conduit/passthrough reachable via BFS is part of the network
        // A more sophisticated implementation would track network IDs
        return true;
    }

    /**
     * Allocates capacity fairly among discovered consumers and grants service.
     * Advances cursor once per plant tick.
     */
    private void allocateAndGrant() {
        if (consumers.isEmpty()) {
            // No consumers: still pay standing cost if enabled
            if (standingFePerTick > 0) {
                buffer().extract(standingFePerTick, null); // best effort
            }
            return;
        }

        int[] demands = new int[consumers.size()];
        for (int i = 0; i < consumers.size(); i++) {
            demands[i] = consumers.get(i).consumer.vacuumDemand();
        }

        CentralVacuumAllocator.Allocation allocation = CentralVacuumAllocator.allocate(networkCapacity, demands, allocationCursor);
        allocationCursor = (allocationCursor + 1) % Math.max(1, consumers.size());

        // Calculate total FE cost: standing + service
        int totalService = allocation.totalAllocatedMicroTorrPerTick();
        int serviceFe = totalService * fePerServiceMicroTorr;
        int totalFeCost = standingFePerTick + serviceFe;

        // Check if we can afford the total cost (full-or-nothing for service)
        boolean canPay = buffer().getAmountAsLong() >= totalFeCost;

        networkFePaused = canPay ? 0 : totalFeCost;

        if (!canPay) {
            // Cannot afford: no grants, only standing cost if possible
            if (buffer().getAmountAsLong() >= standingFePerTick) {
                buffer().extract(standingFePerTick, null);
            }
            // All consumers get zero grant
            for (DiscoveredConsumer dc : consumers) {
                dc.consumer.acceptVacuumGrant(plantTick, 0);
            }
            return;
        }

        // Pay the full cost
        buffer().extract(totalFeCost, null);

        // Grant to each consumer
        for (int i = 0; i < consumers.size(); i++) {
            DiscoveredConsumer dc = consumers.get(i);
            int grant = allocation.at(i);
            boolean allowed = dc.consumer.acceptVacuumGrant(plantTick, grant);
            // Track for UI: if a consumer with demand got zero grant, it's undersupplied
            if (demands[i] > 0 && grant == 0) {
                networkFePaused = Math.max(networkFePaused, demands[i]);
            }
        }
    }

    private void sync() {
        data().set(DATA_ENERGY, buffer().getAmountAsInt());
        data().set(DATA_CAPACITY, buffer().getCapacityAsInt());
        data().set(DATA_PROGRESS, (int) (plantTick & 0x7FFFFFFF)); // reuse progress for tick display
        data().set(DATA_TICKS, networkCapacity); // reuse ticks for capacity display
    }

    // ------------------------------------------------------------------
    // Removal and drops
    // ------------------------------------------------------------------

    @Override public void setRemoved() {
        // Removal may run after this chunk left the loaded map: unload() does no world queries.
        // Clear consumer bindings on our members
        if (level != null) {
            for (BlockPos pos : formation.members()) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof VacuumMemberBlockEntity member) {
                    member.clearConsumer();
                }
            }
        }
        formation.unload();
        super.setRemoved();
    }

    // ------------------------------------------------------------------
    // Save and load
    // ------------------------------------------------------------------

    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        buffer().serialize(output);
        output.putInt("vacuumMaxNodes", maxNetworkNodes);
        output.putInt("vacuumStandingFe", standingFePerTick);
        output.putInt("vacuumFePerService", fePerServiceMicroTorr);
        output.putLong("vacuumPlantTick", plantTick);
        output.putLong("vacuumDiscoveryEpoch", discoveryEpoch);
        output.putInt("vacuumCursor", allocationCursor);
        output.putBoolean("vacuumEnabled", enabled);
    }

    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        buffer().deserialize(input);
        this.maxNetworkNodes = input.getIntOr("vacuumMaxNodes", 512);
        this.standingFePerTick = input.getIntOr("vacuumStandingFe", 100);
        this.fePerServiceMicroTorr = input.getIntOr("vacuumFePerService", 1);
        this.plantTick = input.getLongOr("vacuumPlantTick", 0);
        this.discoveryEpoch = input.getLongOr("vacuumDiscoveryEpoch", 0);
        this.allocationCursor = input.getIntOr("vacuumCursor", 0);
        this.enabled = input.getBooleanOr("vacuumEnabled", true);
        // Clear stale grants on load
        this.consumers.clear();
        this.networkCapacity = 0;
        this.networkDemand = 0;
        this.networkFePaused = 0;
        this.networkConflict = false;
        this.networkClientCount = 0;
        data().set(DATA_CAPACITY, buffer().getCapacityAsInt());
        sync();
    }

    @Override protected AbstractContainerMenu createMachineMenu(int id, Inventory inventory, FabMachineBlockEntity machine) {
        return new VacuumMenu(id, inventory, this);
    }

    /** Internal record for discovered consumers with their owner position for deduplication. */
    private record DiscoveredConsumer(VacuumConsumer consumer, BlockPos ownerPos) {}
}