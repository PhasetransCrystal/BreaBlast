package com.phasetranscrystal.blast.skill;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.phasetranscrystal.blast.Blast;
import com.phasetranscrystal.blast.Registries;
import com.phasetranscrystal.blast.registry.AttributeRegistry;
import com.phasetranscrystal.horiz.EntityEventDistribute;
import com.phasetranscrystal.horiz.Horiz;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Math;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class SkillData<T extends Entity> {
    public static final Codec<SkillData<? extends Entity>> CODEC = RecordCodecBuilder.create((instance) -> instance.group(
            Codec.INT.fieldOf("energyEnergy").forGetter(SkillData::getInactiveEnergy),
            Codec.STRING.fieldOf("behavior").forGetter(SkillData::getBehaviorName),
            Codec.BOOL.fieldOf("enabled").forGetter(SkillData::isEnabled),
            Registries.SKILL.byNameCodec().fieldOf("skill").forGetter(i -> i.skill),
            Codec.INT.fieldOf("activeTimes").forGetter(SkillData::getActiveTimes),
            Codec.pair(Codec.STRING, Codec.INT).optionalFieldOf("schedulerInfo").forGetter(i -> Optional.ofNullable(i.occupy ? Pair.of(i.stageChangeScheduleName, i.delay) : null)),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).fieldOf("cacheData").forGetter(SkillData::getCacheData),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).fieldOf("extendData").forGetter(SkillData::getExtendData),
            Codec.STRING.listOf().fieldOf("markCleanKeys").forGetter(i -> i.markCleanKeys.stream().toList()),
            Codec.STRING.listOf().fieldOf("markCleanCacheOnce").forGetter(i -> i.markCleanCacheOnce.stream().toList())
    ).apply(instance, SkillData::new));
    public static final Logger LOGGER = LogManager.getLogger("BreaBlast:Skill/Data");
    public static final ResourceLocation SKILL_BASE_KEY = ResourceLocation.fromNamespaceAndPath(Blast.MODID, "skill_base");
    public static final ResourceLocation SKILL_BEHAVIOR_KEY = ResourceLocation.fromNamespaceAndPath(Blast.MODID, "skill_behavior");

    private boolean enabled = true;
    public final Skill<T> skill;
    public final ResourceLocation skillName;
    private T entity;//NOSAVE
    private boolean enableAttribute = true;//NOSAVE

    private int energy;
    private String behaviorName;
    private Behavior<T> behavior;

    private int activeTimes = 0;

    public final Map<String, String> cacheData = new HashMap<>();
    public final Map<String, String> extendData = new HashMap<>();
    private HashSet<String> markCleanKeys = new HashSet<>();
    private HashSet<String> markCleanCacheOnce = new HashSet<>();

    private final Set<Pair<Holder<Attribute>, ResourceLocation>> attributeCache = new HashSet<>();//NOSAVE

    private int delay = 0;//NOSAVE
    private boolean occupy = false;
    private String stageChangeScheduleName = null;

    private boolean markChanged = true;
    private boolean loading = false;


    public SkillData(final Skill<T> skill) {
        this.skill = skill;
        this.skillName = skill.getResourceKey().location();
        this.behaviorName = skill.initBehaviorName;
        this.behavior = skill.initBehavior;
        this.energy = skill.initialEnergy;
    }

    @SuppressWarnings("unchecked")
    protected SkillData(int energy, String behaviorName, boolean enabled, Skill<?> skill, int activeTimes, Optional<Pair<String, Integer>> stageChangeScheduler,
                        Map<String, String> cacheData, Map<String, String> extendData, List<String> markClean, List<String> markCleanCacheOnce) {
        this.skill = (Skill<T>) skill;
        this.skillName = skill.getResourceKey().location();
        if (!skill.behaviors.containsKey(behaviorName)) {

            LOGGER.warn("Unable to find behavior({}) when load, switched to default status.", behaviorName);
            this.energy = skill.initialEnergy;
            this.behaviorName = (String) skill.behaviors.keySet().toArray()[0];
            this.behavior = (Behavior<T>) skill.behaviors.get(this.behaviorName);
        } else {
            this.loading = true;
            this.energy = energy;
            this.enabled = enabled;
            this.activeTimes = activeTimes;
            this.behaviorName = behaviorName;
            this.behavior = (Behavior<T>) skill.behaviors.get(behaviorName);
            this.cacheData.putAll(cacheData);
            this.extendData.putAll(extendData);
            this.markCleanKeys.addAll(markClean);
            this.markCleanCacheOnce.addAll(markCleanCacheOnce);
            if (stageChangeScheduler.isPresent()) {
                if (!skill.behaviors.containsKey(stageChangeScheduler.get().getFirst())) {
                    LOGGER.warn("Unable to find scheduled target behavior({}) when load, state change won't work.", stageChangeScheduler.get().getFirst());
                } else {
                    occupy = true;
                    stageChangeScheduleName = stageChangeScheduler.get().getFirst();
                    delay = stageChangeScheduler.get().getSecond();
                }
            }
        }
    }

    //---[实体绑定初始化]---

    @SuppressWarnings("unchecked,unused")
    public boolean tryCastAnd(LivingEntity entity, Consumer<T> consumer) {
        try {
            if (skill.bindingEntityClass.isAssignableFrom(entity.getClass())) {
                LOGGER.warn("Unable to cast an instance of {} to {}. No legal relation found.", entity.getClass(), skill.bindingEntityClass);
                ;
            }
            consumer.accept((T) entity);
            return true;
        } catch (ClassCastException e) {
            LOGGER.warn("Unable to cast an instance of {} to {}.", entity.getClass(), skill.bindingEntityClass);
            LOGGER.warn("Details:", e);
            return false;
        }
    }

    public void bindEntity(T entity) {
        if (this.entity != null && !entity.getUUID().equals(this.entity.getUUID())) {
            LOGGER.warn("Entity instance (class={}) already exists. Skipped.", entity.getClass());
            return;
        }
        if (!(entity instanceof LivingEntity living)) {
            this.enableAttribute = false;
        } else if (!living.getAttributes().hasAttribute(AttributeRegistry.SKILL_INACTIVE_ENERGY) ||
                !living.getAttributes().hasAttribute(AttributeRegistry.SKILL_ACTIVE_ENERGY) ||
                !living.getAttributes().hasAttribute(AttributeRegistry.SKILL_MAX_CHARGE) ||
                !living.getAttributes().hasAttribute(AttributeRegistry.ENERGY)) {
            LOGGER.info("Try to bind skill {} to {} with no target attribute. Dispatch Enabled.", skillName, entity);
            this.enableAttribute = false;
        } else {
            this.enableAttribute = true;
            //由于我们在上面已经判断过具有属性 所以这里并不会出错
            living.getAttribute(this.behavior.useActiveCounter ? AttributeRegistry.SKILL_ACTIVE_ENERGY : AttributeRegistry.SKILL_INACTIVE_ENERGY).setBaseValue(this.behavior.maxStageEnergy);
            living.getAttribute(AttributeRegistry.SKILL_MAX_CHARGE).setBaseValue(this.behavior.maxCharge);
            living.getAttribute(AttributeRegistry.ENERGY).setBaseValue(this.energy);
        }

        this.entity = entity;
        if (!enabled) return;

        enable();
    }

    @SuppressWarnings("all")
    private void enable() {
        enabled = true;

        //技能基础行为初始化
        EntityEventDistribute distribute = entity.getData(Horiz.EVENT_DISTRIBUTE);
//        distribute.add(EntityTickEvent.Post.class, ticker, Skill.NAME, skillName, SKILL_BASE_KEY);
        skill.onStart.accept(this);
        skill.listeners.forEach((clazz, consumer) -> distribute.add(clazz, event -> ((BiConsumer) consumer).accept(event, this), Skill.NAME, skillName, SKILL_BASE_KEY));

        markChanged = true;

        postBehavior(distribute);
    }

    //在技能被启用或状态被切换至时执行
    @SuppressWarnings("unchecked,rawtypes")
    private void postBehavior(EntityEventDistribute distribute) {

        markChanged = true;

        String behaviorNameCache = this.behaviorName;
        behavior.start.accept(this);
        if (!this.behaviorName.equals(behaviorNameCache)) return;

        int charge = behavior.maxStageEnergy == 0 ? 0 : energy / behavior.maxStageEnergy;
        if (charge > 0 && behavior.maxCharge > 1) {
            behavior.chargeReady.accept(this);
        }
        if (charge >= behavior.maxCharge) {
            behavior.chargeFull.accept(this);
        }
        if (energy <= 0) {
            behavior.energyEmpty.accept(this);
        }

        behavior.energyChange.accept(this, energy);
        if (charge > 0) {
            behavior.chargeChange.accept(this, charge);
        }

        behavior.listeners.forEach((clazz, consumer) -> distribute.add(clazz, event -> ((BiConsumer) consumer).accept(event, this), Skill.NAME, skillName, SKILL_BEHAVIOR_KEY));

        loading = false;
    }

    @SuppressWarnings("all")
    public boolean switchToIfNot(String behavior) {
        if (!this.behaviorName.equals(behavior)) {
            return switchTo(behavior);
        }
        return false;
    }


    public boolean switchTo(String behaviorName) {
        if (!enabled || occupy) return false;
        EntityEventDistribute eventDtb = entity.getData(Horiz.EVENT_DISTRIBUTE);

        if (!skill.behaviors.containsKey(behaviorName)) {
            LOGGER.error("Unable to find Behavior(name={}), state switch canceled. See debug.log for more details.", behaviorName);
            LOGGER.debug("Details: Skill={} Entity={uuid={}, type={}}", skillName, entity.getUUID(), entity.getType());
            LOGGER.debug("Fired at com.phasetranscrystal.nonard.skill.SkillData#switchTo.", new Throwable());
            return false;
        }

        if (!skill.judge.applyAsBoolean(this, behaviorName))
            return false;

        eventDtb.removeMarked(Skill.NAME, skillName, SKILL_BEHAVIOR_KEY);
        @Nonnull Behavior<T> behaviorTo = skill.behaviors.get(behaviorName);

        skill.stateChange.accept(this, behaviorName);
        this.behavior.end.accept(this);

        if (entity instanceof LivingEntity living) {
            attributeCache.forEach(pair -> living.getAttribute(pair.getFirst()).removeModifier(pair.getSecond()));
            attributeCache.clear();
        }

        markCleanKeys.forEach(cacheData::remove);
        markCleanKeys = markCleanCacheOnce;
        markCleanCacheOnce = new HashSet<>();

        this.behaviorName = behaviorName;
        this.behavior = behaviorTo;
        postBehavior(eventDtb);


        return true;
    }

    public boolean requestEnable() {
        if (entity == null || entity.isRemoved() || enabled) return false;
        enable();
        return true;
    }

    public boolean requestDisable() {
        if (!enabled) return false;
        disable();
        return true;
    }


    private boolean disable() {
        EntityEventDistribute distribute = entity.getData(Horiz.EVENT_DISTRIBUTE);
        distribute.removeMarked(Skill.NAME, skillName);
        skill.onEnd.accept(this);
        if (entity instanceof LivingEntity living) {
            attributeCache.forEach(pair -> living.getAttribute(pair.getFirst()).removeModifier(pair.getSecond()));
            attributeCache.clear();
        }
        enabled = false;
        behaviorName = skill.initBehaviorName;
        energy = skill.initialEnergy;
        activeTimes = 0;
        markCleanCacheOnce.clear();
        markCleanKeys.clear();
        cacheData.clear();
        markChanged = true;
        return true;
    }


    //---[能力分发调度]---

