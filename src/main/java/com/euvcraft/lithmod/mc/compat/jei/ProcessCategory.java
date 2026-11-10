package com.euvcraft.lithmod.mc.compat.jei;

import com.euvcraft.lithmod.core.MaskData;
import com.euvcraft.lithmod.core.viewer.ViewerPage;
import com.euvcraft.lithmod.core.viewer.ViewerPage.Ingredient;
import com.euvcraft.lithmod.mc.registry.ModDataComponents;
import com.euvcraft.lithmod.mc.viewer.ViewerMachines;
import java.util.*;
import mezz.jei.api.gui.builder.*;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.*;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.constants.VanillaTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraft.world.level.material.Fluids;

/** Compact readable layout; overflow notes remain available as a complete hover tooltip. */
public final class ProcessCategory implements IRecipeCategory<ViewerPage> {
    public static final int WIDTH=266,HEIGHT=156;
    private final IRecipeType<ViewerPage> type;
    private final Item controller;
    private final IDrawable icon;
    ProcessCategory(String machine,IRecipeType<ViewerPage> type,IGuiHelper helper){this.type=type;controller=BuiltInRegistries.ITEM.getValue(Identifier.parse(ViewerMachines.CONTROLLERS.get(machine)));icon=helper.createDrawableIngredient(VanillaTypes.ITEM_STACK,new ItemStack(controller));}
    @Override public IRecipeType<ViewerPage> getRecipeType(){return type;}
    @Override public Component getTitle(){return new ItemStack(controller).getHoverName();}
    @Override public int getWidth(){return WIDTH;}
    @Override public int getHeight(){return HEIGHT;}
    @Override public IDrawable getIcon(){return icon;}
    @Override public Identifier getIdentifier(ViewerPage page){return Identifier.parse(page.id());}
    @Override public void setRecipe(IRecipeLayoutBuilder builder,ViewerPage page,IFocusGroup focuses){
        slots(builder,page.inputs(),RecipeIngredientRole.INPUT,6,18,6,page.machine());
        slots(builder,page.outputs(),RecipeIngredientRole.OUTPUT,146,18,6,page.machine());
        slots(builder,page.catalysts(),RecipeIngredientRole.RENDER_ONLY,6,78,12,page.machine());
        for(var ingredient:page.catalysts())if(ingredient.kind()==ViewerPage.Kind.ITEM)builder.addInvisibleIngredients(RecipeIngredientRole.INPUT).add(stack(ingredient));
    }
    private void slots(IRecipeLayoutBuilder builder,List<Ingredient> ingredients,RecipeIngredientRole role,int x,int y,int columns,String machine){
        for(int n=0;n<ingredients.size();n++){
            var ingredient=ingredients.get(n);var slot=builder.addSlot(role,x+18*(n%columns),y+18*(n/columns)).setStandardSlotBackground();
            if(!ingredient.label().isBlank())slot.addRichTooltipCallback((view,tooltip)->tooltip.add(Component.literal(ingredient.label())));
            switch(ingredient.kind()){
                case FLUID,GAS->{var fluid=BuiltInRegistries.FLUID.getValue(Identifier.parse(ingredient.id()));if(fluid!=null&&fluid!=Fluids.EMPTY)slot.add(fluid,ingredient.amount()).setFluidRenderer(Math.max(1000,ingredient.amount()),false,16,16);}
                case TAG->{var tag=TagKey.create(Registries.ITEM,Identifier.parse(ingredient.id()));var stacks=new ArrayList<ItemStack>();for(var holder:BuiltInRegistries.ITEM.getTagOrEmpty(tag))stacks.add(new ItemStack(holder.value(),ingredient.amount()));slot.addItemStacks(stacks);}
                case ITEM->{var stack=stack(ingredient);var mask=stack.get(ModDataComponents.MASK_DATA.get());if(mask!=null&&machine.equals("ebeam_mask_writer"))stack.set(ModDataComponents.MASK_DATA.get(),new MaskData(mask.dieType(),mask.tierIndex(),mask.usesRemaining(),2));slot.add(stack);}
            }
        }
    }
    public static ItemStack stack(Ingredient ingredient){
        var item=BuiltInRegistries.ITEM.getValue(Identifier.parse(ingredient.id()));var stack=new ItemStack(item,ingredient.amount());
        if(!ingredient.family().isBlank()&&ingredient.tier()>0&&ingredient.id().equals("lithmod:mask"))stack.set(ModDataComponents.MASK_DATA.get(),new MaskData(ingredient.family(),ingredient.tier(),Math.max(1,ingredient.uses()),ingredient.tier()>=7?3:ingredient.tier()>=3?2:1));
        if(ingredient.cores()>0)stack.set(ModDataComponents.CORE_COUNT.get(),ingredient.cores());return stack;
    }
    private static String cost(ViewerPage page){return (page.ticks()>0?page.ticks()+" ticks":"Variable time")+" | "+(page.fePerTick()>0?page.fePerTick()+" FE/t":"FE: see notes");}
    @Override public void draw(ViewerPage page,IRecipeSlotsView slots,GuiGraphicsExtractor graphics,double mouseX,double mouseY){
        var font=Minecraft.getInstance().font;
        graphics.text(font,Component.translatable("jei.lithmod.inputs"),6,4,0xff404040,false);graphics.text(font,Component.translatable("jei.lithmod.outputs"),146,4,0xff404040,false);
        graphics.text(font,Component.literal("→"),126,26,0xff404040,false);
        if(!page.catalysts().isEmpty())graphics.text(font,Component.translatable("jei.lithmod.catalysts"),6,64,0xff404040,false);
        graphics.text(font,font.plainSubstrByWidth(cost(page),250),6,104,0xff404040,false);
        int line=0;outer:for(var note:page.notes())for(var text:font.split(Component.literal(note),250)){if(line>=2)break outer;graphics.text(font,text,6,118+line++*10,0xff404040,false);}
        if(!page.notes().isEmpty())graphics.text(font,Component.translatable("jei.lithmod.details"),6,144,0xff606060,false);
    }
    @Override public void getTooltip(ITooltipBuilder tooltip,ViewerPage page,IRecipeSlotsView slots,double x,double y){if(y>=102){tooltip.add(Component.literal(cost(page)));for(var note:page.notes())tooltip.add(Component.literal(note));}}
}
