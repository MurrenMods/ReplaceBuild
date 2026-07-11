package com.murren.replacebuild.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.lwjgl.glfw.GLFW;

public class ReplacebuildClient implements ClientModInitializer {

    private static boolean isEnabled = false;

    private static KeyMapping toggleKey;
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("replacebuild", "rpb"));
    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.replacebuild.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_KP_4, CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            while (toggleKey.consumeClick()) {
                Minecraft.getInstance().gui.setOverlayMessage(Component.literal(isEnabled ? "ReplaceBuild Disabled" : "ReplaceBuild Enabled"), true);
                isEnabled = !isEnabled;
            }
        });

        UseBlockCallback.EVENT.register(this::onUseBlock);
    }

    private InteractionResult onUseBlock(Player player,
                                         Level world,
                                         InteractionHand hand,
                                         BlockHitResult hitResult) {

        Minecraft client = Minecraft.getInstance();

        // Only run our logic on the client, and only for the local player

        if (!isEnabled)
            return InteractionResult.PASS;

        if (!world.isClientSide() || !(player instanceof LocalPlayer clientPlayer)) {
            return InteractionResult.PASS;
        }

        // Allow normal building if sneaking
        if (player.isCrouching())
            return InteractionResult.PASS;

        if (client.gameMode == null) {
            return InteractionResult.PASS;
        }

        if (!player.getAbilities().instabuild) {
            return InteractionResult.PASS;
        }

        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof BlockItem)) {
            return InteractionResult.PASS;
        }

        BlockPos targetPos = hitResult.getBlockPos();

        // Skip if the block is already replaceable
        // vanilla already handles that case as a normal replace.
        if (world.getBlockState(targetPos).getCollisionShape(world, targetPos).isEmpty()
                && world.getBlockState(targetPos).canBeReplaced()) {
            return InteractionResult.PASS;
        }

        // Simulate the "left click" half of the simultaneous click:
        // instantly break the targeted block, client-authoritative in creative.
        client.gameMode.continueDestroyBlock(targetPos, hitResult.getDirection());
        clientPlayer.swing(hand);

        // Let vanilla's normal right-click placement logic continue immediately
        // afterward (same tick) — since the block is now air, it will place
        // directly on targetPos instead of the offset face, i.e. a "replace".
        return InteractionResult.PASS;
    }
}