package com.example.everpingproxy;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Plugin(id = "everpingproxy", name = "EverPingProxy", version = "1.0", authors = {"Salmone"})
public class EverPingProxy {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private ConfigurationNode config;
    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.legacyAmpersand();

    @Inject
    public EverPingProxy(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        init();
    }

    public void init() {
        logger.info("EverPingProxy abilitato!");
        updateConfig();
        loadConfig();
        server.getCommandManager().register("ping", new PingCommand(), "latency");
        server.getCommandManager().register("everping", new EverPingCommand(), "ep");
        server.getEventManager().register(this, new DisconnectListener());
    }

    private void updateConfig() {
        File configFile = new File(dataDirectory.toFile(), "config.yml");

        try {
            if (!configFile.exists()) {
                logger.info("Creazione della configurazione...");
                downloadFile("https://raw.githubusercontent.com/Salmone30/Congig/main/config.yml", configFile);
            } else {
                ConfigurationNode configNode = YamlConfigurationLoader.builder().file(configFile).build().load();
                ConfigurationNode versionNode = configNode.node("version");
                if (versionNode.virtual() || !versionNode.getString().equals("1.0")) {
                    logger.info("Aggiornamento della configurazione...");
                    downloadFile("https://raw.githubusercontent.com/Salmone30/Congig/main/config.yml", configFile);
                }
            }
        } catch (Exception e) {
            logger.severe("Errore durante l'aggiornamento della configurazione: " + e.getMessage());
            e.printStackTrace();
        }

        loadConfig();
    }

    private void downloadFile(String fileUrl, File outputFile) throws IOException {
        File parentDir = outputFile.getParentFile();
        if (!parentDir.exists() && !parentDir.mkdirs()) {
            throw new IOException("Impossibile creare la directory: " + parentDir.getAbsolutePath());
        }

        URL url = new URL(fileUrl);
        try (InputStream inputStream = url.openStream()) {
            Files.copy(inputStream, outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void loadConfig() {
        File configFile = new File(dataDirectory.toFile(), "config.yml");
        YamlConfigurationLoader loader = YamlConfigurationLoader.builder().file(configFile).build();
        try {
            config = loader.load();
            if (config.node("messages", "own_ping").virtual()) {
                config.node("messages", "own_ping").set("&aIl tuo ping è di: &b{ping}ms");
                config.node("messages", "other_ping").set("&aIl ping di &b{player} &aè di: &b{ping}ms");
                config.node("messages", "player_not_found").set("&cIl giocatore specificato non è online o non esiste!");
                config.node("messages", "player_only").set("&cSolo i giocatori possono usare questo comando!");
                config.node("messages", "usage").set("&eUsa il comando nel modo corretto: &b/ping &eoppure &b/ping <giocatore>");
                loader.save(config);
            }
        } catch (IOException e) {
            logger.severe("Impossibile caricare il file di configurazione!");
            e.printStackTrace();
        }
    }

    public class DisconnectListener {
        @Subscribe
        public void onPlayerDisconnect(DisconnectEvent event) {
            logger.info(event.getPlayer().getUsername() + " si è disconnesso.");
        }
    }

    public class PingCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            CommandSource source = invocation.source();
            String[] args = invocation.arguments();

            if (args.length == 0) {
                if (source instanceof Player) {
                    Player player = (Player) source;
                    long ping = player.getPing();
                    String message = config.node("messages", "own_ping").getString()
                            .replace("{ping}", String.valueOf(ping));
                    player.sendMessage(legacySerializer.deserialize(message));
                } else {
                    source.sendMessage(legacySerializer.deserialize(config.node("messages", "player_only").getString()));
                }
            } else if (args.length == 1) {
                Optional<Player> target = server.getPlayer(args[0]);
                if (target.isPresent() && target.get().isActive()) {
                    long ping = target.get().getPing();
                    String message = config.node("messages", "other_ping").getString()
                            .replace("{player}", target.get().getUsername())
                            .replace("{ping}", String.valueOf(ping));
                    source.sendMessage(legacySerializer.deserialize(message));
                } else {
                    source.sendMessage(legacySerializer.deserialize(config.node("messages", "player_not_found").getString()));
                }
            } else {
                source.sendMessage(legacySerializer.deserialize(config.node("messages", "usage").getString()));
            }
        }

        @Override
        public List<String> suggest(Invocation invocation) {
            return server.getAllPlayers().stream()
                    .map(Player::getUsername)
                    .collect(Collectors.toList());
        }
    }

    public class EverPingCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            CommandSource source = invocation.source();
            String[] args = invocation.arguments();

            if (!source.hasPermission("everping.admin")) {
                source.sendMessage(legacySerializer.deserialize("&cNon hai il permesso di utilizzare questo comando!"));
                return;
            }

            if (args.length == 0) {
                sendHelpMessage(source);
                return;
            }

            switch (args[0].toLowerCase()) {
                case "reload":
                    reloadConfig(source);
                    break;
                case "help":
                    sendHelpMessage(source);
                    break;
                default:
                    source.sendMessage(legacySerializer.deserialize("&cComando sconosciuto! Usa &b/everping help"));
                    break;
            }
        }

        @Override
        public List<String> suggest(Invocation invocation) {
            String[] args = invocation.arguments();
            List<String> suggestions = new ArrayList<>();

            if (args.length == 1) {
                suggestions.add("reload");
                suggestions.add("help");
            }

            return suggestions.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        private void sendHelpMessage(CommandSource source) {
            source.sendMessage(legacySerializer.deserialize("&cComandi Disponibili:\n" +
                    "&b/everping reload &7- &fRicarica le Configurazioni\n" +
                    "&b/everping help &7- &fMostra questo messaggio\n" +
                    "&b/ping &7- &fMostra la tua latenza"));
        }

        private void reloadConfig(CommandSource source) {
            loadConfig();
            source.sendMessage(legacySerializer.deserialize("&aConfigurazione ricaricata con successo!"));
        }
    }
}