package me.chrr.camerapture.gui;

import me.chrr.camerapture.Camerapture;
import me.chrr.camerapture.item.AlbumItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

public class AlbumScreenHandler extends ScreenHandler {
    private final Inventory inventory;

    public AlbumScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(AlbumItem.SLOTS));
    }

    public AlbumScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory) {
        super(Camerapture.ALBUM_SCREEN_HANDLER, syncId);

        checkSize(inventory, AlbumItem.SLOTS);
        this.inventory = inventory;
        inventory.onOpen(playerInventory.player);

        // Album inventory
        for (int page = 0; page < AlbumItem.PAGES; page++) {
            for (int side = 0; side < 2; side++) {
                for (int y = 0; y < 3; y++) {
                    for (int x = 0; x < 2; x++) {
                        int i = page * 12 + side * 6 + y * 2 + x;
                        int dx = side * 131 + 22 + x * 56;
                        int dy = 27 + y * 32;

                        this.addSlot(new PictureSlot(inventory, i, dx, dy));
                    }
                }
            }
        }

        // Player inventory
        for (int x = 0; x < 9; x++) {
            for (int y = 0; y < 3; y++) {
                this.addSlot(new Slot(playerInventory, 9 + y * 9 + x, 60 + x * 18, 155 + y * 18));
            }
        }

        // Player hotbar
        for (int x = 0; x < 9; x++) {
            this.addSlot(new Slot(playerInventory, x, 60 + x * 18, 213));
        }
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return this.inventory.canPlayerUse(player);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slotId) {
        ItemStack itemStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(slotId);

        if (slot.hasStack()) {
            ItemStack stack = slot.getStack();
            itemStack = stack.copy();

            // If true, the shift-click is from the album to the player inventory.
            if (slotId < AlbumItem.SLOTS) {
                if (!this.insertItem(stack, AlbumItem.SLOTS, AlbumItem.SLOTS + 36, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.tryAddPicture(stack)) {
                return ItemStack.EMPTY;
            }

            // If we have inserted everything, we empty the original slot
            if (stack.isEmpty()) {
                slot.setStack(ItemStack.EMPTY);
            } else {
                slot.markDirty();
            }

            // If the stack count hasn't changed, we don't do anything.
            if (stack.getCount() == itemStack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTakeItem(player, stack);
        }

        return itemStack;
    }

    private boolean tryAddPicture(ItemStack stack) {
        if (!stack.isOf(Camerapture.PICTURE)) {
            return false;
        }

        // We put a single picture into the first free slot if it's available.
        for (int i = 0; i < AlbumItem.SLOTS; i++) {
            Slot slot = getSlot(i);
            if (!slot.hasStack()) {
                ItemStack picture = stack.split(1);
                slot.setStack(picture);
                return true;
            }
        }

        return false;
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        this.inventory.onClose(player);
    }
}