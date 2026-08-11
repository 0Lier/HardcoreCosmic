package com.cosmicraft.hardcorecosmic.commands;

import com.cosmicraft.hardcorecosmic.HardcoreCosmic;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * Comando /hardcore — control total del plugin desde consola o en juego.
 *
 * Subcomandos:
 *   /hardcore start               → Programa el modo hardcore a 1 hora vista
 *   /hardcore start_now           → Activa el modo hardcore de inmediato
 *   /hardcore reset               → Resetea el estado del hardcore (para testing)
 *   /hardcore hardcore on         → Activa hardcore de inmediato (alias de start_now)
 *   /hardcore hardcore off        → Desactiva hardcore y limpia el estado persistido
 *   /hardcore netherite on        → Degrada todo el netherite a diamante
 *   /hardcore netherite off       → Revierte el downgrade (diamante → netherite)
 *   /hardcore downgrade_netherite → Alias legacy de "netherite on"
 */
public class HardcoreCommand implements CommandExecutor {

    private final HardcoreCosmic plugin;

    public HardcoreCommand(HardcoreCosmic plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("hardcorecosmic.admin")) {
            sender.sendMessage(ChatColor.RED + "No tienes permisos.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {

            // ── Hardcore ─────────────────────────────────────────────────────
            case "start" -> {
                plugin.getHardcoreManager().onBattleCompleted();
                sender.sendMessage(ChatColor.GREEN + "Cuenta regresiva del modo Hardcore iniciada (1 hora).");
            }
            case "start_now" -> {
                plugin.getHardcoreManager().debugActivateNow();
                sender.sendMessage(ChatColor.GREEN + "Modo Hardcore activado de inmediato.");
            }
            case "reset" -> {
                plugin.getHardcoreManager().debugResetPersistedState();
                sender.sendMessage(ChatColor.GREEN + "Estado del hardcore reseteado.");
            }

            // ── /hardcore hardcore on|off ─────────────────────────────────
            case "hardcore" -> {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Uso: /hardcore hardcore <on|off>");
                    return true;
                }
                switch (args[1].toLowerCase()) {
                    case "on" -> {
                        plugin.getHardcoreManager().debugActivateNow();
                        sender.sendMessage(ChatColor.GREEN + "Modo Hardcore " + ChatColor.BOLD + "ACTIVADO.");
                    }
                    case "off" -> {
                        plugin.getHardcoreManager().debugResetPersistedState();
                        sender.sendMessage(ChatColor.YELLOW + "Modo Hardcore " + ChatColor.BOLD + "DESACTIVADO" +
                                ChatColor.YELLOW + " y estado limpiado.");
                    }
                    default -> sender.sendMessage(ChatColor.RED + "Uso: /hardcore hardcore <on|off>");
                }
            }

            // ── /hardcore netherite on|off ────────────────────────────────
            case "netherite" -> {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Uso: /hardcore netherite <on|off>");
                    return true;
                }
                switch (args[1].toLowerCase()) {
                    case "on" -> {
                        if (plugin.getNetheriteDowngradeManager().isDowngraded()) {
                            sender.sendMessage(ChatColor.YELLOW + "El netherite ya está degradado.");
                            return true;
                        }
                        plugin.getNetheriteDowngradeManager().runFullWorldDowngrade();
                        sender.sendMessage(ChatColor.GREEN + "Downgrade de netherite " + ChatColor.BOLD + "ejecutado.");
                    }
                    case "off" -> {
                        if (!plugin.getNetheriteDowngradeManager().isDowngraded()) {
                            sender.sendMessage(ChatColor.YELLOW + "El downgrade de netherite no está activo.");
                            return true;
                        }
                        plugin.getNetheriteDowngradeManager().disableDowngradeState();
                        sender.sendMessage(ChatColor.GREEN + "Receta de plantilla de netherite " +
                                ChatColor.BOLD + "restaurada" + ChatColor.GREEN +
                                " y pendientes limpiados. Los items degradados no se revierten.");
                    }
                    default -> sender.sendMessage(ChatColor.RED + "Uso: /hardcore netherite <on|off>");
                }
            }

