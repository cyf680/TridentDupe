/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.example.addon.modules;

import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class SpectralArrowDupe extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    
    private final Setting<Double> delay = sgGeneral.add(new DoubleSetting.Builder()
        .name("delay")
        .defaultValue(3.0)
        .min(0)
        .build()
    );

    private final Setting<Double> chargeDelay = sgGeneral.add(new DoubleSetting.Builder()
        .name("charge-delay")
        .defaultValue(1.0)
        .min(0)
        .build()
    );

    private final Setting<Integer> arrowSlot = sgGeneral.add(new IntSetting.Builder()
        .name("arrow-slot")
        .defaultValue(35)
        .range(0, 35)
        .build()
    );

    private final Setting<Integer> craftingSlot = sgGeneral.add(new IntSetting.Builder()
        .name("crafting-slot")
        .defaultValue(1)
        .range(1, 4)
        .build()
    );

    private boolean cancelPackets;
    private final List<Pair<Long, Runnable>> scheduledTasks = new ArrayList<>();

    public SpectralArrowDupe() {
        super(YourAddon.CATEGORY, "spectral-arrow-dupe", "Dupes spectral arrows using bow");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onPacketSend(PacketEvent.Send event) {
        if (cancelPackets && (event.packet instanceof PlayerActionC2SPacket || event.packet instanceof ClickSlotC2SPacket)) {
            event.cancel();
        }
    }

    @Override
    public void onActivate() {
        if (mc.player == null) return;
        if (!validateSetup()) {
            this.toggle();
            return;
        }
        startDupeCycle();
    }

    private boolean validateSetup() {
        if (mc.player.getInventory().getStack(0).getItem() != Items.BOW) return false;
        if (mc.player.getInventory().getStack(arrowSlot.get()).getItem() != Items.SPECTRAL_ARROW) return false;
        return true;
    }

    private void startDupeCycle() {
        moveArrowToCrafting();
        cancelPackets = true;
        scheduleTask(this::handleBowAction, 50);
    }

    private void handleBowAction() {
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        scheduleTask(this::releaseBow, (long)(chargeDelay.get() * 1000));
    }

    private void releaseBow() {
        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.RELEASE_USE_ITEM,
            BlockPos.ORIGIN,
            Direction.DOWN,
            0
        ));
        moveArrowBack();
        scheduleTask(this::startDupeCycle, (long)(delay.get() * 1000));
    }

    private void moveArrowToCrafting() {
        clickSlot(arrowSlot.get(), craftingSlot.get());
    }

    private void moveArrowBack() {
        clickSlot(craftingSlot.get(), arrowSlot.get());
    }

    private void clickSlot(int from, int to) {
        mc.interactionManager.clickSlot(
            mc.player.currentScreenHandler.syncId,
            from,
            to,
            SlotActionType.SWAP,
            mc.player
        );
    }

    private void scheduleTask(Runnable task, long delay) {
        scheduledTasks.add(new Pair<>(System.currentTimeMillis() + delay, task));
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        Iterator<Pair<Long, Runnable>> iterator = scheduledTasks.iterator();
        while (iterator.hasNext()) {
            Pair<Long, Runnable> entry = iterator.next();
            if (System.currentTimeMillis() >= entry.getLeft()) {
                entry.getRight().run();
                iterator.remove();
            }
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        toggle();
    }

    @EventHandler
    private void onScreenOpen(OpenScreenEvent event) {
        if (event.screen instanceof DisconnectedScreen) {
            toggle();
        }
    }
}
