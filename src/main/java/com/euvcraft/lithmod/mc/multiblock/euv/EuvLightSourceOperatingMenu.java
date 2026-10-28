package com.euvcraft.lithmod.mc.multiblock.euv;

import com.euvcraft.lithmod.mc.cleanroom.WideContainerData;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.machine.FabMachineMenu;
import com.euvcraft.lithmod.mc.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

/** Operating menu for EUV Light Source. Extends FabMachineMenu with 1 input slot (tin) + player inventory. */
public final class EuvLightSourceOperatingMenu extends FabMachineMenu {

    private static final TagKey<Item> TIN = TagKey.create(Registries.ITEM, Identifier.parse("c:ingots/tin"));
    private final @Nullable EuvLightSourceControllerBlockEntity lightSource;
    private final BlockPos panelPos;
    private final @Nullable EuvLightSourceMemberBlockEntity panel;
    private final ContainerData values;

    public EuvLightSourceOperatingMenu(int containerId, Inventory playerInventory, @Nullable EuvLightSourceControllerBlockEntity lightSource, BlockPos pos) {
        super(EuvMachines.LIGHT_SOURCE_OPERATING_MENU.get(), containerId, playerInventory, lightSource, 1, 1);
        this.lightSource = lightSource;
        this.panelPos = pos.immutable();
        panel=lightSource!=null&&lightSource.getLevel()!=null&&lightSource.getLevel().getBlockEntity(pos) instanceof EuvLightSourceMemberBlockEntity p?p:null;
        this.values = lightSource == null ? new SimpleContainerData(8) : new ContainerData() {
            public int get(int i) {
                return switch (i) {
                    case 0 -> lightSource.isFormed() ? 1 : 0;
                    case 1 -> lightSource.buffer().getAmountAsInt();
                    case 2 -> lightSource.buffer().getCapacityAsInt();
                    case 3 -> lightSource.hydrogenTank.amount();
                    case 4 -> lightSource.tinStored();
                    case 5 -> lightSource.tinCreditTicks();
                    case 6 -> lightSource.vacuumState().pressureMicroTorr() / 1000;
                    case 7 -> lightSource.hasScannerReservation() ? 1 : 0;
                    default -> 0;
                };
            }
            public void set(int i, int v) {}
            public int getCount() { return 8; }
        };
        addDataSlots(new WideContainerData(values));
    }

    public EuvLightSourceOperatingMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, playerInventory, null, buffer.readBlockPos());
    }

    @Override
    protected void addMachineSlots() {
        // Slot 0: Tin input (c:ingots/tin)
        addCustomSlot(new Slot(machineSlots(), 0, 80, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return lightSource == null ? stack.is(TIN) : lightSource.acceptsTin(ItemResource.of(stack));
            }
        });
    }

    public int value(int i) { return values.get(i); }

    @Override
    public boolean stillValid(Player player) {
        return lightSource != null && panel!=null && lightSource.isFormed() && panelPos.equals(lightSource.getBlockPos().above()) && player.level().getBlockEntity(panelPos)==panel && panel.lightSource()==lightSource && lightSource.getLevel() == player.level()
                && !lightSource.isRemoved() && lightSource.getLevel().getBlockEntity(lightSource.getBlockPos()) == lightSource
                && player.distanceToSqr(panelPos.getX() + .5, panelPos.getY() + .5, panelPos.getZ() + .5) <= 64;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size() || lightSource == null || !stillValid(player)) return ItemStack.EMPTY;
        var slot = slots.get(index);
        var stack = slot.getItem();
        var original = stack.copy();
        if (index == 0) {
            if (!moveItemStackTo(stack, 1, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            if (!lightSource.acceptsTin(ItemResource.of(stack)) || !moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, original);
        lightSource.setChanged();
        return original;
    }
}