package com.cosmicraft.hardcorecosmic.managers;

import com.cosmicraft.hardcorecosmic.HardcoreCosmic;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Gestiona la degradación de netherite al morir el Dragón Cósmico.
 *
 * COMPORTAMIENTO:
 *  - El downgrade de items es un evento ÚNICO e IRREVERSIBLE.
 *    /hardcore netherite on  → degrada todos los items + bloquea el crafteo de la plantilla
 *    /hardcore netherite off → desbloquea el crafteo, limpia pendientes; los items NO se tocan
 *
 *  - El bloqueo del crafteo de la plantilla de herrería es PERSISTENTE:
 *    sobrevive reinicios (el flag state.netherite-downgraded persiste en config.yml).
 *    En vez de eliminar la receta del servidor (lo que causaba errores de receta
 *    no reconocida al recargar), se bloquea el resultado via PrepareItemCraftEvent:
 *    la receta sigue registrada, pero el slot de resultado queda vacío si el flag
 *    está activo. Así no tocamos el sistema de recetas en absoluto.
 */
public class NetheriteDowngradeManager implements Listener {

    private final HardcoreCosmic plugin;

    private static final Map<Material, Material> DOWNGRADE_MAP = new EnumMap<>(Material.class);

    static {
        DOWNGRADE_MAP.put(Material.NETHERITE_HELMET,     Material.DIAMOND_HELMET);
        DOWNGRADE_MAP.put(Material.NETHERITE_CHESTPLATE, Material.DIAMOND_CHESTPLATE);
        DOWNGRADE_MAP.put(Material.NETHERITE_LEGGINGS,   Material.DIAMOND_LEGGINGS);
        DOWNGRADE_MAP.put(Material.NETHERITE_BOOTS,      Material.DIAMOND_BOOTS);
        DOWNGRADE_MAP.put(Material.NETHERITE_SWORD,      Material.DIAMOND_SWORD);
        DOWNGRADE_MAP.put(Material.NETHERITE_PICKAXE,    Material.DIAMOND_PICKAXE);
        DOWNGRADE_MAP.put(Material.NETHERITE_AXE,        Material.DIAMOND_AXE);
        DOWNGRADE_MAP.put(Material.NETHERITE_SHOVEL,     Material.DIAMOND_SHOVEL);
        DOWNGRADE_MAP.put(Material.NETHERITE_HOE,        Material.DIAMOND_HOE);
        DOWNGRADE_MAP.put(Material.NETHERITE_BLOCK,      Material.DIAMOND_BLOCK);
        DOWNGRADE_MAP.put(Material.NETHERITE_INGOT,      Material.DIAMOND);
    }

    public NetheriteDowngradeManager(HardcoreCosmic plugin) {
        this.plugin = plugin;
    }

    // ─── BLOQUEO DE CRAFTEO VIA EVENTO ───────────────────────────────────────

    /**
     * Cancela el crafteo de la Plantilla de Herrería de Netherite mientras el
     * downgrade esté activo. No toca el sistema de recetas — simplemente vacía
     * el slot de resultado cuando el jugador intenta craftearla.
     */
    @EventHandler
    public void onPrepareItemCraft(PrepareItemCraftEvent event) {
        if (!isDowngraded()) return;
        ItemStack result = event.getInventory().getResult();
        if (result != null && result.getType() == Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE) {
            event.getInventory().setResult(new ItemStack(Material.AIR));
        }
    }

    // ─── DISPARADOR PRINCIPAL (/hardcore netherite on / dragón muerto) ────────

    /**
     * Degrada todos los items de netherite del mundo (una sola vez, irreversible)
     * y activa el bloqueo del crafteo de la plantilla de herrería.
     */
    public void runFullWorldDowngrade() {
        plugin.getConfig().set("state.netherite-downgraded", true);
        plugin.saveConfig();

        // Jugadores online: degradar ahora mismo
        for (Player p : Bukkit.getOnlinePlayers()) {
            downgradePlayerInventory(p);
        }

        // Jugadores offline: encolar para degradarles en su próximo login
        List<String> pending = new ArrayList<>();
        for (org.bukkit.OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            if (!op.isOnline()) pending.add(op.getUniqueId().toString());
        }
        plugin.getConfig().set("state.netherite-pending-players", pending);
        plugin.saveConfig();

        // Chunks ya cargados en memoria — procesados en lotes para no congelar el servidor
        List<Chunk> allChunks = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            allChunks.addAll(Arrays.asList(world.getLoadedChunks()));
        }
        processChunksInBatches(allChunks);

