package com.euvcraft.lithmod.mc.machine.mask;

import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.machine.FabMachineMenu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;

/**
 * Menu for the Pattern Generator: two input slots and one output slot.
 *
 * <p>Identical on both sides. The three machine slots are added in the same order by
 * {@link #addMachineSlots()} on the server instance and on the client instance the
 * {@code MenuType} factory builds, so the slot indices in a click packet mean the same thing
 * on each, and the base class's shift-click routing splits the slot list at the right
 * place: a shift-click out of the machine moves to the player inventory, a shift-click from
 * it consults each input slot's {@code mayPlace}, and the output slot takes nothing.</p>
 *
 * <p>The status value is this menu's own and is registered <em>after</em> the base class's
 * four, so it is always the fifth value the server syncs and never disturbs the energy and
 * progress indices every machine shares. It is registered on the client too, from a local
 * {@link SimpleContainerData}, which is what the sync fills in; without it the client would
 * read a value that was never sent.</p>
 */
public class PatternGeneratorMenu extends FabMachineMenu {

    /**
     * The index of the status value inside this machine's own one-value
     * {@link ContainerData}.
     *
     * <p>Not the wire index: a {@code ContainerData} is addressed from its own zero, so the
     * status holder this menu builds is always read at index 0 whatever the base class
     * registered before it.</p>
     */
    public static final int STATUS_DATA_INDEX = 0;

    /**
     * Where the status value sits on the wire: the fifth, straight after the base class's
     * four.
     *
     * <p>It is that index because the base constructor calls {@code addDataSlots} before this
     * constructor's own body does, on the server instance and on the client instance alike,
     * and the sync counts the values in registration order.</p>
     */
    public static final int STATUS_WIRE_INDEX =
        FabMachineBlockEntity.DATA_COUNT + STATUS_DATA_INDEX;

    private final ContainerData status;

    public PatternGeneratorMenu(int containerId, Inventory playerInventory,
                                FabMachineBlockEntity machine) {
        super(MaskMachines.PATTERN_GENERATOR_MENU.get(), containerId, playerInventory, machine,
            PatternGeneratorBlockEntity.INPUT_SLOTS, PatternGeneratorBlockEntity.OUTPUT_SLOT + 1);
        this.status = machine instanceof PatternGeneratorBlockEntity generator
            ? generator.statusData()
            : new SimpleContainerData(PatternGeneratorBlockEntity.STATUS_COUNT);
        addDataSlots(this.status);
    }

    /**
     * Client-side constructor, reached through the {@code MenuType} factory that
     * {@code IMenuTypeExtension.create} installed.
     */
    public PatternGeneratorMenu(int containerId, Inventory playerInventory,
                                RegistryFriendlyByteBuf buffer) {
        this(containerId, playerInventory, (FabMachineBlockEntity) null);
    }

    @Override
    protected void addMachineSlots() {
        // The two ingredients sit side by side rather than stacked, so three slot rows do not
        // have to be fitted above the player inventory, which the base menu fixes at y = 84.
        // The pair spans 70..106, which is the same centre as the single output slot at 80.
        addMachineSlot(PatternGeneratorBlockEntity.BLANK_SLOT,
            MACHINE_SLOT_X - 10, MACHINE_SLOT_Y);
        addMachineSlot(PatternGeneratorBlockEntity.DESIGN_SLOT,
            MACHINE_SLOT_X + 8, MACHINE_SLOT_Y);
        addOutputSlot(PatternGeneratorBlockEntity.OUTPUT_SLOT,
            MACHINE_SLOT_X, MACHINE_SLOT_Y + 18);
    }

    /**
     * Why the matching recipe is being refused, or {@link PatternGeneratorBlockEntity#STATUS_NONE}.
     *
     * <p>The raw number, not a translated line: the menu is server-side code too, and the
     * screen is the only place that turns a status into words for a player.</p>
     */
    public final int status() {
        return this.status.get(STATUS_DATA_INDEX);
    }
}

