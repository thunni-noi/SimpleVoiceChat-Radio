package de.maxhenkel.radio.components;

import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.entity.EntityComponentFactoryRegistry;
import org.ladysnake.cca.api.v3.entity.EntityComponentInitializer;
import org.ladysnake.cca.api.v3.component.ComponentRegistryV3;
import org.ladysnake.cca.api.v3.entity.RespawnCopyStrategy;
import net.minecraft.util.Identifier;


public class RadioSettingComponents implements EntityComponentInitializer {

    public static final ComponentKey<RadioOverlaySettingCmp> RADIO_OVERLAY = ComponentRegistryV3.INSTANCE.getOrCreate(Identifier.of("radio","overlay_settings"), RadioOverlaySettingCmp.class);

    @Override
    public void registerEntityComponentFactories(EntityComponentFactoryRegistry entityComponentFactoryRegistry) {
        //entityComponentFactoryRegistry.registerForPlayers();
        entityComponentFactoryRegistry.registerForPlayers(RADIO_OVERLAY, player -> new RadioOverlaySettingCmp(), RespawnCopyStrategy.ALWAYS_COPY);
    }
}