//    public int getEnergy(){
//        if(enableAttribute){
//            ((LivingEntity) entity).getAttribute(AttributeRegistry.)
//        }
//    }

    //---[状态 State]---
    public boolean addEnergy() {
        return addEnergy(1) == 1;
    }

    //    @SuppressWarnings("all")
    public int addEnergy(int amount) {
        return addEnergy(amount, false);
    }

    public int addEnergy(int amount, boolean consumerChargeLessThanZero) {
        return addEnergy(amount, consumerChargeLessThanZero, behavior.maxCharge);
    }

    public int addEnergyAllowOverCharge(int amount, boolean consumerChargeLessThanZero) {
        return addEnergy(amount, consumerChargeLessThanZero, Integer.MAX_VALUE);
    }

    public int addEnergy(int amount, boolean consumerChargeLessThanZero, int allowedOverCharge) {
        if (!enabled) return 0;
        if (amount == 0) return 0;

        // 计算最大可增加的能量
        int maxEnergy = allowedOverCharge == Integer.MAX_VALUE ? Integer.MAX_VALUE : behavior.getMaxEnergy();
        if (amount > 0 && this.energy >= maxEnergy) return 0;

        int chargeCache = this.energy / behavior.maxStageEnergy;
        int energyCache = this.energy;

        energy = Math.clamp((amount > 0 || consumerChargeLessThanZero) ? 0 : chargeCache * behavior.maxStageEnergy, maxEnergy, this.energy + amount);

        int deltaEnergy = this.energy - energyCache;


        if (deltaEnergy == 0) return 0;

        behavior.energyChange.accept(this, deltaEnergy);

        int chargeTo = getCharge();
        if (chargeTo != chargeCache) {
            behavior.chargeChange.accept(this, chargeTo - chargeCache);
            if (behavior.maxCharge > 1 && chargeCache <= 0 && chargeTo >= 1)
                behavior.chargeReady.accept(this);
            if (chargeTo >= behavior.maxCharge && chargeCache < behavior.maxCharge)
                behavior.chargeFull.accept(this);
        }

        markChanged = true;

        return deltaEnergy; // 如果没有增加charge，仍然返回消耗的总能量点数
    }

    //---[缓存数据 DataCache]---

    public String getCacheData(String key) {
        return cacheData.get(key);
    }

    public int getCacheDataAsInt(String key, int fallback, boolean logIfFailed) {
        String result = cacheData.get(key);
        if (result != null) {
            try {
                return Integer.parseInt(result);
            } catch (Exception ignored) {
            }
        }
        if (logIfFailed) {
            String s = result == null ? "null" : ("\"" + result + "\"");
            LOGGER.error("Unable to parse String({}) to int. Fallback used. See debug.log for more details.", s);
            LOGGER.debug("Details: CacheData(key={}, value={}) to int. Skill={}, Stage={}", key, s, skill, behaviorName);
            LOGGER.debug("Fired at com.phasetranscrystal.nonard.skill.SkillData#getCacheDataAsInt", new Throwable());
        }
        return fallback;
    }

    public double getCacheDataAsDouble(String key, int fallback, boolean logIfFailed) {
        String result = cacheData.get(key);
        if (result != null) {
            try {
                return Double.parseDouble(result);
            } catch (Exception ignored) {
            }
        }
        if (logIfFailed) {
            String s = result == null ? "null" : ("\"" + result + "\"");
            LOGGER.error("Unable to parse String({}) to double. Fallback used. See debug.log for more details.", s);
            LOGGER.debug("Details: CacheData(key={}, value={}) to double. Skill={}, Stage={}", key, s, skill, behaviorName);
            LOGGER.debug("Fired at com.phasetranscrystal.nonard.skill.SkillData#getCacheDataAsDouble", new Throwable());
        }
        return fallback;
    }

    public Map<String, String> getCacheData() {
        return cacheData;
    }

    public String putCacheData(String key, String value, boolean markAutoClean, boolean keepToNextStage) {
        if (!enabled || entity == null) return null;
        if (markAutoClean) {
            if (keepToNextStage) markCleanCacheOnce.add(key);
            else markCleanKeys.add(key);
        }
        return cacheData.put(key, value);
    }

    //---[额外数据 ExtendData]---
    public String putExtendData(String key, String value) {
        if (!enabled || entity == null) return null;
        return extendData.put(key, value);
    }

    public String getExtendData(String key) {
        return extendData.get(key);
    }

    public Map<String, String> getExtendData() {
        return extendData;
    }


    @SuppressWarnings("null")
    public boolean addAutoCleanAttribute(AttributeModifier modifier, Holder<Attribute> type) {
        AttributeInstance instance;
        if (!enabled || entity == null || !(entity instanceof LivingEntity living) || (instance = living.getAttribute(type)) == null)
            return false;

        instance.addOrUpdateTransientModifier(modifier);
        this.attributeCache.add(Pair.of(type, modifier.id()));
        return true;
    }


    //---[数据获取 Getter]---
    public int getInactiveEnergy() {
        return energy;
    }

    public int getCharge() {
        return this.energy / behavior.maxStageEnergy;
    }

    public T getEntity() {
        return entity;
    }

    public Behavior<T> getBehavior() {
        return behavior;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getActiveTimes() {
        return activeTimes;
    }

    public void consumerActiveStart() {
        this.activeTimes++;
        //TODO 全局计数器
    }

    public int getEnergy() {
        return energy;
    }

    public String getBehaviorName() {
        return behaviorName;
    }

    public void cacheOnce(String key) {
        markCleanCacheOnce.add(key);
        markCleanKeys.remove(key);
    }

    public void setEnergy(int energy) {
        addEnergy(energy - this.energy);
    }

    public void setCharge(int charge) {
        setEnergy(behavior.maxStageEnergy * charge);
    }


    public void consumeChange() {
        this.markChanged = false;
    }

    public boolean isChanged() {
        return markChanged;
    }

    //    public record BehaviorRecord(String behaviorName, boolean isActive) {
//        public static final Codec<BehaviorRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
//                Codec.STRING.fieldOf("name").forGetter(BehaviorRecord::behaviorName),
//                Codec.BOOL.fieldOf("active").forGetter(BehaviorRecord::isActive)
//        ).apply(instance, BehaviorRecord::new));
//    }
}
