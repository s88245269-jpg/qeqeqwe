package com.mortgagemod;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(MortgageMod.MODID)
public class MortgageMod {
    public static final String MODID = "mortgage";

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final RegistryObject<Block> DEED_BLOCK = BLOCKS.register("deed_block",
            () -> new DeedBlock(BlockBehaviour.Properties.of().mapColor(MapColor.EMERALD)
                    .strength(5.0f, 3600000.0f).sound(SoundType.METAL)));
    public static final RegistryObject<Item> DEED_ITEM = ITEMS.register("deed_block",
            () -> new BlockItem(DEED_BLOCK.get(), new Item.Properties()));
    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main",
            () -> CreativeModeTab.builder().title(Component.translatable("itemGroup.mortgage"))
                    .icon(() -> new ItemStack(DEED_ITEM.get()))
                    .displayItems((params, out) -> out.accept(DEED_ITEM.get())).build());

    public MortgageMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(bus);
        ITEMS.register(bus);
        TABS.register(bus);
    }
}
