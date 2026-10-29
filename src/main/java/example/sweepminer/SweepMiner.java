package example.sweepminer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

public class SweepMiner implements ClientModInitializer {

    private static final double DISTANCE = 105.0;
    private static final int PAUSE_TICKS = 20; // 1 sekunda

    private enum State { OFF, RIGHT, PAUSE_AFTER_RIGHT, LEFT, PAUSE_AFTER_LEFT }

    private State state = State.OFF;
    private Vec3d startPos;
    private int pauseCounter;
    private KeyBinding toggleKey;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.sweepminer.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, "category.sweepminer"));

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    private void onTick(MinecraftClient mc) {
        while (toggleKey.wasPressed()) {
            if (state == State.OFF && mc.player != null) {
                mc.options.pauseOnLostFocus = false;
                mc.options.write();
                start(mc, State.RIGHT);
            } else {
                stop(mc);
            }
        }

        if (state == State.OFF) return;
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            stop(mc);
            return;
        }

        GameOptions o = mc.options;

        switch (state) {
            case RIGHT -> {
                o.leftKey.setPressed(false);
                o.rightKey.setPressed(true);
                mine(mc);
                if (travelled(mc) >= DISTANCE) {
                    release(mc);
                    pauseCounter = PAUSE_TICKS;
                    state = State.PAUSE_AFTER_RIGHT;
                }
            }
            case PAUSE_AFTER_RIGHT -> {
                if (--pauseCounter <= 0) start(mc, State.LEFT);
            }
            case LEFT -> {
                o.rightKey.setPressed(false);
                o.leftKey.setPressed(true);
                mine(mc);
                if (travelled(mc) >= DISTANCE) {
                    release(mc);
                    pauseCounter = PAUSE_TICKS;
                    state = State.PAUSE_AFTER_LEFT;
                }
            }
            case PAUSE_AFTER_LEFT -> {
                if (--pauseCounter <= 0) start(mc, State.RIGHT);
            }
            default -> {}
        }
    }

    private void start(MinecraftClient mc, State newState) {
        startPos = mc.player.getPos();
        state = newState;
    }

    private void stop(MinecraftClient mc) {
        release(mc);
        state = State.OFF;
    }

    private void release(MinecraftClient mc) {
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        mc.options.attackKey.setPressed(false);
    }

    private double travelled(MinecraftClient mc) {
        Vec3d p = mc.player.getPos();
        double dx = p.x - startPos.x;
        double dz = p.z - startPos.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private void mine(MinecraftClient mc) {
        mc.options.attackKey.setPressed(true);
        if (mc.crosshairTarget instanceof BlockHitResult hit
                && hit.getType() == HitResult.Type.BLOCK) {
            if (mc.interactionManager.updateBlockBreakingProgress(hit.getBlockPos(), hit.getSide())) {
                mc.particleManager.addBlockBreakingParticles(hit.getBlockPos(), hit.getSide());
                mc.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }
}
