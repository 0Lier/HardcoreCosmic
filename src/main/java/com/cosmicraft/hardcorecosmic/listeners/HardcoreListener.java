package com.cosmicraft.hardcorecosmic.listeners;

import com.cosmicraft.hardcorecosmic.HardcoreCosmic;
import org.bukkit.Bukkit;
import org.bukkit.BanList;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.Skull;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

public class HardcoreListener implements Listener {

    private final HardcoreCosmic plugin;

    private static final String PERMADEATH_SOUND_KEY = "minecraft:custom.death";
    private static final long SPECTATOR_PREVIEW_TICKS = 60L; // 3s en espectador antes del kick

    public HardcoreListener(HardcoreCosmic plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (!plugin.getHardcoreManager().isHardcoreActive()) return;

        Player player = event.getEntity();
        String playerName = player.getName();
        Location deathLoc = player.getLocation().getBlock().getLocation();

        // Leer la causa real de muerte desde el último daño recibido.
        // NO usamos event.getDeathMessage(): puede ser null si otro plugin
        // lo limpió antes (MONITOR priority) o si Vanilla aún no lo generó.
        String deathCause = resolveDeathCause(player);

        event.setDeathMessage(null);

        // Si el jugador está en la whitelist, lo ponemos en espectador sin ban ni animaciones/mensajes.
        if (plugin.getHardcoreManager().isWhitelisted(playerName)) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    player.spigot().respawn();
                    player.setGameMode(GameMode.SPECTATOR);
                }
            });
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            placePermadeathSkull(deathLoc, player);

            if (deathLoc.getWorld() != null) {
                deathLoc.getWorld().strikeLightningEffect(deathLoc);
            }

            for (Player online : Bukkit.getOnlinePlayers()) {
                online.sendTitle(
                        ChatColor.DARK_RED + "" + ChatColor.BOLD + "☠ PERMADEATH ☠",
                        ChatColor.RED + playerName + ChatColor.DARK_RED + " murió permanentemente",
                        10, 70, 20
                );
                try {
                    online.playSound(online.getLocation(), PERMADEATH_SOUND_KEY, SoundCategory.MASTER, 1f, 1f);
                } catch (Exception ignored) {
                    online.playSound(online.getLocation(), Sound.ENTITY_WITHER_DEATH, 1f, 0.7f);
                }
            }

            Bukkit.broadcastMessage(ChatColor.DARK_RED + "☠ " + ChatColor.RED + playerName +
                    ChatColor.GRAY + " ha muerto de forma " + ChatColor.DARK_RED + "" + ChatColor.BOLD +
                    "permanente" + ChatColor.RESET + ChatColor.GRAY + ". " +
                    ChatColor.GRAY + "(" + deathCause + ")");

            // Banear siempre, esté online u offline (combat log incluido).
            // getBanList(NAME) funciona tanto si el jugador está conectado como si no.
            banPlayer(player, playerName, deathCause);

            // Si sigue online: ponerlo en espectador 3s y luego kickear.
            // Si ya se fue (combat log): el ban ya está aplicado; cuando intente
            // volver a conectar, el servidor lo rechazará automáticamente.
            if (player.isOnline()) {
                player.spigot().respawn();
                player.setGameMode(GameMode.SPECTATOR);

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        player.kickPlayer(ChatColor.DARK_RED + "☠ Has muerto de forma permanente.\n" +
                                ChatColor.GRAY + "Modo hardcore");
                    }
                }, SPECTATOR_PREVIEW_TICKS);
            }
        });
    }

    /**
     * Construye una descripción legible de la causa de muerte a partir del
     * último EntityDamageEvent del jugador. Funciona aunque el death message
     * haya sido nulleado por otro plugin.
     */
    private String resolveDeathCause(Player player) {
        EntityDamageEvent cause = player.getLastDamageCause();
        if (cause == null) return "causa desconocida";

        // Si fue dañado por otra entidad (mob, jugador, proyectil...)
        if (cause instanceof EntityDamageByEntityEvent byEntity) {
            org.bukkit.entity.Entity damager = byEntity.getDamager();

            // Proyectil: buscar el shooter real
            if (damager instanceof org.bukkit.entity.Projectile proj
                    && proj.getShooter() instanceof org.bukkit.entity.Entity shooter) {
                damager = shooter;
            }

            String killerName = (damager instanceof org.bukkit.entity.LivingEntity le)
                    ? le.getCustomName() != null ? le.getCustomName()
                      : damager.getType().name().toLowerCase().replace('_', ' ')
                    : damager.getType().name().toLowerCase().replace('_', ' ');

            return "asesinado por " + killerName;
        }

        // Daño ambiental: mapear DamageCause a texto español.
        // Usamos el nombre del enum en vez de listar cada caso, porque
        // algunas constantes (p.ej. EXPLOSION) difieren entre versiones
        // de la API (EXPLOSION vs ENTITY_EXPLOSION/BLOCK_EXPLOSION).
        String name = cause.getCause().name();
        return switch (name) {
            case "FALL"                                  -> "caída";
            case "FIRE", "FIRE_TICK"                      -> "fuego";
            case "LAVA"                                   -> "lava";
            case "DROWNING"                                -> "ahogamiento";
            case "SUFFOCATION"                              -> "sofocación";
            case "STARVATION"                               -> "hambre";
            case "POISON"                                   -> "veneno";
            case "MAGIC"                                    -> "magia";
            case "WITHER"                                   -> "wither";
            case "LIGHTNING"                                -> "rayo";
            case "VOID"                                     -> "vacío";
            case "EXPLOSION", "ENTITY_EXPLOSION", "BLOCK_EXPLOSION" -> "explosión";
            case "CONTACT"                                  -> "cactus / contacto";
            case "CRAMMING"                                 -> "aplastamiento";
            case "FREEZE"                                   -> "congelamiento";
            case "SONIC_BOOM"                               -> "explosión sónica";
            default                                         -> name.toLowerCase().replace('_', ' ');
        };
    }

    /**
     * Aplica el ban por nombre usando BanList — funciona tanto para jugadores
     * online como offline (combat log). El motivo queda guardado en el ban.
     */
    private void banPlayer(Player player, String playerName, String deathCause) {
        String reason = "☠ Has muerto de forma permanente. Causa: " + deathCause;
        org.bukkit.BanList<org.bukkit.profile.PlayerProfile> banList = (org.bukkit.BanList<org.bukkit.profile.PlayerProfile>) Bukkit.getBanList(BanList.Type.PROFILE);
        banList.addBan(player.getPlayerProfile(), reason, (java.util.Date) null, (String) null);
        plugin.getLogger().info("[HardcoreCosmic] " + playerName + " baneado por permadeath. Causa: " + deathCause);
    }

    private void placePermadeathSkull(Location loc, Player player) {
        if (loc.getY() < loc.getWorld().getMinHeight()) {
            loc.setY(loc.getWorld().getMinHeight());
        }
        Block block = loc.getBlock();
        block.setType(Material.PLAYER_HEAD);
        if (block.getState() instanceof Skull skull) {
            skull.setOwningPlayer(player);
            skull.update(true, false);
        }
    }
}