package de.maxhenkel.radio.components;

import net.minecraft.registry.RegistryWrapper;
import net.minecraft.nbt.NbtCompound;
import org.jetbrains.annotations.NotNull;

// keep if user want overlay or not?
public class RadioOverlaySettingCmp implements PlayerBooleanComponent {
    // default as true
    private boolean value = true;

    @Override
    public boolean getValue(){
        return this.value;
    }

    @Override
    public void setValue(boolean value) {
        this.value = value;
    }

    @Override
    public void toggle() {
        PlayerBooleanComponent.super.toggle();
    }


    @Override
    public void readFromNbt(NbtCompound nbtCompound, RegistryWrapper.WrapperLookup wrapperLookup) {
        this.value = nbtCompound.getBoolean("radio_overlay_enabled").orElse(true);
    }

    @Override
    public void writeToNbt(NbtCompound nbtCompound, RegistryWrapper.WrapperLookup wrapperLookup) {
        nbtCompound.putBoolean("radio_overlay_enabled", this.value);
    }
}
