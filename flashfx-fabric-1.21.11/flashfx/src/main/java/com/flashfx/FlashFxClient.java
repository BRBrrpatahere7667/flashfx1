package com.flashfx;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FlashFxClient implements ClientModInitializer {
    // Toggle with /flashfx <name>
    static final Map<String, Boolean> FX = new LinkedHashMap<>();
    static {
        FX.put("jump", true);   // shockwave ring + dust burst on jump
        FX.put("hit", true);    // particle burst when you hit an entity
        FX.put("trail", true);  // rainbow trail while moving
        FX.put("bright", false);// fullbright (client-side night vision)
        FX.put("hud", true);    // rainbow HUD text
        FX.put("halo", true);   // rotating gold halo above head
        FX.put("wings", true);  // white feather wings behind you
        FX.put("hat", false);   // cone hat made of particles
        FX.put("petals", true); // falling cherry petals around you
        FX.put("stardust", true);// floating sparkles around you
        FX.put("ghost", false); // outlined silhouette + sparkles (no mixin)
        FX.put("glass", false); // translucent glass player skin (render mixin)
    }

    static final String[] THEME_NAMES = {"Cyan", "Gold", "Pink", "Purple", "Green", "Rainbow"};
    static final int[] THEME = {0x35E0FF, 0xFFE066, 0xFF6FAE, 0xB26BFF, 0x4CFF7A, -1};
    static int themeIdx = 0;
    static final int[] GLASS_ALPHA = {0x38, 0x59, 0x80, 0xA6};
    static int glassIdx = 1;
    static boolean glassOn() { return FX.get("glass"); }
    static int glassColor() { return (GLASS_ALPHA[glassIdx] << 24) | 0xDFF6FF; }
    static int tick = 0;
    private boolean menuKeyDown = false, glassWasOn = false, brightWasOn = false;

    static int theme() {
        int c = THEME[themeIdx];
        return c == -1 ? Color.HSBtoRGB((tick % 120) / 120f, 1f, 1f) & 0xFFFFFF : c;
    }

    static Path cfg() { return FabricLoader.getInstance().getConfigDir().resolve("flashfx.properties"); }

    static void save() {
        Properties pr = new Properties();
        FX.forEach((k, v) -> pr.setProperty(k, String.valueOf(v)));
        pr.setProperty("theme", String.valueOf(themeIdx));
        pr.setProperty("glassAlpha", String.valueOf(glassIdx));
        try (Writer wr = Files.newBufferedWriter(cfg())) { pr.store(wr, "FlashFX settings"); } catch (IOException ignored) {}
    }

    static void load() {
        if (!Files.exists(cfg())) return;
        Properties pr = new Properties();
        try (Reader rd = Files.newBufferedReader(cfg())) { pr.load(rd); } catch (IOException e) { return; }
        for (String k : FX.keySet()) if (pr.containsKey(k)) FX.put(k, Boolean.parseBoolean(pr.getProperty(k)));
        try { glassIdx = Math.floorMod(Integer.parseInt(pr.getProperty("glassAlpha", "1")), GLASS_ALPHA.length); } catch (NumberFormatException ignored) {}
        try { themeIdx = Math.floorMod(Integer.parseInt(pr.getProperty("theme", "0")), THEME.length); } catch (NumberFormatException ignored) {}
    }

    private boolean wasGrounded = true;
    private final List<double[]> rings = new ArrayList<>(); // x, y, z, age

    @Override
    public void onInitializeClient() {
        load();
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient() && FX.get("hit")) hitBurst(world, entity);
            return ActionResult.PASS;
        });

        HudElementRegistry.addLast(Identifier.of("flashfx", "hud"), this::renderHud);

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommandManager.literal("flashfx");
            for (String name : FX.keySet()) {
                root.then(ClientCommandManager.literal(name).executes(ctx -> {
                    boolean v = !FX.get(name);
                    FX.put(name, v);
                    save();
                    ctx.getSource().sendFeedback(Text.literal("FlashFX " + name + ": " + (v ? "ON" : "OFF")));
                    return 1;
                }));
            }
            root.then(ClientCommandManager.literal("menu").executes(ctx -> {
                MinecraftClient.getInstance().send(() -> MinecraftClient.getInstance().setScreen(new FlashFxScreen()));
                return 1;
            }));
            dispatcher.register(root);
        });
    }

    private void onTick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        World w = mc.world;
        if (p == null || w == null) return;
        tick++;

        boolean down = GLFW.glfwGetKey(mc.getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        if (down && !menuKeyDown && mc.currentScreen == null) mc.setScreen(new FlashFxScreen());
        menuKeyDown = down;

        // Jump detection: left the ground while moving up
        boolean grounded = p.isOnGround();
        if (FX.get("jump") && wasGrounded && !grounded && p.getVelocity().y > 0.1) {
            jumpEffect(w, p);
        }
        wasGrounded = grounded;

        // Expanding shockwave rings
        for (int i = rings.size() - 1; i >= 0; i--) {
            double[] r = rings.get(i);
            int age = (int) r[3]++;
            double radius = 0.4 + age * 0.3;
            int n = 28;
            for (int k = 0; k < n; k++) {
                double a = k * Math.PI * 2 / n;
                double cx = Math.cos(a), cz = Math.sin(a);
                ParticleEffect pe = (k % 2 == 0)
                        ? ParticleTypes.END_ROD
                        : new DustParticleEffect(theme(), 1.3f);
                w.addParticleClient(pe, r[0] + cx * radius, r[1] + 0.1, r[2] + cz * radius,
                        cx * 0.04, 0.02, cz * 0.04);
            }
            if (age >= 9) rings.remove(i);
        }

        // Rainbow trail
        if (FX.get("trail") && tick % 2 == 0 && p.getVelocity().horizontalLength() > 0.08) {
            int rgb = Color.HSBtoRGB((tick % 120) / 120f, 1f, 1f) & 0xFFFFFF;
            w.addParticleClient(new DustParticleEffect(rgb, 1.1f),
                    p.getX(), p.getY() + 0.1, p.getZ(), 0, 0, 0);
        }

        cosmetics(w, p);

        // Glass player: invisible body + glowing outline + sparkles through the body
        if (FX.get("ghost")) {
            p.setGlowing(true);
            if (!p.hasStatusEffect(StatusEffects.INVISIBILITY)) {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 400, 0, false, false, false));
            }
            if (tick % 2 == 0) {
                double x = p.getX() + (Math.random() - 0.5) * 0.5;
                double y = p.getY() + Math.random() * p.getHeight();
                double z = p.getZ() + (Math.random() - 0.5) * 0.5;
                w.addParticleClient(Math.random() < 0.5 ? ParticleTypes.END_ROD : new DustParticleEffect(theme(), 0.5f), x, y, z, 0, 0.01, 0);
            }
            glassWasOn = true;
        } else if (glassWasOn) {
            p.setGlowing(false);
            p.removeStatusEffect(StatusEffects.INVISIBILITY);
            glassWasOn = false;
        }

        // Fullbright
        if (!FX.get("bright") && brightWasOn) {
            p.removeStatusEffect(StatusEffects.NIGHT_VISION);
            brightWasOn = false;
        }
        if (FX.get("bright")) {
            brightWasOn = true;
            if (tick % 100 == 0 || !p.hasStatusEffect(StatusEffects.NIGHT_VISION)) {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 400, 0, false, false, false));
            }
        }
    }

    private void cosmetics(World w, ClientPlayerEntity p) {
        double x = p.getX(), y = p.getY(), z = p.getZ(), h = p.getHeight();
        double yaw = Math.toRadians(p.getBodyYaw());
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);   // right vector
        double bx = Math.sin(yaw), bz = -Math.cos(yaw);    // back vector

        if (FX.get("halo") && tick % 2 == 0) {
            for (int k = 0; k < 10; k++) {
                double a = tick * 0.08 + k * Math.PI * 2 / 10;
                w.addParticleClient(new DustParticleEffect(theme(), 0.6f),
                        x + Math.cos(a) * 0.35, y + h + 0.25, z + Math.sin(a) * 0.35, 0, 0, 0);
            }
        }
        if (FX.get("wings") && tick % 2 == 0) {
            double flap = Math.sin(tick * 0.15);
            for (int side = -1; side <= 1; side += 2) {
                for (int i = 0; i < 7; i++) {
                    double s = 0.2 + i * 0.22;
                    double up = 1.3 + Math.sin(i * 0.5) * 0.35 - i * 0.05 + flap * 0.08 * i;
                    for (int row = 0; row < 2; row++) {
                        double u = up - row * 0.3;
                        w.addParticleClient(new DustParticleEffect(0xFFFFFF, 0.7f),
                                x + rx * side * s + bx * (0.25 + i * 0.03),
                                y + u,
                                z + rz * side * s + bz * (0.25 + i * 0.03), 0, 0, 0);
                    }
                }
            }
        }
        if (FX.get("hat") && tick % 2 == 0) {
            double[][] layers = {{0.65, 0.15}, {0.45, 0.3}, {0.25, 0.45}, {0.0, 0.6}};
            for (double[] l : layers) {
                int n = l[0] == 0 ? 1 : 10;
                for (int k = 0; k < n; k++) {
                    double a = k * Math.PI * 2 / n;
                    w.addParticleClient(new DustParticleEffect(theme(), 0.7f),
                            x + Math.cos(a) * l[0], y + h + l[1], z + Math.sin(a) * l[0], 0, 0, 0);
                }
            }
        }
        if (FX.get("petals") && tick % 3 == 0) {
            w.addParticleClient(ParticleTypes.CHERRY_LEAF,
                    x + (Math.random() - 0.5) * 6, y + 3, z + (Math.random() - 0.5) * 6, 0, -0.02, 0);
        }
        if (FX.get("stardust") && tick % 2 == 0) {
            w.addParticleClient(ParticleTypes.END_ROD,
                    x + (Math.random() - 0.5) * 3, y + Math.random() * 2.5, z + (Math.random() - 0.5) * 3,
                    0, 0.01, 0);
        }
    }

    private void jumpEffect(World w, ClientPlayerEntity p) {
        rings.add(new double[]{p.getX(), p.getY(), p.getZ(), 0});
        for (int i = 0; i < 20; i++) {
            double a = Math.random() * Math.PI * 2;
            double v = 0.05 + Math.random() * 0.1;
            w.addParticleClient(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.05, p.getZ(),
                    Math.cos(a) * v, 0.02, Math.sin(a) * v);
        }
        for (int i = 0; i < 14; i++) { // petal burst
            w.addParticleClient(ParticleTypes.CHERRY_LEAF, p.getX(), p.getY() + 0.2, p.getZ(),
                    (Math.random() - 0.5) * 0.3, 0.1 + Math.random() * 0.15, (Math.random() - 0.5) * 0.3);
        }
        for (int i = 0; i < 16; i++) { // upward spiral
            double a = i * 0.7;
            w.addParticleClient(ParticleTypes.END_ROD,
                    p.getX() + Math.cos(a) * 0.5, p.getY() + i * 0.08, p.getZ() + Math.sin(a) * 0.5,
                    0, 0.06, 0);
        }
    }

    private void hitBurst(World w, Entity e) {
        double x = e.getX(), y = e.getBodyY(0.5), z = e.getZ();
        for (int i = 0; i < 30; i++) {
            double a = Math.random() * Math.PI * 2, v = 0.25 + Math.random() * 0.25;
            w.addParticleClient(ParticleTypes.ENCHANTED_HIT, x, y, z,
                    Math.cos(a) * v, (Math.random() - 0.3) * 0.4, Math.sin(a) * v);
        }
        for (int i = 0; i < 12; i++) {
            double a = Math.random() * Math.PI * 2, v = 0.2 + Math.random() * 0.2;
            w.addParticleClient(ParticleTypes.CRIT, x, y, z, Math.cos(a) * v, Math.random() * 0.3, Math.sin(a) * v);
        }
    }

    private void renderHud(DrawContext ctx, RenderTickCounter counter) {
        if (!FX.get("hud")) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        int c = Color.HSBtoRGB((System.currentTimeMillis() % 3000) / 3000f, 0.8f, 1f) | 0xFF000000;
        ctx.drawTextWithShadow(mc.textRenderer, "FlashFX  " + mc.getCurrentFps() + " FPS", 6, 6, c);
    }
}
