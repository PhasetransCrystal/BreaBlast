package com.phasetranscrystal.blast.skill;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.phasetranscrystal.blast.Blast;
import com.phasetranscrystal.blast.Registries;
import com.phasetranscrystal.blast.player.KeyInput;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;
import org.apache.commons.lang3.function.ToBooleanBiFunction;
import org.apache.commons.lang3.function.TriConsumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Math;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class Skill<T extends Entity> {
    public static final Logger LOGGER = LogManager.getLogger("BreaBlast:Skill");
    public static final ResourceLocation NAME = Blast.location("skill");

    public final int inactiveEnergy, maxCharge, initialEnergy, initialCharge, activeEnergy;

    public final Optional<String> initBehavior;
    public final ImmutableMap<String, Behavior<T>> behaviors;

    public final Consumer<SkillData<T>> onStart;
    public final Consumer<SkillData<T>> onEnd;
    public final ToBooleanBiFunction<SkillData<T>, Optional<String>> judge;
    public final BiConsumer<SkillData<T>, Optional<String>> stateChange;
    public final KeyInput.Consumer<T> keyChange;

    public final IntList keys;
    public final ImmutableMap<Class<? extends Event>, BiConsumer<? extends Event, SkillData<T>>> listeners;
//    public final ImmutableSet<Flag> flags;

    public final Class<T> clazz;

    private Skill(Builder<T> builder, Class<T> clazz) {
        this.inactiveEnergy = Math.max(builder.inactiveEnergy, 0);
        this.maxCharge = Math.max(builder.maxCharge, 1);
        this.initialEnergy = Math.clamp(0, inactiveEnergy, builder.initialEnergy);
        this.initialCharge = Math.clamp(0, maxCharge, builder.initialCharge);
        this.activeEnergy = Math.max(builder.activeEnergy, 0);

        this.initBehavior = Optional.ofNullable(builder.initBehavior);

        if (builder.initBehavior != null && !builder.behaviors.containsKey(builder.initBehavior)) {
            LOGGER.error("Init behavior(name={}) not exist in behaviors({}). Changed to null.", builder.initBehavior, Arrays.toString(builder.behaviors.keySet().toArray()));
            builder.initBehavior = null;
        }

        ImmutableMap.Builder<String, Behavior<T>> behavBuilder = ImmutableMap.builder();
        builder.behaviors.forEach((name, b) -> behavBuilder.put(name, b.build()));
        this.behaviors = behavBuilder.build();


        this.onStart = builder.onStart;
        this.onEnd = builder.onEnd;
        this.judge = builder.judge;
        this.stateChange = builder.behaviorChange;
        this.keyChange = builder.keyChange;

        this.keys = IntList.of(builder.keys.toIntArray());
        this.listeners = ImmutableMap.copyOf(builder.listeners);
//        this.flags = ImmutableSet.copyOf(builder.flags);

        this.clazz = clazz;
    }

    public static class Builder<T extends Entity> {
        public final Consumer<SkillData<T>> NO_ACTION = data -> {
        };

        public int inactiveEnergy, maxCharge, initialEnergy, initialCharge, activeEnergy;

        @Nullable
        public String initBehavior = "inactive";

        public HashMap<String, Behavior.Builder<T>> behaviors = new HashMap<>();

        public Consumer<SkillData<T>> onStart = NO_ACTION;
        public Consumer<SkillData<T>> onEnd = NO_ACTION;
        public ToBooleanBiFunction<SkillData<T>, Optional<String>> judge = (data, behavior) -> true;
        public BiConsumer<SkillData<T>, Optional<String>> behaviorChange = (data, behaviorRecord) -> {
        };
        public KeyInput.Consumer<T> keyChange = (data, packet) -> {
        };
        public IntList keys = new IntArrayList();
        public HashMap<Class<? extends Event>, BiConsumer<? extends Event, SkillData<T>>> listeners = new HashMap<>();

//        public HashSet<Flag> flags = new HashSet<>();

        private Builder(int inactiveEnergy, int maxChargeTimes) {
            this.inactiveEnergy = inactiveEnergy;
            this.activeEnergy = inactiveEnergy;
            this.maxCharge = maxChargeTimes;
        }

        private Builder(int inactiveEnergy) {
            this.inactiveEnergy = inactiveEnergy;
            this.activeEnergy = inactiveEnergy;
            this.maxCharge = 1;
        }

        private Builder(int inactiveEnergy, int maxCharge, int initialEnergy, int initialCharge, int activeEnergy) {
            this.inactiveEnergy = inactiveEnergy;
            this.maxCharge = maxCharge;
            this.activeEnergy = activeEnergy;
            this.initialEnergy = initialEnergy;
            this.initialCharge = initialCharge;
        }

        public static <T extends Entity> Builder<T> of(int energyCost, int maxChargeTimes) {
            return new Builder<>(energyCost, maxChargeTimes);
        }

        public static <T extends Entity> Builder<T> of(int energyCost) {
            return new Builder<>(energyCost);
        }

        public static <T extends Entity> Builder<T> of(Skill<T> skill) {
            Builder<T> builder = of(skill.inactiveEnergy, skill.maxCharge, skill.initialEnergy, skill.initialCharge, skill.activeEnergy);
            return builder.copyFrom(skill);
        }

        /**
         * @param energyCost    能量上限
         * @param maxCharge     充能层数上限
         * @param initialEnergy 初始能量
         * @param initialCharge 初始充能层数
         * @param activeEnergy  技能开启后能量上限
         * @param <T>           技能释放主体
         */
        public static <T extends Entity> Builder<T> of(int energyCost, int maxCharge, int initialEnergy, int initialCharge, int activeEnergy) {
            return new Builder<>(energyCost, maxCharge, initialEnergy, initialCharge, activeEnergy);
        }

        public Builder<T> copyFrom(Skill<T> skill) {

            skill.behaviors.forEach((name, builder) -> this.behaviors.put(name, Behavior.Builder.create(builder)));

            this.onStart = skill.onStart;
            this.onEnd = skill.onEnd;
            this.judge = skill.judge;
            this.behaviorChange = skill.stateChange;
            this.listeners.putAll(skill.listeners);
            return this;
        }

        /**
         * 当技能被设置为*启用*状态时触发
         */
        public Builder<T> start(Consumer<SkillData<T>> consumer) {
            onStart = consumer;
            return this;
        }

        /**
         * 判断当前状态是否可切换为active阶段？？
         */
        public Builder<T> inactive(Consumer<Behavior.Builder<T>> inactive) {
            inactive.accept(this.behaviors.computeIfAbsent("inactive", key -> Behavior.Builder.create()));
            return this;
        }

        public Builder<T> active(Consumer<Behavior.Builder<T>> active) {
            active.accept(this.behaviors.computeIfAbsent("active", key -> Behavior.Builder.<T>create().onActiveEnergyEmpty(data -> data.switchTo("inactive"))));
            return this;
        }

        public Builder<T> addBehavior(Consumer<Behavior.Builder<T>> inactive, String name) {
            Behavior.Builder<T> builder = Behavior.Builder.create();
            inactive.accept(builder);
            behaviors.put(name, builder);
            return this;
        }

        public Builder<T> removeBehavior(String name) {
            behaviors.remove(name);
            return this;
        }

        public Builder<T> removeBehavior() {
            behaviors.clear();
            return this;
        }

        /**
         * 为技能增加事件监听器<br>
         * 技能处于*启用*状态时可被监听触发
         *
         * @param clazz    需要监听的实体事件
         * @param consumer 事件行为
         * @param <E>      被监听的事件
         */
        public <E extends Event> Builder<T> onEvent(Class<E> clazz, BiConsumer<E, SkillData<T>> consumer) {
            listeners.put(clazz, consumer);
            return this;
        }


        public Builder<T> judge(ToBooleanBiFunction<SkillData<T>, Optional<String>> judge) {
            this.judge = judge;
            return this;
        }

        public Builder<T> onBehaviorChange(BiConsumer<SkillData<T>, Optional<String>> consumer) {
            this.behaviorChange = consumer;
            return this;
        }

        public Builder<T> onKeyInput(KeyInput.Consumer<T> consumer, int... keyListeners) {
            this.keys = new IntArrayList(keyListeners);
            this.keyChange = consumer;
            return this;
        }

        public Builder<T> apply(Consumer<Builder<T>> consumer) {
            consumer.accept(this);
            return this;
        }

//        public Builder<T> flag(Flag flag, boolean execute) {
//            return flag(flag, execute, null, null);
//        }
//
//        public Builder<T> flag(Flag flag, boolean execute, String nameRedirect1, String nameRedirect2) {
//            flags.add(flag);
//            if (execute) {
//                flag.consumer.accept(this, nameRedirect1, nameRedirect2);
//            }
//            return this;
//        }

        public Skill<T> end(Class<T> targetType) {
            return new Skill<>(this, targetType);
        }

        public Skill<T> end(Consumer<SkillData<T>> consumer, Class<T> targetType) {
            onEnd = consumer;
            return end(targetType);
        }
    }

    @Deprecated(forRemoval = true)
    public enum Flag implements StringRepresentable {
        AUTO_START("auto_start", (builder, redirectName1, redirectName2) -> builder.behaviors.get(redirectName1 == null ? "inactive" : redirectName1).onChargeReady(data -> data.switchTo(redirectName2 == null ? "active" : redirectName2))),
        AUTO_FINISH("auto_finish", (builder, redirectName1, redirectName2) -> builder.behaviors.get(Objects.requireNonNullElse(redirectName1, "active")).onActiveEnergyEmpty(data -> data.switchTo(Objects.requireNonNullElse(redirectName2, "inactive")))),

        INSTANT_COMPLETE("instant_complete", (builder, redirectName1, redirectName2) -> builder.activeEnergy = 0),
        PASSIVITY("passivity", (builder, redirectName1, redirectName2) -> builder.inactiveEnergy = 0),

        INTERRUPTIBLE("interruptible"),

        TIME_ADD_INACTIVE_ENERGY("time_add_inactive_energy", (builder, redirectName1, redirectName2) -> builder.behaviors.get(redirectName1 == null ? "inactive" : redirectName1).onTick((event, data) -> data.addEnergy(1))),
        CLEAN_ACTIVE_ENERGY("clean_active_energy", (builder, redirectName1, redirectName2) -> builder.behaviors.get(Objects.requireNonNullElse(redirectName1, "active")).endWith(data -> data.setActiveEnergy(0))),
        MARK_SKILL_TIME("mark_skill_time", (builder, redirectName1, redirectName2) -> builder.onBehaviorChange((data, toName) -> {
            if (toName.equals(Objects.requireNonNullElse(redirectName1, "active"))) data.consumerActiveStart();
        }));

        public final String name;
        public final TriConsumer<Builder<? extends Entity>, String, String> consumer;

        Flag(String name, TriConsumer<Builder<? extends Entity>, String, String> consumer) {
            this.name = name;
            this.consumer = consumer;
        }

        Flag(String name) {
            this(name, (builder, redirectName1, redirectName2) -> {
            });
        }

        @Override
        public @NotNull String getSerializedName() {
            return name;
        }
    }

    public ResourceKey<Skill<?>> getResourceKey() {
        return Registries.SKILL.getResourceKey(this).get();
    }

    @Override
    public String toString() {
        return "BreaBlast-Skill{key=" + getResourceKey() + ", behaviors=" + Arrays.toString(behaviors.keySet().toArray()) + "}";
    }
}
