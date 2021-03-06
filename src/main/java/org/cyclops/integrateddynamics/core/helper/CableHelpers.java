package org.cyclops.integrateddynamics.core.helper;

import com.google.common.collect.Lists;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import org.cyclops.cyclopscore.helper.ItemStackHelpers;
import org.cyclops.cyclopscore.helper.TileHelpers;
import org.cyclops.integrateddynamics.api.block.IFacadeable;
import org.cyclops.integrateddynamics.api.block.cable.ICable;
import org.cyclops.integrateddynamics.api.block.cable.ICableFakeable;
import org.cyclops.integrateddynamics.api.network.INetwork;
import org.cyclops.integrateddynamics.api.network.INetworkCarrier;
import org.cyclops.integrateddynamics.api.network.INetworkElement;
import org.cyclops.integrateddynamics.api.network.INetworkElementProvider;
import org.cyclops.integrateddynamics.api.part.IPartContainer;
import org.cyclops.integrateddynamics.api.part.IPartType;
import org.cyclops.integrateddynamics.api.path.IPathElement;
import org.cyclops.integrateddynamics.capability.cable.CableConfig;
import org.cyclops.integrateddynamics.capability.cable.CableFakeableConfig;
import org.cyclops.integrateddynamics.capability.facadeable.FacadeableConfig;
import org.cyclops.integrateddynamics.capability.path.PathElementConfig;
import org.cyclops.integrateddynamics.core.network.event.NetworkInitializedEvent;
import org.cyclops.integrateddynamics.item.ItemBlockCable;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * Helpers related to cables.
 * @author rubensworks
 */
public class CableHelpers {

    /**
     * Get the cable capability at the given position.
     * @param world The world.
     * @param pos The position.
     * @return The cable capability, or null if not present.
     */
    public static @Nullable ICable getCable(IBlockAccess world, BlockPos pos) {
        return TileHelpers.getCapability(world, pos, CableConfig.CAPABILITY);
    }

    /**
     * Get the fakeable cable capability at the given position.
     * @param world The world.
     * @param pos The position.
     * @return The fakeable cable capability, or null if not present.
     */
    public static @Nullable ICableFakeable getCableFakeable(IBlockAccess world, BlockPos pos) {
        return TileHelpers.getCapability(world, pos, CableFakeableConfig.CAPABILITY);
    }

    /**
     * Get the path element capability at the given position.
     * @param world The world.
     * @param pos The position.
     * @return The path element capability, or null if not present.
     */
    public static @Nullable IPathElement getPathElement(IBlockAccess world, BlockPos pos) {
        return TileHelpers.getCapability(world, pos, PathElementConfig.CAPABILITY);
    }

    /**
     * Request to update the cable connections of all neighbours of the given position.
     * @param world The world.
     * @param pos The center position.
     */
    public static void updateConnectionsNeighbours(IBlockAccess world, BlockPos pos) {
        for(EnumFacing side : EnumFacing.VALUES) {
            updateConnections(world, pos.offset(side));
        }
    }

    /**
     * Request to update the cable connections at the given position.
     * @param world The world.
     * @param pos The position.
     */
    public static void updateConnections(IBlockAccess world, BlockPos pos) {
        ICable cable = getCable(world, pos);
        if(cable != null) {
            cable.updateConnections();
        }
    }

    /**
     * Check if there is a cable at the given position AND if it is connected for the given side.
     * @param world The world.
     * @param pos The position.
     * @param side The side to check a connection for.
     * @return If the cable is connected.
     */
    public static boolean isCableConnected(IBlockAccess world, BlockPos pos, EnumFacing side) {
        ICable cable = getCable(world, pos);
        return cable != null && cable.isConnected(side);
    }

    /**
     * Check if one side of the given cable at the given position can connect to the given side.
     * To be used when the cable connections are being updated.
     * This will check if the origin cable can connect to that side,
     * if there is a cable at the target side and if that cable can connect with this side.
     * This ignores any current cable connections.
     * @param world The world.
     * @param pos The center position.
     * @param side The side from the center position to check.
     * @param originCable The cable at the center position.
     * @return If it can connect.
     */
    public static boolean canCableConnectTo(IBlockAccess world, BlockPos pos, EnumFacing side, ICable originCable) {
        BlockPos neighbourPos = pos.offset(side);
        ICable neighbourCable = getCable(world, neighbourPos);
        return neighbourCable != null
                && originCable.canConnect(neighbourCable, side)
                && neighbourCable.canConnect(originCable, side.getOpposite());
    }

