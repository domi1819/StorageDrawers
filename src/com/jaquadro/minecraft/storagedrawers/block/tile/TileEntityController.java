package com.jaquadro.minecraft.storagedrawers.block.tile;

import com.jaquadro.minecraft.storagedrawers.api.inventory.IDrawerInventory;
import com.jaquadro.minecraft.storagedrawers.api.storage.IDrawer;
import com.jaquadro.minecraft.storagedrawers.api.storage.IDrawerGroup;
import com.jaquadro.minecraft.storagedrawers.api.storage.IDrawerGroupInteractive;
import com.jaquadro.minecraft.storagedrawers.api.storage.INetworked;
import com.jaquadro.minecraft.storagedrawers.block.BlockSlave;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.*;

public class TileEntityController extends TileEntity implements IDrawerGroup, ISidedInventory
{
    private static final int DEPTH_LIMIT = 12;
    private static final int[] emptySlots = new int[0];

    private static class StorageRecord
    {
        public IDrawerGroup storage;
        public boolean mark;
        public int invStorageSize;
        public int drawerStorageSize;
        public int distance = Integer.MAX_VALUE;

        public void clear () {
            storage = null;
            mark = false;
            invStorageSize = 0;
            drawerStorageSize = 0;
            distance = Integer.MAX_VALUE;
        }
    }

    private static class SlotRecord
    {
        public BlockCoord coord;
        public int slot;

        public SlotRecord (BlockCoord coord, int slot) {
            this.coord = coord;
            this.slot = slot;
        }
    }

    private Map<BlockCoord, StorageRecord> storage = new HashMap<BlockCoord, StorageRecord>();
    private List<SlotRecord> invSlotList = new ArrayList<SlotRecord>();
    private List<SlotRecord> drawerSlotList = new ArrayList<SlotRecord>();

    private int[] inventorySlots = new int[0];
    private int[] autoSides = new int[] { 0, 1 };
    private int direction;

    private int drawerSize = 0;

    private long lastClickTime;
    private UUID lastClickUUID;

    public TileEntityController () {
        invSlotList.add(new SlotRecord(null, 0));
        inventorySlots = new int[] { 0 };
    }

    public int getDirection () {
        return direction;
    }

    public void setDirection (int direction) {
        this.direction = direction % 6;

        autoSides = new int[] { 0, 1, ForgeDirection.OPPOSITES[direction], 2, 3 };

        if (direction == 2 || direction == 3) {
            autoSides[3] = 4;
            autoSides[4] = 5;
        }
    }

    public int interactPutItemsIntoInventory (EntityPlayer player) {
        if (inventorySlots.length == 0)
            updateCache();

        boolean dumpInventory = worldObj.getTotalWorldTime() - lastClickTime < 10 && player.getPersistentID().equals(lastClickUUID);
        int count = 0;

        for (int i = 0, n = drawerSlotList.size(); i < n; i++) {
            IDrawerGroup group = getGroupForDrawerSlot(i);
            if (group == null || !(group instanceof IDrawerGroupInteractive))
                continue;

            IDrawerGroupInteractive intGroup = (IDrawerGroupInteractive)group;

            int slot = getLocalDrawerSlot(i);
            if (!group.isDrawerEnabled(slot))
                continue;

            IDrawer drawer = group.getDrawer(slot);
            if (drawer == null || drawer.isEmpty())
                continue;

            if (dumpInventory)
                count += intGroup.interactPutCurrentInventoryIntoSlot(slot, player);
            else
                count += intGroup.interactPutCurrentItemIntoSlot(slot, player);
        }

        lastClickTime = worldObj.getTotalWorldTime();
        lastClickUUID = player.getPersistentID();

        return count;
    }

    private void resetCache () {
        storage.clear();
        invSlotList.clear();
        drawerSlotList.clear();
        drawerSize = 0;
    }

    public boolean isValidSlave (BlockCoord coord) {
        StorageRecord record = storage.get(coord);
        if (record == null || !record.mark)
            return false;

        return record.storage == null;
    }

