package me.chrr.camerapture.screen;

import me.chrr.camerapture.Camerapture;
import me.chrr.camerapture.item.PictureItem;
import me.chrr.camerapture.picture.ClientPictureStore;
import me.chrr.camerapture.util.PictureDrawingUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

public class PictureScreen extends InGameScreen {
    public static final int BAR_WIDTH = 360;
    public static final int BORDER_THICKNESS = 24;

    private final List<ItemStack> pictures;
    private int index = 0;

    private ClientPictureStore.Picture picture;

    private Text pageNumber;
    private Text customName;

    private boolean ctrlHeld = false;

    public PictureScreen(List<ItemStack> pictures) {
        super(Text.translatable("item.camerapture.picture"));
        this.pictures = pictures;

        forceRefresh();
    }

    @Override
    protected void init() {
        super.init();

        if (!isSinglePicture()) {
            int barX = width / 2 - BAR_WIDTH / 2;
            int barY = height - BORDER_THICKNESS - 20;

            addDrawableChild(ButtonWidget.builder(Text.of("←"), button -> {
                        this.index = Math.floorMod(this.index - 1, pictures.size());
                        forceRefresh();
                    })
                    .dimensions(barX, barY, 20, 20)
                    .build());
            addDrawableChild(ButtonWidget.builder(Text.of("→"), button -> {
                        this.index = Math.floorMod(this.index + 1, pictures.size());
                        forceRefresh();
                    })
                    .dimensions(barX + BAR_WIDTH - 20, barY, 20, 20)
                    .build());
        }
    }

    @Override
    public void renderScreen(DrawContext context, int mouseX, int mouseY, float delta) {
        // Drawing the item name and page number
        if (!isSinglePicture()) {
            int barY = height - BORDER_THICKNESS - 20 / 2;

            int pageNumberX = width / 2 - this.textRenderer.getWidth(this.pageNumber) / 2;
            if (this.customName != null) {
                int nameX = width / 2 - this.textRenderer.getWidth(this.customName) / 2;
                context.drawText(this.textRenderer, this.customName, nameX, barY - 1 - textRenderer.fontHeight, 0xffffff, false);
                context.drawText(this.textRenderer, this.pageNumber, pageNumberX, barY + 1, 0xffffff, false);
            } else {
                context.drawText(this.textRenderer, this.pageNumber, pageNumberX, barY - textRenderer.fontHeight / 2, 0xffffff, false);
            }
        }

        if (this.picture == null) {
            return;
        }

        if (this.ctrlHeld) {
            Text text = Text.translatable("text.camerapture.save_as").formatted(Formatting.GRAY);
            int tw = this.textRenderer.getWidth(text);
            context.drawText(this.textRenderer, text, width / 2 - tw / 2, BORDER_THICKNESS - textRenderer.fontHeight - 2, 0xffffff, false);
        }

        // Drawing the picture
        int bottomOffset = isSinglePicture() ? 0 : 24;
        PictureDrawingUtil.drawPicture(context, textRenderer, picture, BORDER_THICKNESS, BORDER_THICKNESS, width - BORDER_THICKNESS * 2, height - BORDER_THICKNESS * 2 - bottomOffset);
    }

    @Nullable
    public NativeImage getNativeImage() {
        if (this.client == null ||
                this.picture == null
                || this.picture.getStatus() != ClientPictureStore.Status.SUCCESS) {
            return null;
        }

        AbstractTexture texture = client.getTextureManager().getTexture(this.picture.getIdentifier());
        if (!(texture instanceof NativeImageBackedTexture backedTexture)) {
            return null;
        }

        return backedTexture.getImage();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_LEFT_CONTROL) {
            this.ctrlHeld = true;
        } else if (keyCode == GLFW.GLFW_KEY_S && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            // On Ctrl-S, we prompt the user to save the image.
            NativeImage image = this.getNativeImage();
            if (image != null) {
                saveAs(image);
                return true;
            }
        } else if (keyCode == GLFW.GLFW_KEY_LEFT) {
            this.index = Math.floorMod(this.index - 1, pictures.size());
            forceRefresh();
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            this.index = Math.floorMod(this.index + 1, pictures.size());
            forceRefresh();
            return true;
        }

        // We leave the usual handling to last, so we override the arrow keys controlling focus.
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_LEFT_CONTROL) {
            this.ctrlHeld = false;
        }

        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    //? if >=1.20.4 {
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        //?} else
    /*public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {*/
        this.index = Math.floorMod(this.index - (int) verticalAmount, pictures.size());
        forceRefresh();
        return true;
    }

    private void forceRefresh() {
        ItemStack stack = pictures.get(index);
        UUID uuid = PictureItem.getUuid(stack);
        this.picture = ClientPictureStore.getInstance().ensureServerPicture(uuid);

        this.pageNumber = Text.literal((index + 1) + " / " + this.pictures.size()).formatted(Formatting.GRAY);
        this.customName = stack.hasCustomName() ? stack.getName() : null;
    }

    private boolean isSinglePicture() {
        return pictures.size() == 1;
    }

    private void saveAs(NativeImage image) {
        new Thread(() -> {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filter = stack.mallocPointer(1);
                filter.put(stack.UTF8("png"));
                filter.flip();

                String path = TinyFileDialogs.tinyfd_saveFileDialog("Save Picture", "picture.png", filter, "*.png");
                if (path == null) {
                    return;
                }

                try {
                    image.writeTo(Path.of(path));
                } catch (IOException e) {
                    Camerapture.LOGGER.error("failed to save picture to disk", e);
                }
            }
        }, "Save prompter").start();
    }
}
