package dev.ava.paperspoof;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.ServerPing;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Plugin(id = "paperspoof", name = "PaperSpoof", version = "1.0.0",
        description = "Spoofs server brand and version sent to clients")
public class PaperSpoof {

    private String brand = "Paper";
    private String versionName = "Paper 26.3";
    private static final MinecraftChannelIdentifier BRAND_CHANNEL =
            MinecraftChannelIdentifier.from("minecraft:brand");

    private final ProxyServer server;
    private final Path dataDir;

    @Inject
    public PaperSpoof(ProxyServer server, @DataDirectory Path dataDir) {
        this.server = server;
        this.dataDir = dataDir;
    }

    @Subscribe
    public void onInit(ProxyInitializeEvent event) {
        try {
            Files.createDirectories(dataDir);
            Path file = dataDir.resolve("config.properties");
            if (!Files.exists(file)) {
                try (InputStream in = getClass().getResourceAsStream("/config.properties")) {
                    Files.copy(in, file);
                }
            }
            Properties p = new Properties();
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                p.load(r);
            }
            versionName = p.getProperty("version-name", versionName).trim();
            brand = p.getProperty("brand", brand).trim();
        } catch (IOException e) {
            System.err.println("[PaperSpoof] could not load config, using defaults: " + e);
        }
    }

    @Subscribe
    public void onPing(ProxyPingEvent event) {
        ServerPing ping = event.getPing();
        int protocol = ping.getVersion().getProtocol();
        String name = versionName;
        if (name.isEmpty()) {
            ProtocolVersion pv = ProtocolVersion.getProtocolVersion(protocol);
            if (!pv.isSupported()) pv = ProtocolVersion.MAXIMUM_VERSION;
            name = brand + " " + pv.getMostRecentSupportedVersion();
        }
        event.setPing(ping.asBuilder()
                .version(new ServerPing.Version(protocol, name))
                .build());
    }

    @Subscribe
    public void onServerConnected(ServerPostConnectEvent event) {
        Player player = event.getPlayer();
        server.getScheduler().buildTask(this, () -> {
            if (player.isActive()) {
                player.sendPluginMessage(BRAND_CHANNEL, encodeString(brand));
            }
        }).delay(1, TimeUnit.SECONDS).schedule();
    }

    private static byte[] encodeString(String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int len = bytes.length;
        while ((len & ~0x7F) != 0) {
            out.write((len & 0x7F) | 0x80);
            len >>>= 7;
        }
        out.write(len);
        out.writeBytes(bytes);
        return out.toByteArray();
    }
}
