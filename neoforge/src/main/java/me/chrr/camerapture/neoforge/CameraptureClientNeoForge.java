package me.chrr.camerapture.neoforge;

import me.chrr.camerapture.Camerapture;
import me.chrr.camerapture.CameraptureClient;
import me.chrr.camerapture.compat.ClothConfigScreenFactory;
import me.chrr.camerapture.gui.*;
import me.chrr.camerapture.item.CameraItem;
import me.chrr.camerapture.picture.ClientPictureStore;
import me.chrr.camerapture.picture.PictureTaker;
import me.chrr.camerapture.render.PictureFrameEntityRenderer;
import me.chrr.camerapture.render.PictureItemRenderer;
import me.chrr.camerapture.render.ShouldRenderPicture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.LogicalSide;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

@Mod(value = Camerapture.MOD_ID, dist = Dist.CLIENT)
public class CameraptureClientNeoForge {
    public CameraptureClientNeoForge(ModContainer mod) {
        Objects.requireNonNull(mod.getEventBus()).register(this);
        NeoForge.EVENT_BUS.register(new ClientEvents());

        if (ModList.get().isLoaded("cloth_config")) {
            mod.registerExtensionPoint(IConfigScreenFactory.class,
                    (container, parent) -> ClothConfigScreenFactory.create(parent));
        }
    }

    @SubscribeEvent
    public void registerContent(RegisterEvent event) {
        CameraptureClient.init();
    }

    @SubscribeEvent
    public void registerHandledScreens(RegisterMenuScreensEvent event) {
        event.register(Camerapture.PICTURE_FRAME_SCREEN_HANDLER, PictureFrameScreen::new);
        event.register(Camerapture.ALBUM_SCREEN_HANDLER, AlbumScreen::new);
        event.register(Camerapture.ALBUM_LECTERN_SCREEN_HANDLER, AlbumLecternScreen::new);
    }

    @SubscribeEvent
    public void registerPackets(RegisterPayloadHandlersEvent event) {
        CameraptureClient.registerPacketHandlers();
    }

    @SubscribeEvent
    public void registerItemModelConditions(RegisterConditionalItemModelPropertyEvent event) {
        event.register(Camerapture.id("should_render_picture"), ShouldRenderPicture.MAP_CODEC);
    }

    @SubscribeEvent
    public void registerItemRenderers(RegisterSpecialModelRendererEvent event) {
        event.register(Camerapture.id("picture"), PictureItemRenderer.Unbaked.MAP_CODEC);
    }

    @SubscribeEvent
    public void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(Camerapture.PICTURE_FRAME, PictureFrameEntityRenderer::new);
    }

    @SubscribeEvent
    public void registerClientExtensions(RegisterClientExtensionsEvent event) {
        // If we're holding a camera, we want to have the arm pose as if we're
        // charging a bow and arrow, so we hold the camera up.
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BipedEntityModel.ArmPose getArmPose(@NotNull LivingEntity entity, @NotNull Hand hand, @NotNull ItemStack stack) {
                return CameraItem.isActive(stack) ? BipedEntityModel.ArmPose.BOW_AND_ARROW : null;
            }
        }, Camerapture.CAMERA);
    }

    private static class ClientEvents {
        /// When attacking with an active camera, we want to take a picture.
        @SubscribeEvent
        public void onAttack(InputEvent.InteractionKeyMappingTriggered event) {
            if (!event.isAttack()) {
                return;
            }

            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            if (player == null) {
                return;
            }

            CameraItem.HeldCamera camera = CameraItem.find(player, true);
            if (camera == null) {
                return;
            }

            if (CameraItem.canTakePicture(player)) {
                PictureTaker.getInstance().takePicture();
            }

            event.setSwingHand(false);
            event.setCanceled(true);
        }

        /// Right-clicking on certain items should open client-side GUI's.
        @SubscribeEvent
        public ActionResult onUseItem(PlayerInteractEvent.RightClickItem event) {
            if (event.getSide() != LogicalSide.CLIENT) {
                return ActionResult.PASS;
            }

            ItemStack stack = event.getItemStack();
            PlayerEntity player = event.getEntity();
            return CameraptureClient.onUseItem(player, stack);
        }

        /// We need to notify the picture taker when the render tick ends.
        @SubscribeEvent
        public void onRenderTickEnd(RenderFrameEvent.Post event) {
            PictureTaker.getInstance().renderTickEnd();
        }

        /// Clear cache and reset the picture taker configuration when logging out of a world.
        @SubscribeEvent
        public void onDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
            ClientPictureStore.getInstance().clear();
            PictureTaker.getInstance().configureFromConfig();
            CameraItem.allowUploading = Camerapture.CONFIG_MANAGER.getConfig().server.allowUploading;
        }

        /// Hide the hand when the player is holding an active camera.
        @SubscribeEvent
        public void onRenderHand(RenderHandEvent event) {
            CameraItem.HeldCamera camera = CameraItem.find(MinecraftClient.getInstance().player, true);
            if (camera != null) {
                event.setCanceled(true);
            }
        }

        /// Hide the GUI and draw the camera overlay and viewfinder
        /// when the player is holding an active camera.
        @SubscribeEvent
        public void onRenderGui(RenderGuiLayerEvent.Pre event) {
            CameraItem.HeldCamera camera = CameraItem.find(MinecraftClient.getInstance().player, true);
            if (camera != null) {
                event.setCanceled(true);
            } else {
                PictureTaker.getInstance().zoomLevel = CameraptureClient.MIN_ZOOM;
                return;
            }

            if (event.getName() == VanillaGuiLayers.CROSSHAIR && !MinecraftClient.getInstance().options.hudHidden) {
                CameraViewFinder.drawCameraViewFinder(event.getGuiGraphics(), MinecraftClient.getInstance().textRenderer);
            }
        }

        /// If we have an active camera, scroll to zoom instead.
        @SubscribeEvent
        public void onScroll(InputEvent.MouseScrollingEvent event) {
            if (CameraItem.find(MinecraftClient.getInstance().player, true) != null) {
                PictureTaker.getInstance().zoom((float) (event.getScrollDeltaY() / 4f));
                event.setCanceled(true);
            }
        }

        /// Apply the camera zoom FOV if we have an active camera.
        @SubscribeEvent
        public void onFovModifier(ComputeFovModifierEvent event) {
            if (CameraItem.find(MinecraftClient.getInstance().player, true) != null) {
                event.setNewFovModifier(PictureTaker.getInstance().getFovModifier());
            }
        }
    }
}
