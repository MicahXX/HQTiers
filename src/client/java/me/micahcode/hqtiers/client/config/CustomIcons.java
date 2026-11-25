package me.micahcode.hqtiers.client.config;

import me.micahcode.hqtiers.Hqtiers;
import me.micahcode.hqtiers.client.model.HqTiersLadder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.Blaze3D;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

final class CustomIcons {
    private static final String PACK = "HQTiers Custom Icons";
    private static final String ASSETS = "/assets/hqtiers/";

    static void openFolder() {
        var client = Minecraft.getInstance();
        try {
            Blaze3D.openPath(prepare(client));
        } catch (IOException error) {
            report(client, error);
        }
    }

    static void enable() {
        var client = Minecraft.getInstance();
        var packs = client.getResourcePackRepository();
        try {
            prepare(client);
            if (packs.getSelectedIds().contains("file/" + PACK)) return;
            packs.reload();
            if (!packs.addPack("file/" + PACK)) throw new IOException("Custom icon pack unavailable");
            client.options.updateResourcePacks(packs);
        } catch (IOException error) {
            report(client, error);
        }
    }

    private static Path prepare(Minecraft client) throws IOException {
        Path pack = client.getResourcePackDirectory().resolve(PACK);
        Path assets = pack.resolve("assets/hqtiers");
        Path icons = Files.createDirectories(assets.resolve("textures/font/custom"));
        Path font = Files.createDirectories(assets.resolve("font")).resolve("custom.json");
        for (var ladder : HqTiersLadder.values()) {
            String name = ladder.name().toLowerCase(Locale.ROOT) + ".png";
            Path target = icons.resolve(name);
            if (Files.notExists(target)) {
                try (var source = resource("textures/font/pvphq/" + name)) {
                    Files.copy(source, target);
                }
            }
        }
        if (Files.notExists(font)) {
            try (var source = resource("font/pvphq.json")) {
                Files.writeString(font, new String(source.readAllBytes(), StandardCharsets.UTF_8).replace("font/pvphq/", "font/custom/"));
            }
        }
        Path metadata = pack.resolve("pack.mcmeta");
        String format = "{\"pack\":{\"description\":\"HQTiers custom gamemode icons\",\"min_format\":75,\"max_format\":%s}}";
        if (Files.notExists(metadata) || Files.readString(metadata).equals(format.formatted("75"))) {
            Files.writeString(metadata, format.formatted("[97,1]"));
        }
        return icons;
    }

    private static InputStream resource(String name) throws IOException {
        var stream = CustomIcons.class.getResourceAsStream(ASSETS + name);
        if (stream == null) throw new IOException("Missing " + name);
        return stream;
    }

    private static void report(Minecraft client, IOException error) {
        Hqtiers.logger.warn("Could not prepare custom icons", error);
        client.gui.hud.getChat().addClientSystemMessage(Component.literal("HQTiers could not open the custom icon pack. Check the game log for details."));
    }
}
