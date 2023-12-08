package io.th0rgal.oraxen.font;


import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.th0rgal.oraxen.OraxenPlugin;
import io.th0rgal.oraxen.config.Settings;
import io.th0rgal.oraxen.utils.AdventureUtils;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import team.unnamed.creative.font.FontProvider;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


public class Glyph {

    public static final Character WHITESPACE_GLYPH = '\ue000';

    private boolean fileChanged = false;

    private final String name;
    private final boolean isEmoji;
    private final boolean tabcomplete;
    private final Character character;
    private Key texture;
    private final int ascent;
    private final int height;
    private final String permission;
    private final String[] placeholders;
    private final BitMapEntry bitmapEntry;
    private final Key font;

    public Glyph(final String glyphName, final ConfigurationSection glyphSection, char newChars) {
        name = glyphName;
        isEmoji = glyphSection.getBoolean("is_emoji", false);

        final ConfigurationSection chatSection = glyphSection.getConfigurationSection("chat");
        placeholders = chatSection != null ? chatSection.getStringList("placeholders").toArray(new String[0]) : new String[0];
        permission = chatSection != null ? chatSection.getString("permission", "") : "";
        tabcomplete = chatSection != null && chatSection.getBoolean("tabcomplete", false);

        if (glyphSection.contains("code")) {
            if (glyphSection.isInt("code")) glyphSection.set("char", (char) glyphSection.getInt("code"));
            glyphSection.set("code", null);
            fileChanged = true;
        }

        if (!glyphSection.contains("char") && !Settings.DISABLE_AUTOMATIC_GLYPH_CODE.toBool()) {
            glyphSection.set("char", newChars);
            fileChanged = true;
        }

        character = glyphSection.get("char") != null ? glyphSection.getString("char", "").charAt(0) : null;


        ConfigurationSection bitmapSection = glyphSection.getConfigurationSection("bitmap");
        bitmapEntry = bitmapSection != null ? new BitMapEntry(bitmapSection.getString("id"), bitmapSection.getInt("row"), bitmapSection.getInt("column")) : null;
        ascent = bitmap() != null ? bitmap().ascent() : glyphSection.getInt("ascent", 8);
        height = bitmap() != null ? bitmap().height() : glyphSection.getInt("height", 8);
        texture = bitmap() != null ? bitmap().texture() : Key.key(glyphSection.getString("texture", "required/exit_icon").replaceAll("^(?!.*\\.png$)", "") + ".png");
        font = bitmap() != null ? bitmap().font() : Key.key(glyphSection.getString("font", "minecraft:default"));
    }

    public record BitMapEntry(String id, int row, int column) {
    }

    public BitMapEntry getBitmapEntry() {
        return bitmapEntry;
    }

    public String getBitmapId() {
        return bitmapEntry != null ? bitmapEntry.id : null;
    }

    public boolean hasBitmap() {
        return getBitmapId() != null;
    }

    public boolean isBitMap() {
        return FontManager.getGlyphBitMap(getBitmapId()) != null;
    }

    public FontManager.GlyphBitMap bitmap() {
        return FontManager.getGlyphBitMap(getBitmapId());
    }

    public boolean isFileChanged() {
        return fileChanged;
    }

    public String name() {
        return name;
    }

    public String character() {
        return character != null ? character.toString() : "";
    }

    public Key texture() {
        return texture;
    }

    public void texture(Key texture) {
        this.texture = texture;
    }

    public int ascent() {
        return ascent;
    }

    public int height() {
        return height;
    }

    public String permission() {
        return permission;
    }

    public String[] placeholders() {
        return placeholders;
    }

    public boolean isEmoji() {
        return isEmoji;
    }

    public boolean hasTabCompletion() {
        return tabcomplete;
    }

    public JsonObject toJson() {
        final JsonObject output = new JsonObject();
        final JsonArray chars = new JsonArray();
        chars.add(character());
        output.add("chars", chars);
        output.addProperty("file", texture.asString());
        output.addProperty("ascent", ascent);
        output.addProperty("height", height);
        output.addProperty("type", "bitmap");
        return output;
    }

    public FontProvider fontProvider() {
        return FontProvider.bitMap()
                .file(texture)
                .ascent(ascent)
                .height(height)
                .characters(List.of(character.toString()))
                .build();
    }

    public boolean hasPermission(Player player) {
        return player == null || permission.isEmpty() || player.hasPermission(permission);
    }

    private final Set<String> materialNames = Arrays.stream(Material.values()).map(Material::name).collect(Collectors.toSet());

