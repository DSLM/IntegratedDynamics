package org.cyclops.integrateddynamics.item;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.cyclops.cyclopscore.config.configurable.ConfigurableItem;
import org.cyclops.cyclopscore.config.extendedconfig.ExtendedConfig;
import org.cyclops.integrateddynamics.IntegratedDynamics;
import org.cyclops.integrateddynamics.core.item.IVariableFacade;
import org.cyclops.integrateddynamics.core.item.IVariableFacadeHandlerRegistry;

import java.util.List;

/**
 * Item for storing variable references.
 * @author rubensworks
 */
public class ItemVariable extends ConfigurableItem {

    private static ItemVariable _instance = null;

    /**
     * Get the unique instance.
     * @return The instance.
     */
    public static ItemVariable getInstance() {
        return _instance;
    }

    /**
     * Make a new item instance.
     *
     * @param eConfig Config for this blockState.
     */
    public ItemVariable(ExtendedConfig eConfig) {
        super(eConfig);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(ItemStack itemStack, EntityPlayer entityPlayer, List list, boolean par4) {
        getVariableFacade(itemStack).addInformation(list, entityPlayer);
        super.addInformation(itemStack, entityPlayer, list, par4);
    }

    public IVariableFacade getVariableFacade(ItemStack itemStack) {
        return IntegratedDynamics._instance.getRegistryManager().getRegistry(IVariableFacadeHandlerRegistry.class).
                handle(itemStack.getTagCompound());
    }

}