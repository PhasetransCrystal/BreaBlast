package com.phasetranscrystal.blast.registry;

import com.phasetranscrystal.blast.Blast;
import com.phasetranscrystal.blast.Registries;
import com.phasetranscrystal.blast.player.SkillGroup;
import com.phasetranscrystal.blast.skill.Skill;
import com.phasetranscrystal.blast.skill.SkillData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = Blast.MODID)
public class SkillRegistry {

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDeath(LivingDeathEvent event) {
        event.getEntity().getExistingData(Blast.SKILL_ATTACHMENT).map(SkillGroup::getCurrentSkillData).ifPresent(SkillData::requestDisable);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void init(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            serverPlayer.getData(Blast.SKILL_ATTACHMENT).bindEntity(serverPlayer);
            serverPlayer.getData(Blast.SKILL_ATTACHMENT).getCurrentSkillData().requestEnable();
        }
    }

    public static final DeferredRegister<Skill<?>> SKILL = DeferredRegister.create(Registries.SKILL, Blast.MODID);

    public static final DeferredHolder<Skill<?>, Skill<? extends Entity>> EMPTY = SKILL.register("empty", () -> Skill.EMPTY);

//    public static final DeferredHolder<Skill<?>, Skill<Player>> TEST_SKILL = SKILL.register("test",
//            () -> Skill.Builder.<Player>of(30, 4)
//                    .start(data -> data.getEntity().displayClientMessage(Component.literal("TestSkillInit"), false))
////                    .flag(Skill.Flag.INSTANT_COMPLETE, true)
//                    .onEvent(EntityTickEvent.Post.class, (event, data) -> {
//                        Player player = data.getEntity();
//                        if (player.level().getGameTime() % 100 == 0 && player.getHealth() < player.getMaxHealth()) {
//                            player.displayClientMessage(Component.literal("You are healed!"), false);
//                            player.addEffect(new MobEffectInstance(MobEffects.HEAL, 100, 2));
//                        }
//                    })
//                    .inactive(builder -> builder
//                            //按键监听测试
//                            .onHurt((event, data) -> data.addEnergy(-1))
//                            .onAttack((event, data) -> data.addEnergy(2))
//                            .onKillTarget((event, data) -> data.addEnergy(5))
//                            .inactiveEnergyChanged((data, i) -> {
//                                data.getEntity().displayClientMessage(Component.literal("Energy " + (i >= 0 ? "§a+" : "§c-") + i), true);
//                            })
//                            .chargeChanged((data, i) -> {
//                                data.getEntity().displayClientMessage(Component.literal("Charge " + (i >= 0 ? "§a+" : "§c-") + i), true);
//                                ResourceLocation location = ResourceLocation.fromNamespaceAndPath(Blast.MODID, "skill_test");
//                                data.addAutoCleanAttribute(new AttributeModifier(location, 0.5 * data.getCharge(), AttributeModifier.Operation.ADD_VALUE), Attributes.MOVEMENT_SPEED);
//                            })
//                            .onChargeReady(data -> data.getEntity().displayClientMessage(Component.literal("ReachReady!"), false))
//                            .onChargeFull(data -> data.getEntity().displayClientMessage(Component.literal("ReachStop!"), false))
//                            .endWith(data -> {
//                                data.putCacheData("charge_consume", data.getCharge() + 1 + "", true, true);
//                                data.setCharge(0);
//                            })
//                    )
//                    .judge((data, name) -> !"active".equals(name.orElse("")) || (data.getEntity().level().isNight() && data.getCharge() >= 1))
//                    .active(builder -> builder
//                            .startWith(data -> {
//                                Vec3 pos = data.getEntity().position();
//                                ((ServerLevel) data.getEntity().level()).sendParticles(ParticleTypes.EXPLOSION, pos.x, pos.y, pos.z, 4, 0.5, 0.5, 0.5, 0.5);
//                            })
//                            .onTick((event, data) -> {
//                                data.getEntity().displayClientMessage(Component.literal("activeTick"), true);
//                                data.modifyActiveEnergy(-1);
//                            })
//                            .endWith(data -> {
//                                data.getEntity().jumpFromGround();
//                                data.getEntity().addDeltaMovement(new Vec3(0, 0.1, 0));
//                                data.getEntity().addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200 * data.getCacheDataAsInt("charge_consume", 0, true), 2));
//                            })
//                    )
//                    .onBehaviorChange((data, behavior) -> {
//                        if (data.getActiveTimes() == 5) data.requestDisable();
//                        else {
//                            data.getEntity().displayClientMessage(Component.literal("StateChanged: to " + behavior.map(s -> "\"" + behavior + "\"").orElse("null") + " time " + data.getActiveTimes()), false);
//                            if ("active".equals(behavior.orElse(""))) data.consumeCharge();
//                        }
//                    })
//                    .end(data -> data.getEntity().displayClientMessage(Component.literal("skill disabled"), false), Player.class)
//    );
//
//    public static final DeferredHolder<Skill<?>, Skill<Player>> OLD_MA = SKILL.register("old_ma", () -> Skill.Builder
//            .<Player>of(50, 3, 0, 0, 50)
//            .start(data -> data.getEntity().displayClientMessage(Component.literal("OldMaInit"), false))
//            .judge((data, name) -> data.getCharge() == 3)
//            .addBehavior(builder -> builder
//                            .onKeyInput((data, pack) -> data.getEntity().sendSystemMessage(Component.literal("按键拦截成功")), GLFW.GLFW_KEY_H)
//                            .endWith(data -> data.getEntity().displayClientMessage(Component.literal("OldMaEnd"), false)),
//                    "key test")
//            .build(Player.class));


    public static class Start extends Item {
        public Start() {
            super(new Properties());
        }

//        @Override
//        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
//            if (level instanceof ServerLevel server) {
//                if (usedHand == InteractionHand.OFF_HAND && !player.getData(Blast.SKILL_ATTACHMENT).isEnabled()) {
//                    player.getData(SKILL_ATTACHMENT).requestEnable();
//                } else {
//                    player.getData(SKILL_ATTACHMENT).switchToIfNot("active");
//                }
//            }
//            return InteractionResultHolder.success(player.getItemInHand(usedHand));
//        }
    }
}