    public void updateCache () {
        int preCount = inventorySlots.length;

        resetCache();

        populateNode(xCoord, yCoord, zCoord, 0);

        flattenLists();
        inventorySlots = sortInventorySlots();

        if (preCount != inventorySlots.length && (preCount == 0 || inventorySlots.length == 0)) {
            if (!worldObj.isRemote)
                markDirty();
        }
    }

    private int[] sortInventorySlots () {
        int index = 0;
        int emptyIndex = 0;
        int disabledIndex = 0;

        int[] slotMap = new int[invSlotList.size()];
        int[] emptyMap = new int[invSlotList.size()];
        int[] disabledMap = new int[invSlotList.size()];

        for (int i = 0, n = invSlotList.size(); i < n; i++) {
            IDrawerGroup group = getGroupForInvSlot(i);
            if (group == null) {
                disabledMap[disabledIndex++] = i;
                continue;
            }

            int localSlot = getLocalInvSlot(i);
            int drawerSlot = group.getDrawerInventory().getDrawerSlot(localSlot);
            if (!group.isDrawerEnabled(drawerSlot)) {
                disabledMap[disabledIndex++] = i;
                continue;
            }

            IDrawer drawer = group.getDrawer(drawerSlot);
            if (!drawer.isEmpty())
                slotMap[index++] = i;
            else
                emptyMap[emptyIndex++] = i;
        }

        for (int i = 0; i < emptyIndex; i++)
            slotMap[index++] = emptyMap[i];

        for (int i = 0; i < disabledIndex; i++)
            slotMap[index++] = disabledMap[i];

        return slotMap;
    }

    private boolean containsNullEntries (List<SlotRecord> list) {
        int nullCount = 0;
        for (int i = 0, n = list.size(); i < n; i++) {
            if (list.get(i) == null)
                nullCount++;
        }

        return nullCount > 0;
    }

    private void flattenLists () {
        if (containsNullEntries(invSlotList)) {
            List<SlotRecord> newInvSlotList = new ArrayList<SlotRecord>();

            for (int i = 0, n = invSlotList.size(); i < n; i++) {
                SlotRecord record = invSlotList.get(i);
                if (record != null)
                    newInvSlotList.add(record);
            }

            invSlotList = newInvSlotList;
        }

        if (containsNullEntries(drawerSlotList)) {
            List<SlotRecord> newDrawerSlotList = new ArrayList<SlotRecord>();

            for (int i = 0, n = drawerSlotList.size(); i < n; i++) {
                SlotRecord record = drawerSlotList.get(i);
                if (record != null)
                    newDrawerSlotList.add(record);
            }

            drawerSlotList = newDrawerSlotList;
        }
    }

    private void clearRecordInfo (BlockCoord coord, StorageRecord record) {
        record.clear();

        for (int i = 0; i < invSlotList.size(); i++) {
            SlotRecord slotRecord = invSlotList.get(i);
            if (slotRecord != null && coord.equals(slotRecord.coord))
                invSlotList.set(i, null);
        }

        for (int i = 0; i < drawerSlotList.size(); i++) {
            SlotRecord slotRecord = drawerSlotList.get(i);
            if (slotRecord != null && coord.equals(slotRecord.coord))
                drawerSlotList.set(i, null);
        }
    }

    private void updateRecordInfo (BlockCoord coord, StorageRecord record, TileEntity te) {
        if (te == null) {
            if (record.storage != null)
                clearRecordInfo(coord, record);

            return;
        }

        if (te instanceof TileEntityController) {
            if (record.storage == null && record.invStorageSize > 0)
                return;

            if (record.storage != null)
                clearRecordInfo(coord, record);

            record.storage = null;
            record.invStorageSize = 1;

            invSlotList.add(new SlotRecord(null, 0));
        }
        else if (te instanceof TileEntitySlave) {
            if (record.storage == null && record.invStorageSize == 0) {
                if (((TileEntitySlave) te).getController() == this)
                    return;
            }

            if (record.storage != null)
                clearRecordInfo(coord, record);

            record.storage = null;
            record.invStorageSize = 0;

            ((TileEntitySlave) te).bindController(xCoord, yCoord, zCoord);
        }
        else if (te instanceof IDrawerGroup) {
            IDrawerGroup group = (IDrawerGroup)te;
            if (record.storage == group)
                return;

            if (record.storage != null && record.storage != group)
                clearRecordInfo(coord, record);

            IDrawerInventory inventory = group.getDrawerInventory();
            if (inventory == null)
                return;

            record.storage = group;
            record.invStorageSize = inventory.getSizeInventory();
            record.drawerStorageSize = group.getDrawerCount();

            for (int i = 0, n = record.invStorageSize; i < n; i++)
                invSlotList.add(new SlotRecord(coord, i));

            for (int i = 0, n = record.drawerStorageSize; i < n; i++)
                drawerSlotList.add(new SlotRecord(coord, i));

            drawerSize += record.drawerStorageSize;
        }
        else {
            if (record.storage != null)
                clearRecordInfo(coord, record);
        }
    }