    /**
     * Check if the given position is not a fake cable.
     * This can mean that there is no cable at all!
     * But if there is a cable, this method will return true only if it is a real cable.
     * @param world The world.
     * @param pos The position.
     * @return If there is no fake cable.
     */
    public static boolean isNoFakeCable(IBlockAccess world, BlockPos pos) {
        ICableFakeable cableFakeable = getCableFakeable(world, pos);
        return cableFakeable == null || cableFakeable.isRealCable();
    }

    /**
     * Actions to be performed when a player right clicks on a cable.
     * @param world The world  of the cable.
     * @param pos The position of the cable.
     * @param state The blockstate of the cable.
     * @param player The player activating the cable.
     * @param heldItem The item with which the player is right-clicking.
     * @param side The side of the block the player right-clicked on.
     * @param cableConnectionHit The side identifying the cable connection that is being activated,
     *                           this will be null if the center part of the cable is activated.
     * @return True if further actions should halt.
     */
    public static boolean onCableActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                           ItemStack heldItem, EnumFacing side, @Nullable EnumFacing cableConnectionHit) {
        ICable cable = CableHelpers.getCable(world, pos);
        if(WrenchHelpers.isWrench(player, heldItem, world, pos, side)) {
            if (player.isSneaking()) {
                removeCable(world, pos, player);
            } else if (cableConnectionHit != null) {
                // Disconnect cable side

                // Store the disconnection in the part entity
                cable.disconnect(cableConnectionHit);

                // Signal changes
                cable.updateConnections();
                CableHelpers.updateConnectionsNeighbours(world, pos);

                // Reinit the networks for this block and the disconnected neighbour.
                NetworkHelpers.initNetwork(world, pos);
                NetworkHelpers.initNetwork(world, pos.offset(cableConnectionHit));
                return true;
            } else if (cableConnectionHit == null) {
                // Reconnect cable side
                BlockPos neighbourPos = pos.offset(side);
                ICable neighbourCable = CableHelpers.getCable(world, neighbourPos);
                if(neighbourCable != null && !cable.isConnected(side) &&
                        (cable.canConnect(neighbourCable, side) || neighbourCable.canConnect(cable, side.getOpposite()))
                        ) {
                    // Notify the reconnection in the part entity of this and the neighbour block,
                    // since we don't know in which one the disconnection was made.
                    cable.reconnect(side);
                    neighbourCable.reconnect(side.getOpposite());

                    // Signal changes
                    cable.updateConnections();
                    CableHelpers.updateConnectionsNeighbours(world, pos);

                    // Reinit the networks for this block and the connected neighbour.
                    NetworkHelpers.initNetwork(world, pos);
                    NetworkHelpers.initNetwork(world, neighbourPos);
                }
                return true;
            }
            return true;
        }
        return false;
    }

    /**
     * This should be called when a cable is added.
     * This method automatically notifies the neighbours and (re-)initializes the network if this cable carries one.
     * @param world The world.
     * @param pos The position.
     * @param placer The entity who placed the cable.
     */
    public static void onCableAdded(World world, BlockPos pos, @Nullable EntityLivingBase placer) {
        CableHelpers.updateConnectionsNeighbours(world, pos);
        if(!world.isRemote) {
            INetwork network = NetworkHelpers.initNetwork(world, pos);
            MinecraftForge.EVENT_BUS.post(new NetworkInitializedEvent(network, world, pos, placer));
        }
    }

    /**
     * This should be called when a cable is being removed, while the part entity is still present.
     * This method won't do anything when called client-side.
     * @param world The world.
     * @param pos The position.
     * @param dropMainElement If the main part element should be dropped.
     * @return If the cable was removed from the network.
     */
    public static boolean onCableRemoving(World world, BlockPos pos, boolean dropMainElement) {
        if (!world.isRemote && CableHelpers.isNoFakeCable(world, pos)) {
            INetworkCarrier networkCarrier = NetworkHelpers.getNetworkCarrier(world, pos);

            // Get all drops from the network elements this cable provides.
            List<ItemStack> itemStacks = Lists.newLinkedList();
            INetworkElementProvider networkElementProvider = NetworkHelpers.getNetworkElementProvider(world, pos);
            if (networkElementProvider != null) {
                for (INetworkElement networkElement : networkElementProvider.createNetworkElements(world, pos)) {
                    networkElement.addDrops(itemStacks, dropMainElement);
                }
                for (ItemStack itemStack : itemStacks) {
                    Block.spawnAsEntity(world, pos, itemStack);
                }
            }

            // If the cable has a network, remove it from the network.
            if(networkCarrier != null && networkCarrier.getNetwork() != null) {
                IPathElement pathElement = getPathElement(world, pos);
                INetwork network = networkCarrier.getNetwork();
                networkCarrier.setNetwork(null);
                return network.removePathElement(pathElement);
            }
        }
        return true;
    }

    /**
     * This should be called AFTER a cable is removed, at this stage the part entity will not exist anymore.
     * This method won't do anything when called client-side.
     * @param world The world.
     * @param pos The position.
     * @return If the cable was removed from the network.
     */
    public static boolean onCableRemoved(World world, BlockPos pos) {
        updateConnectionsNeighbours(world, pos);
        if (!world.isRemote) {
            // Reinit neighbouring networks.
            for(EnumFacing side : EnumFacing.VALUES) {
                BlockPos sidePos = pos.offset(side);
                NetworkHelpers.initNetwork(world, sidePos);
            }
        }
        return true;
    }

    /**
     * Remove a cable.
     * This will automatically handle sounds, drops,
     * fakeable cables, network element removal and network (re)intialization.
     * @param world The world.
     * @param pos The position.
     * @param player The player removing the cable or null.
     */
    public static void removeCable(World world, BlockPos pos, @Nullable EntityPlayer player) {
        ICable cable = getCable(world, pos);
        ICableFakeable cableFakeable = getCableFakeable(world, pos);
        IPartContainer partContainer = PartHelpers.getPartContainer(world, pos);
        IBlockState blockState = world.getBlockState(pos);
        if (cable == null) return;

        CableHelpers.onCableRemoving(world, pos, false);
        // If the cable has no parts or is not fakeable, remove the block,
        // otherwise mark the cable as being fake.
        if (cableFakeable == null || partContainer == null || !partContainer.hasParts()) {
            cable.destroy();
        } else {
            cableFakeable.setRealCable(false);
        }
        if (player == null) {
            ItemStackHelpers.spawnItemStack(world, pos, cable.getItemStack());
        } else if (!player.capabilities.isCreativeMode) {
            ItemStackHelpers.spawnItemStackToPlayer(world, pos, cable.getItemStack(), player);
        }
        CableHelpers.onCableRemoved(world, pos);

        ItemBlockCable.playBreakSound(world, pos, blockState);
    }

    /**
     * Check if the target has a facade.
     * @param world The world.
     * @param pos The position.
     * @return If it has a facade.
     */
    public static boolean hasFacade(IBlockAccess world, BlockPos pos) {
        IFacadeable facadeable = TileHelpers.getCapability(world, pos, null, FacadeableConfig.CAPABILITY);
        return facadeable != null && facadeable.hasFacade();
    }

    /**
     * Get the target's facade
     * @param world The world.
     * @param pos The position.
     * @return The facade or null.
     */
    public static @Nullable IBlockState getFacade(IBlockAccess world, BlockPos pos) {
        IFacadeable facadeable = TileHelpers.getCapability(world, pos, null, FacadeableConfig.CAPABILITY);
        return facadeable != null ? facadeable.getFacade() : null;
    }

    public static boolean isLightTransparent(IBlockAccess world, BlockPos pos) {
        IPartContainer partContainer = PartHelpers.getPartContainer(world, pos);
        if (partContainer != null) {
            for (Map.Entry<EnumFacing, IPartType<?, ?>> entry : partContainer.getParts().entrySet()) {
                IPartType part = entry.getValue();
                if (part.forceLightTransparency(partContainer.getPartState(entry.getKey()))) {
                    return true;
                }
            }
        }
        return false;
    }
}
