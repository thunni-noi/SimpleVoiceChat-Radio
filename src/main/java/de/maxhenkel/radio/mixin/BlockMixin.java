package de.maxhenkel.radio.mixin;

import com.mojang.authlib.GameProfile;
import de.maxhenkel.radio.radio.RadioData;
import de.maxhenkel.radio.radio.RadioManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.world.World;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.PlayerSkullBlock;
import net.minecraft.block.WallPlayerSkullBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SkullBlockEntity;
import net.minecraft.block.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Block.class)
public class BlockMixin {

    @Inject(method = "afterBreak", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/Block;dropStacks(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/entity/BlockEntity;Lnet/minecraft/entity/Entity;Lnet/minecraft/item/ItemStack;)V"), cancellable = true)
    public void playerDestroy(World world, PlayerEntity player, BlockPos pos, BlockState state, BlockEntity blockEntity, ItemStack tool, CallbackInfo ci) {
        if (world.isClient())
            return;

        boolean isNotPlayerHeadBlock = !state.getBlock().equals(Blocks.PLAYER_HEAD) &&
                                       !state.getBlock().equals(Blocks.PLAYER_WALL_HEAD);
        if (isNotPlayerHeadBlock) return;

        if (!(blockEntity instanceof SkullBlockEntity skullBlockEntity))
            return;

        ProfileComponent profile = skullBlockEntity.getOwner();;
        if (profile == null) return;

        GameProfile ownerProfile = profile.gameProfile();

        RadioData radioData = RadioData.fromGameProfile(ownerProfile);
        if (radioData != null) {
            RadioManager.getInstance().onRemoveHead(radioData.getId());
            ItemStack speakerItem = radioData.toItemWithNoId();
            Block.dropStack(world, pos, speakerItem);
            ci.cancel();
        }
    }

    @Inject(method = "onBreak", at = @At(value = "HEAD"))
    public void destroy(World world, BlockPos pos, BlockState state, PlayerEntity player, CallbackInfoReturnable<BlockState> cir) {
        if (world.isClient())
            return;

        boolean isNotPlayerHeadBlock = !state.getBlock().equals(Blocks.PLAYER_HEAD) &&
                                       !state.getBlock().equals(Blocks.PLAYER_WALL_HEAD);
        if (isNotPlayerHeadBlock) return;

        BlockEntity blockEntity = world.getBlockEntity(pos);

        if (!(blockEntity instanceof SkullBlockEntity skullBlockEntity))
            return;

        ProfileComponent resolvableProfile = skullBlockEntity.getOwner();
        if (resolvableProfile == null) return;

        GameProfile ownerProfile = resolvableProfile.gameProfile();
        RadioData radioData = RadioData.fromGameProfile(ownerProfile);

        if (radioData != null)
            RadioManager.getInstance().onRemoveHead(radioData.getId());
    }

    // TODO Stop radio when block is broken by explosion or non-player

}