        // Sonido + mensaje de lore (inmediato, antes de que empiece el procesamiento en lotes)
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), "minecraft:entity.warden.emerge",
                    SoundCategory.MASTER, 2f, 0.8f);
        }
        Bukkit.broadcastMessage(
            ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "⚠ " +
            ChatColor.DARK_PURPLE + "La energía liberada por la muerte del Dragón Cósmico ha " +
            ChatColor.LIGHT_PURPLE + "corrompido y pulverizado" +
            ChatColor.DARK_PURPLE + " el recubrimiento de netherita de todas las armaduras y herramientas."
        );

        plugin.getLogger().info("[HardcoreCosmic] Netherite degradado. Jugadores offline pendientes: " + pending.size());
    }

    // ─── /hardcore netherite off ──────────────────────────────────────────────

    /**
     * Desactiva el bloqueo del crafteo de la plantilla y limpia la lista de pendientes.
     * Los items degradados NO se tocan — el downgrade es irreversible.
     */
    public void disableDowngradeState() {
        plugin.getConfig().set("state.netherite-downgraded", false);
        plugin.getConfig().set("state.netherite-pending-players", new ArrayList<>());
        plugin.saveConfig();
        plugin.getLogger().info("[HardcoreCosmic] Estado de downgrade desactivado. Crafteo de plantilla restaurado.");
    }

    // ─── ON ENABLE ───────────────────────────────────────────────────────────

    /**
     * Llamado en onEnable. No hay nada que hacer para las recetas porque el
     * bloqueo funciona vía evento — solo se loguea el estado actual.
     */
    public void restoreStateOnEnable() {
        if (!plugin.getConfig().getBoolean("state.netherite-downgraded", false)) return;
        plugin.getLogger().info("[HardcoreCosmic] Downgrade de netherite activo — crafteo de plantilla bloqueado vía evento.");
    }

    // ─── LOGIN DE JUGADORES PENDIENTES ───────────────────────────────────────

    /**
     * Degrada el inventario de jugadores que estaban offline cuando se ejecutó
     * el downgrade. Se procesa una única vez por jugador.
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!plugin.getConfig().getBoolean("state.netherite-downgraded", false)) return;

        Player player = event.getPlayer();
        List<String> pending = new ArrayList<>(
                plugin.getConfig().getStringList("state.netherite-pending-players"));

        if (!pending.remove(player.getUniqueId().toString())) return; // ya procesado

        downgradePlayerInventory(player);
        plugin.getConfig().set("state.netherite-pending-players", pending);
        plugin.saveConfig();
    }

    // ─── DOWNGRADE DE ITEMS ───────────────────────────────────────────────────

    private ItemStack downgrade(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return null;
        Material target = DOWNGRADE_MAP.get(item.getType());
        if (target == null) return null;

        int oldMax = item.getType().getMaxDurability();
        ItemMeta oldMeta = item.getItemMeta();

        ItemStack newItem = item.clone();
        newItem.setType(target);

        if (oldMax > 0 && oldMeta instanceof Damageable oldDamageable
                && newItem.getItemMeta() instanceof Damageable newDamageable) {
            int newMax = target.getMaxDurability();
            int scaled = newMax > 0
                    ? (int) Math.round(((double) oldDamageable.getDamage() / oldMax) * newMax)
                    : 0;
            newDamageable.setDamage(Math.min(scaled, Math.max(newMax, 0)));
            newItem.setItemMeta((ItemMeta) newDamageable);
        }
        return newItem;
    }

    private void downgradeInventory(Inventory inv) {
        if (inv == null) return;
        ItemStack[] contents = inv.getContents();
        boolean changed = false;
        for (int i = 0; i < contents.length; i++) {
            ItemStack d = downgrade(contents[i]);
            if (d != null) { contents[i] = d; changed = true; }
        }
        if (changed) inv.setContents(contents);
    }

    private void downgradeEquipment(EntityEquipment eq) {
        ItemStack[] armor = eq.getArmorContents();
        boolean changed = false;
        for (int i = 0; i < armor.length; i++) {
            ItemStack d = downgrade(armor[i]);
            if (d != null) { armor[i] = d; changed = true; }
        }
        if (changed) eq.setArmorContents(armor);

        ItemStack main = downgrade(eq.getItemInMainHand());
        if (main != null) eq.setItemInMainHand(main);
        ItemStack off = downgrade(eq.getItemInOffHand());
        if (off != null) eq.setItemInOffHand(off);
    }

    public void downgradePlayerInventory(Player player) {
        downgradeInventory(player.getInventory());

        ItemStack[] armor = player.getInventory().getArmorContents();
        boolean changed = false;
        for (int i = 0; i < armor.length; i++) {
            ItemStack d = downgrade(armor[i]);
            if (d != null) { armor[i] = d; changed = true; }
        }
        if (changed) player.getInventory().setArmorContents(armor);

        ItemStack offhand = downgrade(player.getInventory().getItemInOffHand());
        if (offhand != null) player.getInventory().setItemInOffHand(offhand);

        downgradeInventory(player.getEnderChest());
        player.updateInventory();
    }

    // ─── PROCESAMIENTO EN LOTES ───────────────────────────────────────────────

    /**
     * Procesa chunks en lotes de BATCH_SIZE por tick para no congelar el hilo
     * principal. Entre lote y lote el servidor tiene un tick libre para física,
     * jugadores, red, etc.
     *
     * Con BATCH_SIZE = 5 y ~20 TPS:
     *   100 chunks → ~0.5 s  |  500 chunks → ~2.5 s  (sin lag perceptible)
     */
    private static final int BATCH_SIZE = 1;
    private boolean sweepInProgress = false;

    private void processChunksInBatches(List<Chunk> chunks) {
        if (chunks.isEmpty()) return;
        sweepInProgress = true;
        scheduleBatch(chunks, 0);
    }

    private void scheduleBatch(List<Chunk> chunks, int offset) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            int end = Math.min(offset + BATCH_SIZE, chunks.size());
            for (int i = offset; i < end; i++) {
                Chunk chunk = chunks.get(i);
                if (chunk.isLoaded()) processChunk(chunk);
            }
            if (end < chunks.size()) {
                scheduleBatch(chunks, end);
            } else {
                sweepInProgress = false;
                plugin.getLogger().info("[HardcoreCosmic] Procesamiento de chunks completado.");
            }
        }, 1L);
    }

    private void processChunk(Chunk chunk) {
        // Contenedores (cofres, barriles, hoppers, etc.)
        for (BlockState state : chunk.getTileEntities()) {
            if (state instanceof InventoryHolder holder) {
                downgradeInventory(holder.getInventory());
            }
        }

        // Entidades: jugadores, ítems en el suelo, item frames, mobs con equipo
        for (Entity e : chunk.getEntities()) {
            if (e instanceof Player player) {
                downgradePlayerInventory(player);
            } else if (e instanceof Item item) {
                ItemStack d = downgrade(item.getItemStack());
                if (d != null) item.setItemStack(d);
            } else if (e instanceof ItemFrame frame) {
                ItemStack d = downgrade(frame.getItem());
                if (d != null) frame.setItem(d);
            } else {
                if (e instanceof LivingEntity living && living.getEquipment() != null) {
                    downgradeEquipment(living.getEquipment());
                }
                if (e instanceof InventoryHolder holder) {
                    downgradeInventory(holder.getInventory());
                }
            }
        }
    }

    // ─── ESTADO ───────────────────────────────────────────────────────────────

    public boolean isDowngraded() {
        return plugin.getConfig().getBoolean("state.netherite-downgraded", false);
    }

    public boolean isSweepComplete()   { return isDowngraded() && !sweepInProgress; }
    public boolean isSweepInProgress() { return sweepInProgress; }

    // ─── ALIAS PARA EL COMANDO ────────────────────────────────────────────────

    public void debugRunDowngrade() { runFullWorldDowngrade(); }
}