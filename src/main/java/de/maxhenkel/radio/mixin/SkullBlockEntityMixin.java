package de.maxhenkel.radio.mixin;

import de.maxhenkel.radio.radio.RadioManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.world.World;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.PlayerSkullBlock;
import net.minecraft.block.WallPlayerSkullBlock;
import net.minecraft.block.entity.SkullBlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkullBlockEntity.class)
public class SkullBlockEntityMixin extends BlockEntity {

    public SkullBlockEntityMixin(BlockEntityType<?> blockEntityType, BlockPos blockPos, BlockState blockState) {
        super(blockEntityType, blockPos, blockState);
    }

    @Inject(method = "setOwner", at = @At("RETURN"))
    public void setOwner(ProfileComponent resolvableProfile, CallbackInfo ci) {
        if (world != null && !world.isClient) {
            RadioManager.getInstance().onLoadHead((SkullBlockEntity) (Object) this);
        }
    }

    @Inject(method = "readNbt", at = @At("RETURN"))
    public void load(NbtCompound nbt, RegistryWrapper.WrapperLookup registries, CallbackInfo ci) {
        if (world != null && !world.isClient) {
            RadioManager.getInstance().onLoadHead((SkullBlockEntity) (Object) this);
        }
    }

    @Override
    public void setWorld(World newWorld) {
        World oldLevel = world;
        super.setWorld(newWorld);
        if (oldLevel == null && newWorld != null && !newWorld.isClient) {
            RadioManager.getInstance().onLoadHead((SkullBlockEntity) (Object) this);
        }
    }
}
