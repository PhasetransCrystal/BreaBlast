package com.phasetranscrystal.blast.skill;

import com.google.common.collect.ImmutableMap;
import com.phasetranscrystal.blast.player.KeyInput;
import com.phasetranscrystal.horiz.EventConsumer;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.HashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class Behavior<T extends Entity> {
    public static final Behavior<Entity> EMPTY = Builder.create().build();



    public final boolean useActiveCounter;
    public final int maxStageEnergy;
    public final int maxCharge;
    public final Consumer<SkillData<T>> start;
    public final Consumer<SkillData<T>> end;
    public final Consumer<SkillData<T>> chargeReady;
    public final Consumer<SkillData<T>> chargeFull;
    public final Consumer<SkillData<T>> energyEmpty;
    public final BiConsumer<SkillData<T>, Integer> energyChange;
    public final BiConsumer<SkillData<T>, Integer> chargeChange;//数值为充能层数变化量
    public final IntList keys;
    public final KeyInput.Consumer<T> keyChange;
    public final ImmutableMap<Class<? extends Event>, BiConsumer<? extends Event, SkillData<T>>> listeners;

    public Behavior(Builder<T> builder, boolean useActiveCounter) {

        this.useActiveCounter = useActiveCounter;
        this.maxStageEnergy = Math.max(builder.maxStageEnergy, 0);
        this.maxCharge = Math.max(builder.maxCharge, 1);
        this.energyChange = builder.energyChange;
        this.chargeChange = builder.chargeChange;
        this.start = builder.start;
        this.end = builder.end;
        this.chargeReady = builder.chargeReady;
        this.chargeFull = builder.chargeFull;
        this.energyEmpty = builder.energyEmpty;
        this.keys = IntList.of(builder.keys.toIntArray());
        this.keyChange = builder.keyChange;
        this.listeners = ImmutableMap.copyOf(builder.listeners);
    }

    public int getMaxEnergy(){
        return maxStageEnergy * maxCharge;
    }

    public static class Builder<T extends Entity> {
        public final Consumer<SkillData<T>> EMPTY = data -> {
        };
        public final BiConsumer<SkillData<T>, Integer> EMPTY_BI = (data, relate) -> {
        };
        public int maxStageEnergy = 64;
        public int maxCharge = 1;
        public BiConsumer<SkillData<T>, Integer> energyChange = EMPTY_BI;
        public BiConsumer<SkillData<T>, Integer> chargeChange = EMPTY_BI;
        public Consumer<SkillData<T>> chargeReady = EMPTY;
        public Consumer<SkillData<T>> chargeFull = EMPTY;
        public Consumer<SkillData<T>> energyEmpty = EMPTY;
        public Consumer<SkillData<T>> start = EMPTY;
        public Consumer<SkillData<T>> end = EMPTY;
        public IntList keys = new IntArrayList();
        public KeyInput.Consumer<T> keyChange = (data, packet) -> {
        };
        public HashMap<Class<? extends Event>, BiConsumer<? extends Event, SkillData<T>>> listeners = new HashMap<>();

        public static <T extends Entity> Builder<T> create() {
            return new Builder<>();
        }

        public static <T extends Entity> Builder<T> create(int maxEnergy, int maxCharge) {
            return new Builder<T>().setMaxStageEnergy(maxEnergy).setMaxCharge(maxCharge);
        }

        public static <T extends Entity> Builder<T> create(Behavior<T> root) {
            Builder<T> builder = new Builder<T>();
            builder.energyChange = root.energyChange;
            builder.chargeChange = root.chargeChange;
            builder.start = root.start;
            builder.end = root.end;
            builder.listeners.putAll(root.listeners);
            return builder;
        }

        public Builder<T> startWith(Consumer<SkillData<T>> consumer) {
            start = consumer;
            return this;
        }

        public Builder<T> setMaxStageEnergy(int maxStageEnergy) {
            this.maxStageEnergy = maxStageEnergy;
            return this;
        }

        public Builder<T> setMaxCharge(int maxCharge) {
            this.maxCharge = maxCharge;
            return this;
        }

        public Builder<T> chargeChanged(BiConsumer<SkillData<T>, Integer> consumer) {
            this.chargeChange = consumer;
            return this;
        }

        public Builder<T> energyChanged(BiConsumer<SkillData<T>, Integer> consumer) {
            this.energyChange = consumer;
            return this;
        }

        public Builder<T> onChargeFull(Consumer<SkillData<T>> consumer) {
            this.chargeFull = consumer;
            return this;
        }

        public Builder<T> onChargeReady(Consumer<SkillData<T>> consumer) {
            this.chargeReady = consumer;
            return this;
        }

        public Builder<T> onActiveEnergyEmpty(Consumer<SkillData<T>> consumer) {
            this.energyEmpty = consumer;
            return this;
        }

        public Builder<T> onTick(BiConsumer<EntityTickEvent.Post, SkillData<T>> consumer) {
            listeners.put(EntityTickEvent.Post.class, consumer);
            return this;
        }

        public Builder<T> onHurt(BiConsumer<LivingDamageEvent.Post, SkillData<T>> consumer) {
            listeners.put(LivingDamageEvent.Post.class, consumer);
            return this;
        }

        public Builder<T> onAttack(BiConsumer<EventConsumer.EntityAttackEvent.Post, SkillData<T>> consumer) {
            listeners.put(EventConsumer.EntityAttackEvent.Post.class, consumer);
            return this;
        }

        public Builder<T> onKillTarget(BiConsumer<EventConsumer.EntityKillEvent.Post, SkillData<T>> consumer) {
            listeners.put(EventConsumer.EntityKillEvent.Post.class, consumer);
            return this;
        }

        public <E extends Event> Builder<T> onEvent(Class<E> clazz, BiConsumer<E, SkillData<T>> consumer) {
            listeners.put(clazz, consumer);
            return this;
        }

        public Builder<T> apply(Consumer<Builder<T>> consumer) {
            consumer.accept(this);
            return this;
        }

        public Builder<T> endWith(Consumer<SkillData<T>> consumer) {
            end = consumer;
            return this;
        }

        public Builder<T> onKeyInput(KeyInput.Consumer<T> keyChange, int... keyListeners) {
            this.keys = new IntArrayList(keyListeners);
            this.keyChange = keyChange;
            return this;
        }

        public Behavior<T> build() {
            return new Behavior<>(this,false);
        }

        public Behavior<T> build(boolean useActiveCounter) {
            return new Behavior<>(this,useActiveCounter);
        }
    }
}
