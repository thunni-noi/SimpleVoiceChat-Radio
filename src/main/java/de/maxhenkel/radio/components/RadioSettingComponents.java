package de.maxhenkel.radio.components;

import net.minecraft.util.Identifier;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.entity.EntityComponentFactoryRegistry;
import org.ladysnake.cca.api.v3.entity.EntityComponentInitializer;
import org.ladysnake.cca.api.v3.component.ComponentRegistryV3;


public class RadioSettingComponents implements EntityComponentInitializer {
    public static final ComponentKey<RadioOverlaySettingCmp> RADIO_OVERLAY_SETTING =
            ComponentRegistryV3.INSTANCE.getOrCreate(Identifier.of("radio/overlay"), RadioOverlaySettingCmp.class);

    @Override
    public void registerEntityComponentFactories(EntityComponentFactoryRegistry entityComponentFactoryRegistry) {
        entityComponentFactoryRegistry.registerForPlayers();
    }
}