            // ── Whitelist ───────────────────────────────────────────────────
            case "whitelist" -> {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Uso: /hardcore whitelist <add|remove|list> [jugador]");
                    return true;
                }
                switch (args[1].toLowerCase()) {
                    case "add" -> {
                        if (args.length < 3) {
                            sender.sendMessage(ChatColor.RED + "Especifica el nombre del jugador.");
                            return true;
                        }
                        String target = args[2];
                        if (plugin.getHardcoreManager().addWhitelist(target)) {
                            sender.sendMessage(ChatColor.GREEN + "Jugador " + target + " añadido a la whitelist (inmune a permadeath).");
                        } else {
                            sender.sendMessage(ChatColor.YELLOW + "El jugador " + target + " ya estaba en la whitelist.");
                        }
                    }
                    case "remove" -> {
                        if (args.length < 3) {
                            sender.sendMessage(ChatColor.RED + "Especifica el nombre del jugador.");
                            return true;
                        }
                        String target = args[2];
                        if (plugin.getHardcoreManager().removeWhitelist(target)) {
                            sender.sendMessage(ChatColor.GREEN + "Jugador " + target + " removido de la whitelist.");
                        } else {
                            sender.sendMessage(ChatColor.YELLOW + "El jugador " + target + " no estaba en la whitelist.");
                        }
                    }
                    case "list" -> {
                        java.util.List<String> wlist = plugin.getHardcoreManager().getWhitelist();
                        if (wlist.isEmpty()) {
                            sender.sendMessage(ChatColor.YELLOW + "La whitelist está vacía.");
                        } else {
                            sender.sendMessage(ChatColor.GREEN + "Jugadores en whitelist: " + ChatColor.WHITE + String.join(", ", wlist));
                        }
                    }
                    default -> sender.sendMessage(ChatColor.RED + "Uso: /hardcore whitelist <add|remove|list> [jugador]");
                }
            }

            // ── Alias legacy ──────────────────────────────────────────────
            case "downgrade_netherite" -> {
                plugin.getNetheriteDowngradeManager().runFullWorldDowngrade();
                sender.sendMessage(ChatColor.GREEN + "Downgrade de netherite ejecutado.");
            }

            default -> sendHelp(sender);
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "── HardcoreCosmic ──────────────────────────────");
        sender.sendMessage(ChatColor.YELLOW + "/hardcore start"              + ChatColor.GRAY + "               → Programa hardcore (1 h)");
        sender.sendMessage(ChatColor.YELLOW + "/hardcore start_now"          + ChatColor.GRAY + "           → Activa hardcore ahora");
        sender.sendMessage(ChatColor.YELLOW + "/hardcore reset"              + ChatColor.GRAY + "               → Limpia estado del hardcore");
        sender.sendMessage(ChatColor.YELLOW + "/hardcore hardcore on"        + ChatColor.GRAY + "         → Activa hardcore (testing)");
        sender.sendMessage(ChatColor.YELLOW + "/hardcore hardcore off"       + ChatColor.GRAY + "        → Desactiva hardcore (testing)");
        sender.sendMessage(ChatColor.YELLOW + "/hardcore netherite on"       + ChatColor.GRAY + "        → Degrada netherite → diamante");
        sender.sendMessage(ChatColor.YELLOW + "/hardcore netherite off"      + ChatColor.GRAY + "       → Restaura diamante → netherite");
        sender.sendMessage(ChatColor.YELLOW + "/hardcore whitelist <add|remove|list>" + ChatColor.GRAY + " → Inmunes al permadeath");
        sender.sendMessage(ChatColor.GOLD + "────────────────────────────────────────────────");
    }
}