    private void populateNode (int x, int y, int z, int depth) {
        if (depth > DEPTH_LIMIT)
            return;

        Block block = worldObj.getBlock(x, y, z);
        if (!(block instanceof INetworked))
            return;

        BlockCoord coord = new BlockCoord(x, y, z);
        StorageRecord record = storage.get(coord);
        if (record == null) {
            record = new StorageRecord();
            storage.put(coord, record);
        }

        if (block instanceof BlockSlave) {
            ((BlockSlave) block).getTileEntitySafe(worldObj, x, y, z);
        }

        updateRecordInfo(coord, record, worldObj.getTileEntity(x, y, z));
        record.mark = true;

        if (record.distance > depth) {
            record.mark = true;
            record.distance = depth;
            populateNeighborNodes(x, y, z, depth + 1);
        }
    }

    private void populateNeighborNodes (int x, int y, int z, int depth) {
        populateNode(x - 1, y, z, depth);
        populateNode(x + 1, y, z, depth);
        populateNode(x, y - 1, z, depth);
        populateNode(x, y + 1, z, depth);
        populateNode(x, y, z - 1, depth);
        populateNode(x, y, z + 1, depth);
    }

    private IDrawerGroup getGroupForInvSlot (int invSlot) {
        if (invSlot >= invSlotList.size())
            return null;

        SlotRecord record = invSlotList.get(invSlot);
        if (record == null)
            return null;

        return getGroupForCoord(record.coord);
    }

    private IDrawerGroup getGroupForDrawerSlot (int drawerSlot) {
        if (drawerSlot >= drawerSlotList.size())
            return null;

        SlotRecord record = drawerSlotList.get(drawerSlot);
        if (record == null)
            return null;

        return getGroupForCoord(record.coord);
    }

    private IDrawerGroup getGroupForCoord (BlockCoord coord) {
        if (coord == null || !storage.containsKey(coord))
            return null;

        TileEntity te = worldObj.getTileEntity(coord.x(), coord.y(), coord.z());
        if (!(te instanceof IDrawerGroup)) {
            storage.remove(coord);
            return null;
        }

        return storage.get(coord).storage;
    }

    private int getLocalInvSlot (int invSlot) {
        if (invSlot >= invSlotList.size())
            return 0;

        SlotRecord record = invSlotList.get(invSlot);
        if (record == null)
            return 0;

        return record.slot;
    }

    private int getLocalDrawerSlot (int drawerSlot) {
        if (drawerSlot >= drawerSlotList.size())
            return 0;

        SlotRecord record = drawerSlotList.get(drawerSlot);
        if (record == null)
            return 0;

        return record.slot;
    }

    private IDrawerInventory getDrawerInventory (int invSlot) {
        IDrawerGroup group = getGroupForInvSlot(invSlot);
        if (group == null)
            return null;

        return group.getDrawerInventory();
    }

    @Override
    public void readFromNBT (NBTTagCompound tag) {
        super.readFromNBT(tag);

        setDirection(tag.getByte("Dir"));

        if (worldObj != null && !worldObj.isRemote)
            updateCache();
    }

    @Override
    public void writeToNBT (NBTTagCompound tag) {
        super.writeToNBT(tag);

        tag.setByte("Dir", (byte)direction);
    }