    /*public void verifyGlyph(List<Glyph> glyphs) {
        // Return on first run as files aren't generated yet
        Path packFolder = Path.of(OraxenPlugin.get().getDataFolder().getAbsolutePath()).resolve("pack");
        if (!packFolder.toFile().exists()) return;

        String texturePath = texture.contains(":") ? "assets/" + StringUtils.substringBefore(texture, ":") + "/textures/" : "textures/";
        texturePath = texturePath + (texture.contains(":") ? texture.split(":")[1] : texture);
        File textureFile;
        // If using minecraft as a namespace, make sure it is in assets or root pack-dir
        if (!StringUtils.substringBefore(texture, ":").equals("minecraft") || packFolder.resolve(texturePath).toFile().exists()) {
            textureFile = packFolder.resolve(texturePath).toFile();
            if (!textureFile.exists())
                textureFile = packFolder.resolve("assets/minecraft/" + texturePath).toFile();
        } else textureFile = packFolder.resolve(texturePath.replace("assets/minecraft/", "")).toFile();

        Map<Glyph, Boolean> sameCharMap = glyphs.stream().filter(g -> !g.name.equals(name) && !g.character().isBlank() && g.character.equals(character)).collect(Collectors.toMap(g -> g, g -> true));
        // Check if the texture is a vanilla item texture and therefore not in oraxen, but the vanilla pack
        boolean isMinecraftNamespace = !texture.contains(":") || texture.split(":")[0].equals("minecraft");
        String textureName = textureFile.getName().split("\\.")[0].toUpperCase();
        boolean isVanillaTexture = isMinecraftNamespace && materialNames.stream().anyMatch(textureName::contains);
        boolean hasUpperCase = false;
        BufferedImage image = null;
        for (char c : texturePath.toCharArray()) if (Character.isUpperCase(c)) hasUpperCase = true;
        try {
            image = ImageIO.read(textureFile);
        } catch (IOException ignored) {
        }

        if (height < ascent) {
            this.texture("required/exit_icon");
            Logs.logError("The ascent is bigger than the height for " + name + ". This will break all your glyphs.");
            Logs.logWarning("It has been temporarily set to a placeholder image. You should edit this in the glyph config.");
        } else if (!isVanillaTexture && (!textureFile.exists() || image == null)) {
            this.texture("required/exit_icon");
            Logs.logError("The texture specified for " + name + " does not exist. This will break all your glyphs.");
            Logs.logWarning("It has been temporarily set to a placeholder image. You should edit this in the glyph config.");
        } else if (hasUpperCase) {
            this.texture("required/exit_icon");
            Logs.logError("The filename specified for " + name + " contains capital letters.");
            Logs.logWarning("This will break all your glyphs. It has been temporarily set to a placeholder image.");
            Logs.logWarning("You should edit this in the glyph config and your textures filename.");
        } else if (texturePath.contains(" ")) {
            this.texture("required/exit_icon");
            Logs.logError("The filename specified for " + name + " contains spaces.");
            Logs.logWarning("This will break all your glyphs. It has been temporarily set to a placeholder image.");
            Logs.logWarning("You should replace spaces with _ in your filename and glyph config.");
        } else if (texturePath.contains("//")) {
            this.texture("required/exit_icon");
            Logs.logError("The filename specified for " + name + " contains double slashes.");
            Logs.logWarning("This will break all your glyphs. It has been temporarily set to a placeholder image.");
            Logs.logWarning("You should make sure that the texture-path you have specified is correct.");
        } else if (!isVanillaTexture && !isBitMap() && (image.getHeight() > 256 || image.getWidth() > 256)) {
            this.texture("required/exit_icon");
            Logs.logError("The texture specified for " + name + " is larger than the supported size.");
            Logs.logWarning("The maximum image size is 256x256. Anything bigger will break all your glyphs.");
            Logs.logWarning("It has been temporarily set to a placeholder-image. You should edit this in the glyph config.");
        } else if (Settings.DISABLE_AUTOMATIC_GLYPH_CODE.toBool() && !sameCharMap.isEmpty()) {
            this.texture("required/exit_icon");
            Logs.logError(name + " code is the same as " + sameCharMap.keySet().stream().map(Glyph::name).collect(Collectors.joining(", ")) + ".");
            Logs.logWarning("This will break all your glyphs. It has been temporarily set to a placeholder image.");
            Logs.logWarning("You should edit the code of all these glyphs to be unique.");
        }
    }*/

    /**
     * Parses all glyph-tags and raw unicodes in a message to their formatted variant
     * Relies on NMSHandler#verifyFor to escape all glyphs that the player doesn't have permission for
     *
     * @param element The JSON Object to parse
     * @return The parsed JSON Object
     */
    public static JsonObject parsePlaceholders(JsonElement element) {
        Component component = AdventureUtils.GSON_SERIALIZER.deserializeFromTree(element);
        for (Glyph glyph : OraxenPlugin.get().fontManager().glyphs()) {
            Component glyphComponent = Component.text().content(glyph.character()).color(NamedTextColor.WHITE).build();
            // Format all non-escaped glyph-tags and raw unicodes
            component = component.replaceText(TextReplacementConfig.builder()
                    .match(Pattern.compile("(?<!\\\\)(" + glyph.character() + "|" + glyph.getGlyphTag() + ")"))
                    .replacement(glyphComponent).build());

            for (String placeholder : glyph.placeholders) {
                component = component.replaceText(TextReplacementConfig.builder()
                        .match(Pattern.compile("(?<!\\\\)" + placeholder))
                        .replacement(glyph.character()).build());

                // Replace all escaped glyphs with their non-formatted variant
                component = component.replaceText(TextReplacementConfig.builder()
                        .match(Pattern.compile("\\\\(" + placeholder + "|" + glyph.character() + ")"))
                        .replacement(glyphComponent)
                        .build());
            }

            // Replace all escaped glyph-tags with their non-formatted variant
            component = component.replaceText(TextReplacementConfig.builder()
                    .match(Pattern.compile("\\\\" + glyph.getGlyphTag()))
                    .replacement(glyph.getGlyphTag())
                    .build());
        }

        component = AdventureUtils.MINI_MESSAGE.deserialize(AdventureUtils.MINI_MESSAGE.serialize(component));
        return AdventureUtils.GSON_SERIALIZER.serializeToTree(component).getAsJsonObject();
    }

    /**
     * Useful to easily get the MiniMessage-tag for a glyph
     */
    public String getGlyphTag() {
        return '<' + "glyph;" + name + '>';
    }

    public String getShortGlyphTag() {
        return "<g:" + name + '>';
    }

    public Key font() {
        return font;
    }
}
