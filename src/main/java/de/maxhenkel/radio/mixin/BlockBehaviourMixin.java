package de.maxhenkel.radio.mixin;

import com.mojang.authlib.GameProfile;
import de.maxhenkel.radio.radio.RadioData;
import de.maxhenkel.radio.radio.RadioManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.ActionResult;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.world.World;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SkullBlockEntity;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.util.hit.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractBlock.class)
public class BlockBehaviourMixin {

    @Inject(method = "onUse", at = @At("HEAD"), cancellable = true)
    public void use(BlockState blockState, World level, BlockPos blockPos, PlayerEntity player, BlockHitResult blockHitResult, CallbackInfoReturnable<ActionResult> cir) {
        if (level.isClient())
            return;

        boolean isNotPlayerHeadBlock = !blockState.getBlock().equals(Blocks.PLAYER_HEAD) &&
                                    !blockState.getBlock().equals(Blocks.PLAYER_WALL_HEAD);
        if (isNotPlayerHeadBlock) return;


        BlockEntity blockEntity = level.getBlockEntity(blockPos);

        if (!(blockEntity instanceof SkullBlockEntity skullBlockEntity))
            return;

        ProfileComponent resolvable = skullBlockEntity.getOwner();

        if(resolvable == null) return;

        GameProfile profile = resolvable.gameProfile();
        RadioData radioData = RadioData.fromGameProfile(profile);
        if (radioData == null) {
            return;
        }

        radioData.setOn(!radioData.isOn());
        radioData.updateProfile(profile);
        skullBlockEntity.markDirty();
        RadioManager.getInstance().updateHeadOnState(radioData.getId(), radioData.isOn());

        level.playSound(null, blockPos, SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.BLOCKS, 1F, 1F);

        cir.setReturnValue(ActionResult.SUCCESS);
    }

}
