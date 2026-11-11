package it.itsHyr0xx.pluginaziendeee;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.InheritanceNode;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PluginAziende extends JavaPlugin implements Listener, TabExecutor {
    private final Map<String, Company> companies = new HashMap<>();
    private final Map<UUID, Invite> invites = new ConcurrentHashMap<>();
    private LuckPerms luckPerms;
    private File dataFile;

    @Override
    public void onEnable() {
        RegisteredServiceProvider<LuckPerms> provider = getServer().getServicesManager().getRegistration(LuckPerms.class);
        if (provider == null) {
            getLogger().severe("LuckPerms non è installato: PluginAziende verrà disabilitato.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        luckPerms = provider.getProvider();
        dataFile = new File(getDataFolder(), "aziende.yml");
        loadCompanies();
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("staffaziende").setExecutor(this);
        getCommand("staffaziende").setTabCompleter(this);
        getCommand("azienda").setExecutor(this);
        getCommand("azienda").setTabCompleter(this);
    }

    @Override
    public void onDisable() {
        saveCompanies();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("staffaziende")) {
            return handleStaffCommand(sender, args);
        }
        return handleCompanyCommand(sender, args);
    }

    private boolean handleStaffCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("staffaziende")) {
            sender.sendMessage("§cNon hai il permesso staffaziende.");
            return true;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("crea") && args[1].equalsIgnoreCase("rango")) {
            if (args.length != 5) {
                sender.sendMessage("§eUso: /staffaziende crea rango <azienda> <nome-rango> <gruppo-luckperms>");
                return true;
            }
            Company company = companies.get(key(args[2]));
            if (company == null) {
                sender.sendMessage("§cAzienda non trovata.");
                return true;
            }
            String rankKey = key(args[3]);
            String groupName = args[4];
            if (rankKey.equals("capo") || rankKey.equals("__capo")) {
                sender.sendMessage("§cQuesto nome rango è riservato.");
                return true;
            }
            if (luckPerms.getGroupManager().getGroup(groupName) == null) {
                sender.sendMessage("§cIl gruppo LuckPerms '" + groupName + "' non esiste.");
                return true;
            }
            if (company.ranks.containsKey(rankKey)) {
                sender.sendMessage("§cEsiste già un rango con questo nome.");
                return true;
            }
            company.ranks.put(rankKey, new Rank(args[3].replace('_', ' '), groupName));
            saveCompanies();
            sender.sendMessage("§aRango " + args[3] + " creato per " + company.name + ".");
            return true;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("crea")) {
            String id = key(args[1]);
            String groupName = args[2];
            if (companies.containsKey(id)) {
                sender.sendMessage("§cQuesta azienda esiste già.");
                return true;
            }
            if (luckPerms.getGroupManager().getGroup(groupName) == null) {
                sender.sendMessage("§cIl gruppo LuckPerms '" + groupName + "' non esiste.");
                return true;
            }
            Company company = new Company(id, args[1].replace('_', ' '), groupName);
            companies.put(id, company);
            saveCompanies();
            sender.sendMessage("§aAzienda " + company.name + " creata. Rango massimo LuckPerms: " + groupName + ".");
            sender.sendMessage("§7Il capo può reclamarla con §f/azienda capo " + args[1] + "§7.");
            return true;
        }
        sender.sendMessage("§eComandi: /staffaziende crea <azienda> <gruppo-massimo>");
        sender.sendMessage("§e/staffaziende crea rango <azienda> <nome-rango> <gruppo-luckperms>");
        return true;
    }

    private boolean handleCompanyCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cQuesto comando può essere usato solo in gioco.");
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("capo")) {
            Company company = companies.get(key(args[1]));
            if (company == null) {
                player.sendMessage("§cAzienda non trovata.");
                return true;
            }
            if (company.owner != null) {
                player.sendMessage("§cQuesta azienda ha già un capo.");
                return true;
            }
            company.owner = player.getUniqueId();
            company.members.put(player.getUniqueId(), "__capo");
            saveCompanies();
            setLuckPermsGroup(player.getUniqueId(), company, "__capo", company.maxGroup, "§aOra sei il capo di " + company.name + ".");
            return true;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("assumi")) {
            return issueInvite(player, args[1], args[2]);
        }
        player.sendMessage("§e/azienda capo <azienda> §7- Reclama un'azienda senza capo.");
        player.sendMessage("§e/azienda assumi <giocatore> <rango> §7- Invita un giocatore vicino.");
        return true;
    }

    private boolean issueInvite(Player employer, String playerName, String rankName) {
        Company company = findOwnedCompany(employer.getUniqueId());
        if (company == null) {
            employer.sendMessage("§cNon sei il capo di un'azienda.");
            return true;
        }
        Rank rank = company.ranks.get(key(rankName));
        if (rank == null) {
            employer.sendMessage("§cRango non trovato. Rangi disponibili: " + String.join(", ", company.ranks.keySet()));
            return true;
        }
        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            employer.sendMessage("§cIl giocatore deve essere online.");
            return true;
        }
        if (target.equals(employer)) {
            employer.sendMessage("§cNon puoi assumere te stesso.");
            return true;
        }
        if (!target.getWorld().equals(employer.getWorld()) || target.getLocation().distanceSquared(employer.getLocation()) > 100) {
            employer.sendMessage("§cIl giocatore è troppo lontano (massimo 10 blocchi).");
            return true;
        }
        Invite invite = new Invite(employer.getUniqueId(), target.getUniqueId(), company.id, key(rankName), System.currentTimeMillis() + 60_000L);
        invites.put(target.getUniqueId(), invite);
        openInvite(target, invite, company, rank);
        employer.sendMessage("§aInvito inviato a " + target.getName() + ".");
        return true;
    }

    private void openInvite(Player target, Invite invite, Company company, Rank rank) {
        InviteHolder holder = new InviteHolder(invite);
        Inventory inventory = Bukkit.createInventory(holder, 9, Component.text("Invito: " + company.name));
        holder.inventory = inventory;
        ItemStack accept = button("§aAccetta", "§7Entra in " + company.name + " come " + rank.name);
        ItemStack decline = button("§cRifiuta", "§7Rifiuta l'invito di " + company.name);
        inventory.setItem(3, accept);
        inventory.setItem(5, decline);
        target.openInventory(inventory);
        target.sendMessage("§eHai ricevuto un invito di assunzione da " + Bukkit.getOfflinePlayer(invite.employer).getName() + ".");
    }

    private ItemStack button(String title, String lore) {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(title));
        meta.lore(java.util.List.of(Component.text(lore)));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof InviteHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (event.getRawSlot() != 3 && event.getRawSlot() != 5) return;
        Invite invite = holder.invite;
        if (!player.getUniqueId().equals(invite.target)) return;
        player.closeInventory();
        if (event.getRawSlot() == 5) {
            invites.remove(player.getUniqueId(), invite);
            player.sendMessage("§7Hai rifiutato l'invito.");
            notifyEmployer(invite, "§c" + player.getName() + " ha rifiutato l'invito.");
            return;
        }
        if (invite.expiresAt < System.currentTimeMillis()) {
            invites.remove(player.getUniqueId(), invite);
            player.sendMessage("§cL'invito è scaduto.");
            return;
        }
        Player employer = Bukkit.getPlayer(invite.employer);
        if (employer == null || !employer.getWorld().equals(player.getWorld()) || employer.getLocation().distanceSquared(player.getLocation()) > 100) {
            invites.remove(player.getUniqueId(), invite);
            player.sendMessage("§cIl capo è troppo lontano: dovete essere entro 10 blocchi.");
            return;
        }
        Company company = companies.get(invite.companyId);
        Rank rank = company == null ? null : company.ranks.get(invite.rankKey);
        if (company == null || rank == null || !invite.equals(invites.get(player.getUniqueId()))) {
            player.sendMessage("§cQuesto invito non è più valido.");
            return;
        }
        invites.remove(player.getUniqueId(), invite);
        company.members.put(player.getUniqueId(), invite.rankKey);
        saveCompanies();
        setLuckPermsGroup(player.getUniqueId(), company, invite.rankKey, rank.group, "§aSei stato assunto in " + company.name + " come " + rank.name + ".");
        employer.sendMessage("§a" + player.getName() + " ha accettato l'assunzione.");
    }

    private void setLuckPermsGroup(UUID uuid, Company company, String rankKey, String groupName, String successMessage) {
        Group group = luckPerms.getGroupManager().getGroup(groupName);
        if (group == null) {
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) online.sendMessage("§cIl gruppo LuckPerms configurato non esiste più.");
            return;
        }
        luckPerms.getUserManager().loadUser(uuid).thenCompose(user -> {
            for (String companyGroup : company.allGroups()) {
                user.data().remove(Node.builder("group." + companyGroup).build());
            }
            user.data().add(InheritanceNode.builder(groupName).build());
            return luckPerms.getUserManager().saveUser(user);
        }).whenComplete((ignored, error) -> Bukkit.getScheduler().runTask(this, () -> {
            Player online = Bukkit.getPlayer(uuid);
            if (online == null) return;
            if (error != null) {
                getLogger().warning("Errore assegnando il gruppo LuckPerms " + groupName + " a " + uuid + ": " + error.getMessage());
                online.sendMessage("§cNon è stato possibile aggiornare il tuo rango LuckPerms.");
            } else {
                online.sendMessage(successMessage);
            }
        }));
    }

    private void notifyEmployer(Invite invite, String message) {
        Player employer = Bukkit.getPlayer(invite.employer);
        if (employer != null) employer.sendMessage(message);
    }

    private Company findOwnedCompany(UUID uuid) {
        return companies.values().stream().filter(company -> uuid.equals(company.owner)).findFirst().orElse(null);
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("staffaziende")) {
            if (args.length == 1) return matches(args[0], "crea");
            if (args.length == 2 && args[0].equalsIgnoreCase("crea")) return matches(args[1], "rango");
            if (args.length == 3 && args[0].equalsIgnoreCase("crea") && args[1].equalsIgnoreCase("rango")) return matches(args[2], companies.keySet().toArray(String[]::new));
            return new ArrayList<>();
        }
        if (args.length == 1) return matches(args[0], "capo", "assumi");
        if (args.length == 2 && args[0].equalsIgnoreCase("capo")) return matches(args[1], companies.keySet().toArray(String[]::new));
        if (args.length == 3 && args[0].equalsIgnoreCase("assumi") && sender instanceof Player player) {
            Company company = findOwnedCompany(player.getUniqueId());
            return company == null ? new ArrayList<>() : matches(args[2], company.ranks.keySet().toArray(String[]::new));
        }
        return new ArrayList<>();
    }

    private java.util.List<String> matches(String input, String... options) {
        String prefix = input.toLowerCase(Locale.ROOT);
        return java.util.Arrays.stream(options).filter(option -> option.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }

    private String key(String value) {
        return value.toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private void loadCompanies() {
        if (!dataFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection root = yaml.getConfigurationSection("companies");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            String path = "companies." + id;
            String name = yaml.getString(path + ".name", id);
            String maxGroup = yaml.getString(path + ".max-group", "");
            Company company = new Company(id, name, maxGroup);
            String owner = yaml.getString(path + ".owner");
            if (owner != null) try { company.owner = UUID.fromString(owner); } catch (IllegalArgumentException ignored) { }
            ConfigurationSection ranks = yaml.getConfigurationSection(path + ".ranks");
            if (ranks != null) for (String rankKey : ranks.getKeys(false)) {
                company.ranks.put(rankKey, new Rank(ranks.getString(rankKey + ".name", rankKey), ranks.getString(rankKey + ".group", "")));
            }
            ConfigurationSection members = yaml.getConfigurationSection(path + ".members");
            if (members != null) for (String member : members.getKeys(false)) {
                try { company.members.put(UUID.fromString(member), members.getString(member, "")); } catch (IllegalArgumentException ignored) { }
            }
            companies.put(id, company);
        }
    }

    private void saveCompanies() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Company company : companies.values()) {
            String path = "companies." + company.id;
            yaml.set(path + ".name", company.name);
            yaml.set(path + ".max-group", company.maxGroup);
            yaml.set(path + ".owner", company.owner == null ? null : company.owner.toString());
            for (Map.Entry<String, Rank> entry : company.ranks.entrySet()) {
                yaml.set(path + ".ranks." + entry.getKey() + ".name", entry.getValue().name);
                yaml.set(path + ".ranks." + entry.getKey() + ".group", entry.getValue().group);
            }
            for (Map.Entry<UUID, String> member : company.members.entrySet()) {
                yaml.set(path + ".members." + member.getKey(), member.getValue());
            }
        }
        try {
            if (!getDataFolder().exists() && !getDataFolder().mkdirs()) throw new IOException("Impossibile creare la cartella dati");
            yaml.save(dataFile);
        } catch (IOException exception) {
            getLogger().severe("Impossibile salvare aziende.yml: " + exception.getMessage());
        }
    }

    private static final class Company {
        private final String id;
        private final String name;
        private final String maxGroup;
        private UUID owner;
        private final Map<String, Rank> ranks = new HashMap<>();
        private final Map<UUID, String> members = new HashMap<>();

        private Company(String id, String name, String maxGroup) {
            this.id = id;
            this.name = name;
            this.maxGroup = maxGroup;
        }

        private java.util.Set<String> allGroups() {
            java.util.Set<String> groups = new java.util.HashSet<>();
            groups.add(maxGroup);
            ranks.values().forEach(rank -> groups.add(rank.group));
            return groups;
        }
    }

    private record Rank(String name, String group) { }
    private record Invite(UUID employer, UUID target, String companyId, String rankKey, long expiresAt) { }

    private static final class InviteHolder implements InventoryHolder {
        private final Invite invite;
        private Inventory inventory;
        private InviteHolder(Invite invite) { this.invite = invite; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
