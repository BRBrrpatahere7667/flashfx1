package com.flashfx;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class FlashFxScreen extends Screen {
    public FlashFxScreen() {
        super(Text.literal("FlashFX Visuals"));
    }

    private static Text label(String name) {
        String pretty = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        return Text.literal(pretty + ": " + (FlashFxClient.FX.get(name) ? "ON" : "OFF"));
    }

    private static Text themeLabel() {
        return Text.literal("Theme color: " + FlashFxClient.THEME_NAMES[FlashFxClient.themeIdx]);
    }

    private static Text glassLabel() {
        int pct = Math.round(FlashFxClient.GLASS_ALPHA[FlashFxClient.glassIdx] * 100f / 255f);
        return Text.literal("Glass opacity: " + pct + "%");
    }

    @Override
    protected void init() {
        int w = 150, h = 20, gap = 4;
        int left = this.width / 2 - w - gap / 2;
        int top = 40;
        int i = 0;
        for (String name : FlashFxClient.FX.keySet()) {
            int x = left + (i % 2) * (w + gap);
            int y = top + (i / 2) * (h + gap);
            addDrawableChild(ButtonWidget.builder(label(name), b -> {
                FlashFxClient.FX.put(name, !FlashFxClient.FX.get(name));
                b.setMessage(label(name));
                FlashFxClient.save();
            }).dimensions(x, y, w, h).build());
            i++;
        }
        int rows = (i + 1) / 2;
        int ty = top + rows * (h + gap) + 6;
        addDrawableChild(ButtonWidget.builder(themeLabel(), b -> {
            FlashFxClient.themeIdx = (FlashFxClient.themeIdx + 1) % FlashFxClient.THEME.length;
            b.setMessage(themeLabel());
            FlashFxClient.save();
        }).dimensions(left, ty, w, h).build());
        addDrawableChild(ButtonWidget.builder(glassLabel(), b -> {
            FlashFxClient.glassIdx = (FlashFxClient.glassIdx + 1) % FlashFxClient.GLASS_ALPHA.length;
            b.setMessage(glassLabel());
            FlashFxClient.save();
        }).dimensions(left + w + gap, ty, w, h).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(this.width / 2 - 100, ty + h + gap + 6, 200, h).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFFFF);
    }
}
