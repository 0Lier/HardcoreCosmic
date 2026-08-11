package com.cosmicraft.hardcorecosmic.managers;

import com.cosmicraft.hardcorecosmic.HardcoreCosmic;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Controla el arranque del modo hardcore (permadeath), que empieza
 * EXACTAMENTE 1 hora después de que la batalla se completa (dragón muerto).
 *
 * El estado se persiste en config.yml para sobrevivir un reinicio del
 * servidor durante esa ventana de 1 hora: si el server se reinicia a los 40
 * minutos de completada la batalla, al volver a arrancar se reprograma el
 * activador con los 20 minutos restantes en vez de reiniciar la cuenta
 * entera desde cero. Si el server se reinicia DESPUÉS de que ya se activó,
 * simplemente se recupera el estado "activo" y sigue como si nada.
 */
public class HardcoreManager {

    private final HardcoreCosmic plugin;
    private boolean hardcoreActive = false;
    private BukkitTask activationTask;
    private final java.util.Set<String> whitelist = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);

    private static final long DELAY_MILLIS = 60L * 60L * 1000L; // 1 hora

    public HardcoreManager(HardcoreCosmic plugin) {
        this.plugin = plugin;
    }

    /** Llamado una sola vez, justo cuando el dragón muere. */
    public void onBattleCompleted() {
        if (plugin.getConfig().getBoolean("state.hardcore-active", false)) {
            hardcoreActive = true;
            return;
        }
        if (plugin.getConfig().contains("state.hardcore-activate-at")) {
            // Ya había una cuenta regresiva programada (ej. llamada duplicada); no reiniciarla.
            return;
        }

        long activateAt = System.currentTimeMillis() + DELAY_MILLIS;
        plugin.getConfig().set("state.hardcore-activate-at", activateAt);
        plugin.saveConfig();
        scheduleActivation(activateAt);

        plugin.getLogger().info("[HardcoreCosmic] Modo hardcore programado para dentro de 1 hora.");
    }

    /** Llamado en onEnable para recuperar el estado tras un reinicio del servidor. */
    public void loadPersistedState() {
        if (plugin.getConfig().contains("state.hardcore-whitelist")) {
            whitelist.addAll(plugin.getConfig().getStringList("state.hardcore-whitelist"));
        }

        if (plugin.getConfig().getBoolean("state.hardcore-active", false)) {
            hardcoreActive = true;
            plugin.getLogger().info("[HardcoreCosmic] Modo hardcore ya estaba activo al reiniciar el servidor.");
            return;
        }
        long activateAt = plugin.getConfig().getLong("state.hardcore-activate-at", -1);
        if (activateAt > 0) {
            scheduleActivation(activateAt);
        }
    }

    private void scheduleActivation(long activateAtMillis) {
        long remainingMillis = activateAtMillis - System.currentTimeMillis();
        long delayTicks = Math.max(0L, remainingMillis / 50L); // 50ms por tick

        if (activationTask != null) activationTask.cancel();
        activationTask = Bukkit.getScheduler().runTaskLater(plugin, this::activateHardcoreMode, delayTicks);

        plugin.getLogger().info("[HardcoreCosmic] Activación de hardcore en " +
                Math.max(0, remainingMillis / 60000) + " minuto(s).");
    }

    private void activateHardcoreMode() {
        hardcoreActive = true;
        plugin.getConfig().set("state.hardcore-active", true);
        plugin.saveConfig();

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle(
                    ChatColor.DARK_RED + "" + ChatColor.BOLD + "☠ MODO HARDCORE ACTIVADO ☠",
                    ChatColor.RED + "A partir de ahora, morir es PERMANENTE.",
                    20, 100, 20
            );
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1f, 0.6f);
        }
        Bukkit.broadcastMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD + "☠ " + ChatColor.RESET +
                ChatColor.RED + "Ha pasado 1 hora desde la victoria contra el Dragón Cósmico. " +
                ChatColor.DARK_RED + "El modo HARDCORE está activo: cualquier muerte ahora es permanente.");
        plugin.getLogger().info("[HardcoreCosmic] Modo hardcore activado.");
    }

    public boolean isHardcoreActive() {
        return hardcoreActive;
    }

    // ─── DEBUG ────────────────────────────────────────────────────────────────

    /** Activa el modo hardcore de inmediato, saltándose la espera de 1 hora. */
    public void debugActivateNow() {
        if (hardcoreActive) return;
        if (activationTask != null) activationTask.cancel();
        activateHardcoreMode();
    }

    /**
     * Cancela cualquier cuenta regresiva pendiente y borra el estado persistido
     * (para /dragon debug resetstate). Si el hardcore ya estaba activo, esto lo
     * desactiva en memoria para esta sesión, pero eso NO revierte nada que ya
     * haya pasado (por ejemplo, si ya se echó a algún jugador por morir).
     */
    public void debugResetPersistedState() {
        if (activationTask != null) { activationTask.cancel(); activationTask = null; }
        hardcoreActive = false;
        plugin.getConfig().set("state.hardcore-active", false);
        plugin.getConfig().set("state.hardcore-activate-at", null);
        plugin.saveConfig();
    }

    /** Minutos restantes hasta la activación programada, 0 si ya está activo, -1 si no hay ninguna programada. */
    public long getMinutesUntilActivation() {
        if (hardcoreActive) return 0;
        long activateAt = plugin.getConfig().getLong("state.hardcore-activate-at", -1);
        if (activateAt <= 0) return -1;
        long remaining = activateAt - System.currentTimeMillis();
        return Math.max(0, remaining / 60000);
    }

    // ─── WHITELIST ────────────────────────────────────────────────────────────

    public boolean isWhitelisted(String playerName) {
        return whitelist.contains(playerName);
    }

    public boolean addWhitelist(String playerName) {
        if (whitelist.add(playerName)) {
            plugin.getConfig().set("state.hardcore-whitelist", new java.util.ArrayList<>(whitelist));
            plugin.saveConfig();
            return true;
        }
        return false;
    }

    public boolean removeWhitelist(String playerName) {
        if (whitelist.remove(playerName)) {
            plugin.getConfig().set("state.hardcore-whitelist", new java.util.ArrayList<>(whitelist));
            plugin.saveConfig();
            return true;
        }
        return false;
    }

    public java.util.List<String> getWhitelist() {
        return new java.util.ArrayList<>(whitelist);
    }
}