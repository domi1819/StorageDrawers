package com.jaquadro.minecraft.storagedrawers.core.api;
/*
import com.jaquadro.minecraft.storagedrawers.api.pack.BlockConfiguration;
import com.jaquadro.minecraft.storagedrawers.api.pack.IPackBlockFactory;
import com.jaquadro.minecraft.storagedrawers.api.pack.IPackDataResolver;
import com.jaquadro.minecraft.storagedrawers.block.pack.BlockDrawersPack;
import com.jaquadro.minecraft.storagedrawers.block.pack.BlockTrimPack;
import com.jaquadro.minecraft.storagedrawers.integration.refinedrelocation.SortingBlockRegistry;
import com.jaquadro.minecraft.storagedrawers.item.pack.ItemDrawersPack;
import com.jaquadro.minecraft.storagedrawers.item.pack.ItemTrimPack;
import net.minecraft.block.Block;
import net.minecraftforge.fml.common.registry.GameRegistry;

public class PackFactory implements IPackBlockFactory
{
    @Override
    public Block createBlock (BlockConfiguration blockConfig, IPackDataResolver dataResolver) {
        switch (blockConfig.getBlockType()) {
            case Drawers:
                return new BlockDrawersPack(dataResolver, blockConfig.getDrawerCount(), blockConfig.isHalfDepth());
            case DrawersSorting:
                if (IntegrationRegistry.instance().isModLoaded("RefinedRelocation"))
                    return new BlockSortingDrawersPack(dataResolver, blockConfig.getDrawerCount(), blockConfig.isHalfDepth());
                return null;
            case Trim:
                return new BlockTrimPack(dataResolver);
        }

        return null;
    }

    private static String getConfigName (BlockConfiguration blockConfig) {
        if (blockConfig.getDrawerCount() == 1)
            return "fullDrawers1";
        if (blockConfig.getDrawerCount() == 2 && !blockConfig.isHalfDepth())
            return "fullDrawers2";
        if (blockConfig.getDrawerCount() == 4 && !blockConfig.isHalfDepth())
            return "fullDrawers4";
        if (blockConfig.getDrawerCount() == 2 && blockConfig.isHalfDepth())
            return "halfDrawers2";
        if (blockConfig.getDrawerCount() == 4 && blockConfig.isHalfDepth())
            return "halfDrawers4";

        return "";
    }

    @Override
    public void registerBlock (Block block, String name) {
        if (block instanceof BlockDrawersPack)
            GameRegistry.registerBlock(block, ItemDrawersPack.class, name);
            OreDictionary.registerOre("drawerBasic", new ItemStack(block, 1, OreDictionary.WILDCARD_VALUE));
            StorageDrawers.proxy.registerDrawer(block);
        }
        else if (block instanceof BlockSortingDrawersPack) {
            GameRegistry.registerBlock(block, ItemSortingDrawersPack.class, name);
            StorageDrawers.proxy.registerDrawer(block);
        }
        else if (block instanceof BlockTrimPack)
            GameRegistry.registerBlock(block, ItemTrimPack.class, name);
    }

    @Override
    public void bindSortingBlock (Block basicBlock, Block sortingBlock) {
        SortingBlockRegistry.register(basicBlock, sortingBlock);
    }

    @Override
    public void hideBlock (String blockID) {
        //NEIStorageDrawersConfig.hideBlock(blockID);
    }
}
*/
