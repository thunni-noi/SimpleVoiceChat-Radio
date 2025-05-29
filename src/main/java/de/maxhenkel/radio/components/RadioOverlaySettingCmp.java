package de.maxhenkel.radio.components;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.nbt.NbtCompound;
import org.ladysnake.cca.api.v3.component.Component;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

// keep if user want overlay or not?
public class RadioOverlaySettingCmp implements Component, AutoSyncedComponent {

    boolean enabled_overlay = true;


    public boolean isRadioOverlayEnabled(){
        return this.enabled_overlay;
    }

    public void setRadioOVerlayEnabled(boolean enabled_overlay) {
        this.enabled_overlay = enabled_overlay;
    }

    public void toggleRadioOverlay() {
        setRadioOVerlayEnabled(!isRadioOverlayEnabled());
    }

    @Override
    public boolean isRequiredOnClient(){
        return false;
    }

    @Override
    public void readFromNbt(NbtCompound nbtCompound, RegistryWrapper.WrapperLookup wrapperLookup) {
        this.enabled_overlay = nbtCompound.getBoolean("radio_overlay_enabled").orElse(true);
    }

    @Override
    public void writeToNbt(NbtCompound nbtCompound, RegistryWrapper.WrapperLookup wrapperLookup) {
        nbtCompound.putBoolean("radio_overlay_enabled", this.enabled_overlay);
    }
}