    @Override
    public Packet getDescriptionPacket () {
        NBTTagCompound tag = new NBTTagCompound();
        writeToNBT(tag);

        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 5, tag);
    }

    @Override
    public void onDataPacket (NetworkManager net, S35PacketUpdateTileEntity pkt) {
        readFromNBT(pkt.func_148857_g());
        getWorldObj().func_147479_m(xCoord, yCoord, zCoord); // markBlockForRenderUpdate
    }

    @Override
    public IDrawerInventory getDrawerInventory () {
        return null;
    }

    @Override
    public int getDrawerCount () {
        return drawerSlotList.size();
    }

    @Override
    public IDrawer getDrawer (int slot) {
        IDrawerGroup group = getGroupForDrawerSlot(slot);
        if (group == null)
            return null;

        return group.getDrawer(getLocalDrawerSlot(slot));
    }

    @Override
    public boolean isDrawerEnabled (int slot) {
        IDrawerGroup group = getGroupForDrawerSlot(slot);
        if (group == null)
            return false;

        return group.isDrawerEnabled(getLocalDrawerSlot(slot));
    }

    @Override
    public void markDirty () {
        for (StorageRecord record : storage.values()) {
            IDrawerGroup group = record.storage;
            if (group != null && group.getDrawerInventory() != null)
                group.markDirtyIfNeeded();
        }

        super.markDirty();
    }

    @Override
    public boolean markDirtyIfNeeded () {
        boolean synced = false;

        for (StorageRecord record : storage.values()) {
            IDrawerGroup group = record.storage;
            if (group != null && group.getDrawerInventory() != null)
                synced |= group.markDirtyIfNeeded();
        }

        if (synced)
            super.markDirty();

        return synced;
    }

    @Override
    public int[] getAccessibleSlotsFromSide (int side) {
        for (int aside : autoSides) {
            if (side == aside)
                return inventorySlots;
        }

        return emptySlots;
    }

    @Override
    public boolean canInsertItem (int slot, ItemStack stack, int side) {
        IDrawerInventory inventory = getDrawerInventory(slot);
        if (inventory == null)
            return false;

        return inventory.canInsertItem(getLocalInvSlot(slot), stack);
    }

    @Override
    public boolean canExtractItem (int slot, ItemStack stack, int side) {
        IDrawerInventory inventory = getDrawerInventory(slot);
        if (inventory == null)
            return false;

        return inventory.canExtractItem(getLocalInvSlot(slot), stack);
    }

    @Override
    public int getSizeInventory () {
        return inventorySlots.length;
    }

    @Override
    public ItemStack getStackInSlot (int slot) {
        IDrawerInventory inventory = getDrawerInventory(slot);
        if (inventory == null)
            return null;

        return inventory.getStackInSlot(getLocalInvSlot(slot));
    }

    @Override
    public ItemStack decrStackSize (int slot, int count) {
        IDrawerInventory inventory = getDrawerInventory(slot);
        if (inventory == null)
            return null;

        return inventory.decrStackSize(getLocalInvSlot(slot), count);
    }

    @Override
    public ItemStack getStackInSlotOnClosing (int slot) {
        IDrawerInventory inventory = getDrawerInventory(slot);
        if (inventory == null)
            return null;

        return inventory.getStackInSlotOnClosing(getLocalInvSlot(slot));
    }

    @Override
    public void setInventorySlotContents (int slot, ItemStack stack) {
        IDrawerInventory inventory = getDrawerInventory(slot);
        if (inventory == null)
            return;

        inventory.setInventorySlotContents(getLocalInvSlot(slot), stack);
        inventory.markDirty();
    }

    @Override
    public String getInventoryName () {
        // TODO
        return null;
    }

    @Override
    public boolean hasCustomInventoryName () {
        // TODO
        return false;
    }

    @Override
    public int getInventoryStackLimit () {
        return 64;
    }

    @Override
    public boolean isUseableByPlayer (EntityPlayer player) {
        return false;
    }

    @Override
    public void openInventory () { }

    @Override
    public void closeInventory () { }

    @Override
    public boolean isItemValidForSlot (int slot, ItemStack stack) {
        IDrawerInventory inventory = getDrawerInventory(slot);
        if (inventory == null)
            return false;

        return inventory.isItemValidForSlot(getLocalInvSlot(slot), stack);
    }
}
