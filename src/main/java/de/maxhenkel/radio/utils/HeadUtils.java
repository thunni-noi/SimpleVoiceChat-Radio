package de.maxhenkel.radio.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.util.UUIDTypeAdapter;
import de.maxhenkel.radio.Radio;
import net.minecraft.text.Style;
import net.minecraft.util.Formatting;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtString;
import net.minecraft.text.Text;
import net.minecraft.text.MutableText;
import net.minecraft.util.Unit;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.PlayerHeadItem;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.block.entity.BlockEntityType;

import java.nio.charset.StandardCharsets;
import java.util.*;

public class HeadUtils {

    public static final String NBT_SOUND_RANGE = "sound_radius";

    public static ItemStack createHead(String itemName, List<Text> loreComponents, GameProfile gameProfile) {
        ItemStack stack = new ItemStack(Items.PLAYER_HEAD);

        MutableText nameText = Text.literal(itemName).setStyle(
                Style.EMPTY.withItalic(false).withColor(Formatting.WHITE)
        );

        LoreComponent lore = new LoreComponent(loreComponents);
        ProfileComponent resolvableProfile = new ProfileComponent(gameProfile);

        stack.set(DataComponentTypes.ITEM_NAME, nameText);
        stack.set(DataComponentTypes.LORE, lore);
        stack.set(DataComponentTypes.TOOLTIP_DISPLAY, new TooltipDisplayComponent(true, new LinkedHashSet<>()));
        stack.set(DataComponentTypes.PROFILE, resolvableProfile);

        return stack;
    }

    private static final Gson gson = new GsonBuilder().registerTypeAdapter(UUID.class, new UUIDTypeAdapter()).create();

    public static GameProfile getGameProfile(UUID uuid, String name, String skinUrl) {
        GameProfile gameProfile = new GameProfile(uuid, name);
        PropertyMap properties = gameProfile.getProperties();

        List<Property> textures = new ArrayList<>();
        Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> textureMap = new HashMap<>();
        textureMap.put(MinecraftProfileTexture.Type.SKIN, new MinecraftProfileTexture(skinUrl, null));

        String json = gson.toJson(new MinecraftTexturesPayload(textureMap));
        String base64Payload = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));

        textures.add(new Property("textures", base64Payload));
        properties.putAll("textures", textures);

        return gameProfile;
    }

    private static class MinecraftTexturesPayload {

        private final Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> textures;

        public MinecraftTexturesPayload(Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> textures) {
            this.textures = textures;
        }

        public Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> getTextures() {
            return textures;
        }
    }

}
