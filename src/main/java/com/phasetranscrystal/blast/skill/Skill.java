package com.phasetranscrystal.blast.skill;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.phasetranscrystal.blast.Blast;
import com.phasetranscrystal.blast.Registries;
import com.phasetranscrystal.blast.player.KeyInput;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.Event;
import org.apache.commons.lang3.function.ToBooleanBiFunction;
import org.apache.commons.lang3.function.TriConsumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Math;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class Skill<T extends Entity> {
    public static final Skill<Entity> EMPTY = Skill.Builder.of(0, "default").addBehavior("default", b -> {
    }).build(Entity.class);

    public static final Logger LOGGER = LogManager.getLogger("BreaBlast:Skill");
    public static final ResourceLocation NAME = Blast.location("skill");

    public final int initialEnergy;

    @Nonnull
    public final Behavior<T> initBehavior;
    public final String initBehaviorName;
    public final ImmutableMap<String, Behavior<T>> behaviors;

    public final Consumer<SkillData<T>> onStart;
    public final Consumer<SkillData<T>> onEnd;
    //第二个参数(String)为将被转换到的状态的id
    public final ToBooleanBiFunction<SkillData<T>, String> judge;
    public final BiConsumer<SkillData<T>, String> stateChange;
    public final KeyInput.Consumer<T> keyChange;

    public final IntList keys;
    public final ImmutableMap<Class<? extends Event>, BiConsumer<? extends Event, SkillData<T>>> listeners;
//    public final ImmutableSet<Flag> flags;

    public final Class<T> bindingEntityClass;

    public Skill(Builder<T> builder, Class<T> bindingEntityClass) {
        this(builder.initialEnergy, builder.initBehavior, Maps.transformValues(builder.behaviors, Behavior.Builder::build), builder.onStart, builder.onEnd, builder.judge, builder.behaviorChange, builder.keyChange, builder.keys, builder.listeners, bindingEntityClass);
    }

    public Skill(int initialEnergy, @Nonnull String initBehavior, Map<String, Behavior<T>> behaviors, Consumer<SkillData<T>> onStart,
                 Consumer<SkillData<T>> onEnd, ToBooleanBiFunction<SkillData<T>, String> judge, BiConsumer<SkillData<T>, String> stateChange, KeyInput.Consumer<T> keyChange,
                 IntList keys, Map<Class<? extends Event>, BiConsumer<? extends Event, SkillData<T>>> listeners, Class<T> bindingEntityClass) {

        if (behaviors.isEmpty()) {
            LOGGER.error("Behaviors list should hava at least one element. A empty Element is added.", new Throwable());
            this.behaviors = ImmutableMap.of("default", (Behavior<T>) Behavior.EMPTY);
        } else {
            this.behaviors = ImmutableMap.copyOf(behaviors);
        }

        if (this.behaviors.containsKey(initBehavior)) {
            this.initBehavior = this.behaviors.get(initBehavior);
            this.initBehaviorName = initBehavior;
        } else {
            LOGGER.error("Init behavior(name={}) not exist in behaviors({}). Changed.", initBehavior, Arrays.toString(behaviors.keySet().toArray()));
            Map.Entry<String, Behavior<T>> entry = this.behaviors.entrySet().stream().findAny().get();
            this.initBehavior = entry.getValue();
            this.initBehaviorName = entry.getKey();
        }

        this.initialEnergy = Math.clamp(0, this.initBehavior.getMaxEnergy(), initialEnergy);

        this.onStart = onStart;
        this.onEnd = onEnd;
        this.judge = judge;
        this.stateChange = stateChange;
        this.keyChange = keyChange;

        this.keys = IntList.of(keys.toIntArray());
        this.listeners = ImmutableMap.copyOf(listeners);
//        this.flags = ImmutableSet.copyOf(builder.flags);

        this.bindingEntityClass = bindingEntityClass;
    }

    public static class Builder<T extends Entity> {
        public final Consumer<SkillData<T>> NO_ACTION = data -> {
        };

        public int initialEnergy;

        @Nonnull
        public String initBehavior;

        public HashMap<String, Behavior.Builder<T>> behaviors = new HashMap<>();

        public Consumer<SkillData<T>> onStart = NO_ACTION;
        public Consumer<SkillData<T>> onEnd = NO_ACTION;
        public ToBooleanBiFunction<SkillData<T>, String> judge = (data, behavior) -> true;
        public BiConsumer<SkillData<T>, String> behaviorChange = (data, behaviorRecord) -> {
        };
        public KeyInput.Consumer<T> keyChange = (data, packet) -> {
        };
        public IntList keys = new IntArrayList();
        public HashMap<Class<? extends Event>, BiConsumer<? extends Event, SkillData<T>>> listeners = new HashMap<>();

//        public HashSet<Flag> flags = new HashSet<>();

        public Builder(@NotNull String initBehavior) {
            this.initBehavior = initBehavior;
        }

        public Builder(int initialEnergy, @NotNull String initBehavior) {
            this.initialEnergy = initialEnergy;
            this.initBehavior = initBehavior;
        }

        public static <T extends Entity> Builder<T> of(int initialEnergy, @NotNull String initBehavior) {
            return new Builder<>(initialEnergy, initBehavior);
        }

        public static <T extends Entity> Builder<T> of(Skill<T> skill) {
            Builder<T> builder = of(skill.initialEnergy, "default");
            return builder.copyFrom(skill);
        }

        public Builder<T> copyFrom(Skill<T> skill) {

            this.initialEnergy = skill.initialEnergy;

            this.initBehavior = skill.initBehaviorName;

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

//        public Builder<T> inactive(Consumer<Behavior.Builder<T>> inactive) {
//            inactive.accept(this.behaviors.computeIfAbsent("inactive", key -> Behavior.Builder.create()));
//            return this;
//        }
//
//        public Builder<T> active(Consumer<Behavior.Builder<T>> active) {
//            active.accept(this.behaviors.computeIfAbsent("active", key -> Behavior.Builder.<T>create().onActiveEnergyEmpty(data -> data.switchTo("inactive"))));
//            return this;
//        }

        public Builder<T> setInitialEnergy(int initialEnergy) {
            this.initialEnergy = initialEnergy;
            return this;
        }

        public Builder<T> setInitBehavior(@Nonnull String initBehavior) {
            this.initBehavior = initBehavior;
            return this;
        }

        public Builder<T> addBehavior(String name, Consumer<Behavior.Builder<T>> consumer) {
            Behavior.Builder<T> builder = Behavior.Builder.create();
            consumer.accept(builder);
            return addBehavior(name, builder);
        }

        public Builder<T> addBehavior(int maxEnergy, int maxCharge, String name, Consumer<Behavior.Builder<T>> consumer) {
            Behavior.Builder<T> builder = Behavior.Builder.create(maxEnergy, maxCharge);
            consumer.accept(builder);
            return addBehavior(name, builder);
        }

        public Builder<T> addBehavior(String name, Behavior.Builder<T> builder) {
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


        public Builder<T> judge(ToBooleanBiFunction<SkillData<T>, String> judge) {
            this.judge = judge;
            return this;
        }

        public Builder<T> onBehaviorChange(BiConsumer<SkillData<T>, String> consumer) {
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

        public Builder<T> onEnd(Consumer<SkillData<T>> consumer) {
            onEnd = consumer;
            return this;
        }

        public Skill<T> build(Class<T> targetType) {
            return new Skill<>(this, targetType);
        }

        public Skill<T> build(Consumer<SkillData<T>> consumer, Class<T> targetType) {
            onEnd = consumer;
            return build(targetType);
        }
    }

    @Deprecated(forRemoval = true)
    public enum Flag implements StringRepresentable {
        AUTO_START("auto_start", (builder, redirectName1, redirectName2) -> builder.behaviors.get(redirectName1 == null ? "inactive" : redirectName1).onChargeReady(data -> data.switchTo(redirectName2 == null ? "active" : redirectName2))),
        AUTO_FINISH("auto_finish", (builder, redirectName1, redirectName2) -> builder.behaviors.get(Objects.requireNonNullElse(redirectName1, "active")).onActiveEnergyEmpty(data -> data.switchTo(Objects.requireNonNullElse(redirectName2, "inactive")))),

//        INSTANT_COMPLETE("instant_complete", (builder, redirectName1, redirectName2) -> builder.maxStageEnergy = 0),
//        PASSIVITY("passivity", (builder, redirectName1, redirectName2) -> builder.energy = 0),

        INTERRUPTIBLE("interruptible"),

        TIME_ADD_INACTIVE_ENERGY("time_add_inactive_energy", (builder, redirectName1, redirectName2) -> builder.behaviors.get(redirectName1 == null ? "inactive" : redirectName1).onTick((event, data) -> data.addEnergy(1))),
        CLEAN_ACTIVE_ENERGY("clean_active_energy", (builder, redirectName1, redirectName2) -> builder.behaviors.get(Objects.requireNonNullElse(redirectName1, "active")).endWith(data -> data.setEnergy(0))),
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
