package me.chrr.camerapture.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public abstract class InGameScreen extends Screen {
    protected InGameScreen(Text title) {
        super(title);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        this.renderScreen(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
    }

    public abstract void renderScreen(DrawContext context, int mouseX, int mouseY, float delta);
}
