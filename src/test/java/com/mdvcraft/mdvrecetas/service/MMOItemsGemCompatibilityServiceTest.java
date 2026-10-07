package com.mdvcraft.mdvrecetas.service;

import com.mdvcraft.mdvrecetas.hook.MMOItemsHook;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MMOItemsGemCompatibilityServiceTest {
    private static final MMOItemsHook.MmoIdentity SWORD =
            new MMOItemsHook.MmoIdentity("SWORD", "FORGED_SWORD");

    private MMOItemsHook hook;
    private RecipeSignatureService signatures;
    private ForjadorModifierService modifiers;
    private MMOItemsGemCompatibilityService bridge;
    private Material material;

    @BeforeEach
    void setup() {
        hook = mock(MMOItemsHook.class);
        signatures = mock(RecipeSignatureService.class);
        modifiers = mock(ForjadorModifierService.class);
        material = mock(Material.class);
        bridge = new MMOItemsGemCompatibilityService(hook, signatures, modifiers);
    }

    @Test
    void cancelledRepairUsesCapturedCloneAndPreservesTheRebuiltResultInOrder() {
        ItemStack original = item(), snapshot = item(), repaired = item();
        ItemStack withModifier = item(), finalItem = item();
        AtomicReference<ItemStack> current = new AtomicReference<>(original);
        InventoryClickEvent event = click(current);
        captureSigned(original, snapshot);

        bridge.captureBeforeMmoItems(event);

        // MMOItems consumes the repair consumable, cancels the click and replaces the weapon.
        current.set(repaired);
        when(event.isCancelled()).thenReturn(true);
        identity(repaired, SWORD);
        when(modifiers.restoreAfterMmoItemsMutation(snapshot, repaired)).thenReturn(withModifier);
        when(signatures.restoreAfterMmoItemsMutation(snapshot, withModifier)).thenReturn(finalItem);

        bridge.restoreAfterMmoItems(event);

        InOrder order = inOrder(modifiers, signatures, event);
        order.verify(modifiers).restoreAfterMmoItemsMutation(snapshot, repaired);
        order.verify(signatures).restoreAfterMmoItemsMutation(snapshot, withModifier);
        order.verify(event).setCurrentItem(finalItem);
        assertSame(finalItem, current.get());
        verify(original, times(1)).clone();
        verify(modifiers, never()).restoreAfterMmoItemsMutation(original, repaired);
    }

    @Test
    void fiveRepairsUseTheirOwnSnapshotsAndEachClickIsRestoredOnlyOnce() {
        ItemStack weapon = item();
        for (int repair = 0; repair < 5; repair++) {
            ItemStack snapshot = item(), repaired = item(), normalized = item();
            AtomicReference<ItemStack> current = new AtomicReference<>(weapon);
            InventoryClickEvent event = click(current);
            captureSigned(weapon, snapshot);
            bridge.captureBeforeMmoItems(event);
            current.set(repaired);
            identity(repaired, SWORD);
            when(event.isCancelled()).thenReturn(true);
            when(modifiers.restoreAfterMmoItemsMutation(snapshot, repaired)).thenReturn(repaired);
            when(signatures.restoreAfterMmoItemsMutation(snapshot, repaired)).thenReturn(normalized);

            bridge.restoreAfterMmoItems(event);
            bridge.restoreAfterMmoItems(event);

            verify(signatures, times(1)).restoreAfterMmoItemsMutation(snapshot, repaired);
            verify(event, times(1)).setCurrentItem(normalized);
            assertSame(normalized, current.get());
            weapon = normalized;
        }
        verify(signatures, times(5)).restoreAfterMmoItemsMutation(any(), any());
        verify(modifiers, times(5)).restoreAfterMmoItemsMutation(any(), any());
    }

    @Test
    void existingGemOperationAlsoRestoresModifierOnlyItems() {
        ItemStack original = item(), snapshot = item(), socketed = item(), restored = item();
        AtomicReference<ItemStack> current = new AtomicReference<>(original);
        InventoryClickEvent event = click(current);
        when(modifiers.hasModifierData(original)).thenReturn(true);
        when(original.clone()).thenReturn(snapshot);
        identity(original, SWORD);
        bridge.captureBeforeMmoItems(event);
        current.set(socketed);
        identity(socketed, SWORD);
        when(event.isCancelled()).thenReturn(true);
        when(modifiers.restoreAfterMmoItemsMutation(snapshot, socketed)).thenReturn(restored);
        when(signatures.restoreAfterMmoItemsMutation(snapshot, restored)).thenReturn(restored);

        bridge.restoreAfterMmoItems(event);

        verify(event).setCurrentItem(restored);
        assertSame(restored, current.get());
    }

    @Test
    void uncancelledSwapDoesNotRewriteTheItemOrLeaveAStaleSnapshot() {
        ItemStack original = item(), snapshot = item();
        InventoryClickEvent event = click(new AtomicReference<>(original));
        captureSigned(original, snapshot);
        bridge.captureBeforeMmoItems(event);

        bridge.restoreAfterMmoItems(event);
        when(event.isCancelled()).thenReturn(true);
        bridge.restoreAfterMmoItems(event);

        verifyNoRestoration(event);
    }

    @Test
    void unsignedItemIsLeftUntouchedEvenWhenAnotherPluginCancelsTheClick() {
        ItemStack original = item();
        InventoryClickEvent event = click(new AtomicReference<>(original));
        when(event.isCancelled()).thenReturn(true);

        bridge.captureBeforeMmoItems(event);
        bridge.restoreAfterMmoItems(event);

        verifyNoInteractions(hook);
        verify(original, never()).clone();
        verifyNoRestoration(event);
    }

    @Test
    void signedVanillaItemWithoutMmoIdentityIsLeftUntouched() {
        ItemStack original = item();
        InventoryClickEvent event = click(new AtomicReference<>(original));
        when(signatures.hasSignatureData(original)).thenReturn(true);
        when(hook.readIdentity(original)).thenReturn(Optional.empty());
        when(event.isCancelled()).thenReturn(true);

        bridge.captureBeforeMmoItems(event);
        bridge.restoreAfterMmoItems(event);

        verify(original, never()).clone();
        verifyNoRestoration(event);
    }

    @Test
    void sameIdInAnotherMmoTypeDoesNotReceiveTheOriginalSignature() {
        ItemStack original = item(), snapshot = item(), replacement = item();
        AtomicReference<ItemStack> current = new AtomicReference<>(original);
        InventoryClickEvent event = click(current);
        captureSigned(original, snapshot);
        bridge.captureBeforeMmoItems(event);
        current.set(replacement);
        identity(replacement, new MMOItemsHook.MmoIdentity("CONSUMABLE", SWORD.id()));
        when(event.isCancelled()).thenReturn(true);

        bridge.restoreAfterMmoItems(event);

        verifyNoRestoration(event);
        assertSame(replacement, current.get());
    }

    @Test
    void differentMmoItemIdDoesNotReceiveTheOriginalSignature() {
        ItemStack original = item(), snapshot = item(), replacement = item();
        AtomicReference<ItemStack> current = new AtomicReference<>(original);
        InventoryClickEvent event = click(current);
        captureSigned(original, snapshot);
        bridge.captureBeforeMmoItems(event);
        current.set(replacement);
        identity(replacement, new MMOItemsHook.MmoIdentity("SWORD", "OTHER_SWORD"));
        when(event.isCancelled()).thenReturn(true);

        bridge.restoreAfterMmoItems(event);

        verifyNoRestoration(event);
    }

    @Test
    void consumedOrRemovedTargetDoesNotGetRecreated() {
        for (boolean air : new boolean[]{false, true}) {
            ItemStack original = item(), snapshot = item(), removed = air ? item() : null;
            AtomicReference<ItemStack> current = new AtomicReference<>(original);
            InventoryClickEvent event = click(current);
            captureSigned(original, snapshot);
            bridge.captureBeforeMmoItems(event);
            current.set(removed);
            if (air) {
                Material airMaterial = mock(Material.class);
                when(airMaterial.isAir()).thenReturn(true);
                when(removed.getType()).thenReturn(airMaterial);
            }
            when(event.isCancelled()).thenReturn(true);

            bridge.restoreAfterMmoItems(event);

            verifyNoRestoration(event);
        }
    }

    @Test
    void unrelatedInventoryActionDoesNotCaptureOrRestoreAnything() {
        InventoryClickEvent event = click(new AtomicReference<>(item()));
        when(event.getAction()).thenReturn(InventoryAction.PICKUP_ALL);
        when(event.isCancelled()).thenReturn(true);

        bridge.captureBeforeMmoItems(event);
        bridge.restoreAfterMmoItems(event);

        verifyNoInteractions(hook, signatures, modifiers);
        verify(event, never()).setCurrentItem(any());
    }

    @Test
    void monitorWithoutCaptureAndClearBeforeMonitorDoNotMutateTheInventory() {
        ItemStack original = item(), snapshot = item();
        InventoryClickEvent event = click(new AtomicReference<>(original));
        when(event.isCancelled()).thenReturn(true);
        bridge.restoreAfterMmoItems(event);
        captureSigned(original, snapshot);
        bridge.captureBeforeMmoItems(event);
        bridge.clear();

        bridge.restoreAfterMmoItems(event);

        verifyNoRestoration(event);
    }

    private ItemStack item() {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(material);
        return item;
    }

    private InventoryClickEvent click(AtomicReference<ItemStack> current) {
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getWhoClicked()).thenReturn(mock(Player.class));
        when(event.getAction()).thenReturn(InventoryAction.SWAP_WITH_CURSOR);
        when(event.getCurrentItem()).thenAnswer(ignored -> current.get());
        doAnswer(invocation -> {
            current.set(invocation.getArgument(0));
            return null;
        }).when(event).setCurrentItem(any());
        return event;
    }

    private void captureSigned(ItemStack original, ItemStack snapshot) {
        when(signatures.hasSignatureData(original)).thenReturn(true);
        when(original.clone()).thenReturn(snapshot);
        identity(original, SWORD);
    }

    private void identity(ItemStack item, MMOItemsHook.MmoIdentity identity) {
        when(hook.readIdentity(item)).thenReturn(Optional.of(identity));
    }

    private void verifyNoRestoration(InventoryClickEvent event) {
        verify(modifiers, never()).restoreAfterMmoItemsMutation(any(), any());
        verify(signatures, never()).restoreAfterMmoItemsMutation(any(), any());
        verify(event, never()).setCurrentItem(any());
    }
}
