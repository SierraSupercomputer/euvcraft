package com.euvcraft.lithmod.mc.multiblock.linear;
import com.euvcraft.lithmod.mc.machine.screen.AdvancedMachineScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
public final class LinearStageScreen extends AdvancedMachineScreen<LinearStageMenu>{
    private Button repair;
    public LinearStageScreen(LinearStageMenu menu,Inventory inventory,Component title){super(menu,inventory,title,258);}
    @Override protected boolean formed(){return menu.formed();}
    @Override protected boolean cabinet(){return !menu.panel();}
    @Override protected void init(){super.init();if(menu.panel())repair=addRenderableWidget(Button.builder(Component.translatable("button.lithmod.linear.repair"),button->{if(minecraft!=null&&minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,1);}).bounds(leftPos+8,topPos+228,160,20).build());}
    @Override protected void containerTick(){super.containerTick();if(repair!=null)repair.active=menu.formed()&&menu.requiresRepair();}
    @Override protected void extractLabels(GuiGraphicsExtractor graphics,int x,int y){super.extractLabels(graphics,x,y);int i=menu.status();var states=LinearStageBlockEntity.Status.values();line(graphics,Component.translatable("state.lithmod.linear."+(i>=0&&i<states.length?states[i].name().toLowerCase(java.util.Locale.ROOT):"config")),8,194,160);line(graphics,Component.translatable("label.lithmod.linear.upw",menu.water(),menu.waterCapacity()),8,206,160);line(graphics,Component.translatable(menu.requiresRepair()?"label.lithmod.linear.repair":"label.lithmod.linear.upstream"),8,218,160);}
}
