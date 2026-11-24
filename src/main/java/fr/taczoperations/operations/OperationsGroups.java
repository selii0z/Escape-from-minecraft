package fr.taczoperations.operations;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

final class OperationsGroups {
    private static final Map<MinecraftServer, OperationsGroups> SERVERS = new WeakHashMap<>();
    private static final long INVITE_DURATION = 20L * 60;
    private final Map<UUID, UUID> partners = new HashMap<>();
    private final Map<UUID, Invitation> invitations = new HashMap<>();

    private record Invitation(UUID from, long expiresAt) {}

    static OperationsGroups of(ServerPlayer player) {
        return SERVERS.computeIfAbsent(player.server, ignored -> new OperationsGroups());
    }

    UUID partner(ServerPlayer player) {
        return partners.get(player.getUUID());
    }

    UUID inviter(ServerPlayer player) {
        invitations.values().removeIf(invitation -> invitation.expiresAt() <= now(player));
        Invitation invitation = invitations.get(player.getUUID());
        if (invitation == null || player.server.getPlayerList().getPlayer(invitation.from()) == null) {
            return null;
        }
        return invitation.from();
    }

    String invite(ServerPlayer sender, UUID targetId) {
        UUID senderId = sender.getUUID();
        ServerPlayer target = sender.server.getPlayerList().getPlayer(targetId);
        if (target == null || targetId.equals(senderId)) {
            return "Ce joueur est indisponible.";
        }
        if (partners.containsKey(senderId) || partners.containsKey(targetId)) {
            return "Un des joueurs est déjà en groupe.";
        }
        if (inviter(target) != null) {
            return "Ce joueur a déjà une invitation.";
        }
        invitations.put(targetId, new Invitation(senderId, now(sender) + INVITE_DURATION));
        target.sendSystemMessage(Component.literal(sender.getGameProfile().getName()
            + " t’invite dans son duo. Ouvre le Tableau des opérations, onglet Groupe."));
        return "Invitation envoyée.";
    }

    String answer(ServerPlayer player, UUID expectedInviter, boolean accept) {
        UUID sender = inviter(player);
        UUID recipient = player.getUUID();
        if (sender == null || !sender.equals(expectedInviter)) {
            return "Cette invitation a expiré.";
        }
        invitations.remove(recipient);
        ServerPlayer inviter = player.server.getPlayerList().getPlayer(sender);
        if (!accept) {
            notify(inviter, "Invitation refusée.");
            return "Invitation refusée.";
        }
        if (partners.containsKey(sender) || partners.containsKey(recipient)) {
            return "Un des joueurs est déjà en groupe.";
        }
        partners.put(recipient, sender);
        partners.put(sender, recipient);
        invitations.entrySet().removeIf(entry ->
            entry.getKey().equals(sender) || entry.getValue().from().equals(sender)
                || entry.getValue().from().equals(recipient));
        notify(inviter, "Groupe formé.");
        return "Groupe formé.";
    }

    String leave(ServerPlayer player) {
        UUID partner = partners.remove(player.getUUID());
        if (partner != null) {
            partners.remove(partner);
            notify(player.server.getPlayerList().getPlayer(partner), "Ton duo a été dissous.");
        }
        return "Tu joues en solo.";
    }

    private static long now(ServerPlayer player) {
        return player.server.overworld().getGameTime();
    }

    private static void notify(ServerPlayer player, String message) {
        if (player != null) {
            player.sendSystemMessage(Component.literal(message));
        }
    }
}
