package me.chrr.camerapture.fabric;

import me.chrr.camerapture.Camerapture;
import me.chrr.camerapture.CameraptureClient;
import me.chrr.camerapture.gui.*;
import me.chrr.camerapture.item.AlbumItem;
import me.chrr.camerapture.item.CameraItem;
import me.chrr.camerapture.item.PictureItem;
import me.chrr.camerapture.picture.ClientPictureStore;
import me.chrr.camerapture.picture.PictureTaker;
import me.chrr.camerapture.render.PictureFrameEntityRenderer;
import me.chrr.camerapture.render.PictureItemRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.item.ItemStack;
import net.minecraft.util.TypedActionResult;

import java.util.List;

public class CameraptureClientFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        this.registerClientContent();
        this.registerClientEvents();
        CameraptureClient.registerPacketHandlers();

        CameraptureClient.init();
    }

    public void registerClientContent() {
        // Picture
        ModelPredicateProviderRegistry.register(Camerapture.PICTURE, Camerapture.id("should_render_picture"),
                (stack, world, entity, seed) -> PictureItemRenderer.canRender(stack) ? 1f : 0f);

        // Picture Frame
        EntityRendererRegistry.register(Camerapture.PICTURE_FRAME, PictureFrameEntityRenderer::new);
        HandledScreens.register(Camerapture.PICTURE_FRAME_SCREEN_HANDLER, PictureFrameScreen::new);

        // Album
        HandledScreens.register(Camerapture.ALBUM_SCREEN_HANDLER, AlbumScreen::new);
        HandledScreens.register(Camerapture.ALBUM_LECTERN_SCREEN_HANDLER, AlbumLecternScreen::new);
    }

    public void registerClientEvents() {
        // When attacking with an active camera, we want to take a picture.
        ClientPreAttackCallback.EVENT.register((client, player, clickCount) -> {
            CameraItem.HeldCamera camera = CameraItem.find(player, true);
            if (camera == null) {
                return false;
            }

            if (CameraItem.canTakePicture(player)) {
                PictureTaker.getInstance().takePicture();
            }

            return true;
        });

        // Right-clicking on certain items should open client-side GUI's.
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            MinecraftClient client = MinecraftClient.getInstance();

            if (!world.isClient()) {
                return TypedActionResult.pass(stack);
            }

            if (client.player != player) {
                return TypedActionResult.pass(stack);
            }

            if (stack.isOf(Camerapture.PICTURE)) {
                // Right-clicking a picture item should open the picture screen.
                if (PictureItem.getPictureData(stack) != null) {
                    client.executeSync(() -> client.setScreen(new PictureScreen(List.of(stack))));
                    return TypedActionResult.success(stack);
                }
            } else if (stack.isOf(Camerapture.ALBUM) && !player.isSneaking()) {
                // Right-clicking the album should open the gallery screen.
                List<ItemStack> pictures = AlbumItem.getPictures(stack);
                if (!pictures.isEmpty()) {
                    client.executeSync(() -> client.setScreen(new PictureScreen(pictures)));
                    return TypedActionResult.success(stack);
                }
            } else if (CameraItem.allowUploading
                    && player.isSneaking()
                    && stack.isOf(Camerapture.CAMERA)
                    && !CameraItem.isActive(stack)
                    && !player.getItemCooldownManager().isCoolingDown(Camerapture.CAMERA)) {
                // Shift-right clicking the camera should open the upload screen.
                client.executeSync(() -> client.setScreen(new UploadScreen()));
                return TypedActionResult.success(stack);
            }

            return TypedActionResult.pass(stack);
        });

        // Clear cache and reset the picture taker configuration when logging out of a world.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientPictureStore.getInstance().clear();
            PictureTaker.getInstance().configureFromConfig();
            CameraItem.allowUploading = Camerapture.CONFIG_MANAGER.getConfig().server.allowUploading;
        });
    }
}
