package com.phasetranscrystal.blast.skill;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.phasetranscrystal.blast.Blast;
import com.phasetranscrystal.blast.Registries;
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

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class SkillData<T extends Entity> {
    public static final Codec<SkillData<? extends Entity>> CODEC = RecordCodecBuilder.create((instance) -> instance.group(
            Codec.INT.fieldOf("inactiveEnergy").forGetter(SkillData::getInactiveEnergy),
            Codec.INT.fieldOf("activeEnergy").forGetter(SkillData::getActiveEnergy),
            Codec.STRING.optionalFieldOf("behavior").forGetter(SkillData::getBehaviorName),
            Codec.BOOL.fieldOf("enabled").forGetter(SkillData::isEnabled),
            Registries.SKILL.byNameCodec().fieldOf("skill").forGetter(i -> i.skill),
            Codec.INT.fieldOf("activeTimes").forGetter(SkillData::getActiveTimes),
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

    private int inactiveEnergy;
    private int activeEnergy = 0;
    private Optional<String> behaviorName;
    private Optional<Behavior<T>> behavior;

    private int activeTimes = 0;

    public final Map<String, String> cacheData = new HashMap<>();
    public final Map<String, String> extendData = new HashMap<>();
    private HashSet<String> markCleanKeys = new HashSet<>();
    private HashSet<String> markCleanCacheOnce = new HashSet<>();

    private final Set<Pair<Holder<Attribute>, ResourceLocation>> attributeCache = new HashSet<>();//NOSAVE

    private int delay = 0;//NOSAVE
    private boolean occupy = false;
    private Runnable stageChangeSchedule = () -> {//NOSAVE
    };
    private final Consumer<EntityTickEvent.Post> ticker = event -> tickerHandler();//FINAL

    private boolean markChanged = true;


    public SkillData(final Skill<T> skill) {
        this.skill = skill;
        this.skillName = skill.getResourceKey().location();
        this.inactiveEnergy = skill.initialEnergy + skill.initialCharge * skill.inactiveEnergy;
        this.behaviorName = skill.initBehavior;
        this.behavior = behaviorName.map(skill.behaviors::get);
    }

    @SuppressWarnings("unchecked")
    protected SkillData(int inactiveEnergy, int activeEnergy, Optional<String> behaviorName, boolean enabled, Skill<?> skill, int activeTimes, Map<String, String> cacheData, Map<String, String> extendData, List<String> markClean, List<String> markCleanCacheOnce) {

        if (behaviorName.isEmpty() || skill.behaviors.containsKey(behaviorName.get())) {
            this.inactiveEnergy = inactiveEnergy;
            this.activeEnergy = activeEnergy;
            this.enabled = enabled;
            this.activeTimes = activeTimes;
            this.skill = (Skill<T>) skill;
            this.behaviorName = behaviorName;
            this.behavior = behaviorName.map(this.skill.behaviors::get);
            this.skillName = skill.getResourceKey().location();
            this.cacheData.putAll(cacheData);
            this.extendData.putAll(extendData);
            this.markCleanKeys.addAll(markClean);
            this.markCleanCacheOnce.addAll(markCleanCacheOnce);
        } else {
            LOGGER.warn("Unable to find behavior({}) when load, switched to default status.", behaviorName);
            this.skill = (Skill<T>) skill;
            this.skillName = skill.getResourceKey().location();
            this.inactiveEnergy = skill.initialEnergy + skill.initialCharge * skill.inactiveEnergy;
            this.behaviorName = skill.initBehavior;
        }
    }

    //---[实体绑定初始化]---

    @SuppressWarnings("unchecked,unused")
    public boolean tryCastAnd(LivingEntity entity, Consumer<T> consumer) {
        try {
            if(skill.clazz.isAssignableFrom(entity.getClass())) return false;
            consumer.accept((T) entity);
            return true;
        } catch (ClassCastException e) {
            return false;
        }
    }

    public void bindEntity(T entity) {
        if (this.entity != null && !entity.getUUID().equals(this.entity.getUUID())) {
            LOGGER.warn("Entity instance (class={}) already exists. Skipped.", entity.getClass());
            return;
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
        distribute.add(EntityTickEvent.Post.class, ticker, Skill.NAME, skillName, SKILL_BASE_KEY);
        skill.onStart.accept(this);
        skill.listeners.forEach((clazz, consumer) -> distribute.add(clazz, event -> ((BiConsumer) consumer).accept(event, this), Skill.NAME, skillName, SKILL_BASE_KEY));

        markChanged = true;

        postBehavior(distribute);
    }

    @SuppressWarnings("unchecked,rawtypes")
    private void postBehavior(EntityEventDistribute distribute) {
        this.behavior.ifPresent(behavior -> {
            var name = this.behaviorName;
            behavior.start.accept(this);

            int charge = inactiveEnergy / skill.inactiveEnergy;
            if (charge > 0 && skill.maxCharge > 1) {
                behavior.chargeReady.accept(this);
            }
            if (charge >= skill.maxCharge) {
                behavior.chargeFull.accept(this);
            }
            if (activeEnergy <= 0) {
                behavior.activeEnd.accept(this);
            }

            if (inactiveEnergy > 0) {
                behavior.inactiveEnergyChange.accept(this, inactiveEnergy);
            }
            if (charge > 0) {
                behavior.chargeChange.accept(this, charge);
            }
            if (activeEnergy < skill.activeEnergy || skill.activeEnergy == 0) {
                behavior.activeEnergyChange.accept(this, skill.activeEnergy - activeEnergy);
            }

            if (Objects.equals(behaviorName.orElse(null), name.orElse(null))) {
                behavior.listeners.forEach((clazz, consumer) -> distribute.add(clazz, event -> ((BiConsumer) consumer).accept(event, this), Skill.NAME, skillName, SKILL_BEHAVIOR_KEY));
            }
        });
        markChanged = true;
    }

    @SuppressWarnings("all")
    public boolean switchToIfNot(String behavior) {
        if (!Objects.equals(this.behaviorName.orElse(null), behavior)) {
            return switchTo(behavior);
        }
        return false;
    }


    public boolean switchTo(String behaviorName) {
        if (!enabled || occupy) return false;
        EntityEventDistribute distribute = entity.getData(Horiz.EVENT_DISTRIBUTE);

        var newBehavior = Optional.ofNullable(behaviorName);

        if (!skill.judge.applyAsBoolean(this, newBehavior))
            return false;

        distribute.removeMarked(Skill.NAME, skillName, SKILL_BEHAVIOR_KEY);
        Optional<Behavior<T>> behaviorTo = newBehavior.map(skill.behaviors::get);
        if (newBehavior.isPresent() && behaviorTo.isEmpty()) {
            LOGGER.error("Unable to find Behavior(name={}), state switch canceled. See debug.log for more details.", behaviorName);
            LOGGER.debug("Details: Skill={} Entity={uuid={}, type={}}", skillName, entity.getUUID(), entity.getType());
            LOGGER.debug("Fired at com.phasetranscrystal.nonard.skill.SkillData#switchTo.", new Throwable());
            return false;
        }

        skill.stateChange.accept(this, newBehavior);
        this.behavior.ifPresent(b -> b.end.accept(this));

        if (entity instanceof LivingEntity living) {
            attributeCache.forEach(pair -> living.getAttribute(pair.getFirst()).removeModifier(pair.getSecond()));
            attributeCache.clear();
        }
        cacheDataRoll();


        this.behaviorName = newBehavior;

        if (behaviorTo.isPresent()) {
            stageChangeSchedule = () -> {
                this.behavior = behaviorTo;
                postBehavior(distribute);
            };
            delay = behaviorTo.get().delay;
        } else {
            stageChangeSchedule = () -> {
                this.behavior = Optional.empty();
                markChanged = true;
            };
            delay = 1;
        }
        occupy = true;

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
        behaviorName = skill.initBehavior;
        inactiveEnergy = skill.initialEnergy + skill.initialCharge * skill.inactiveEnergy;
        activeTimes = 0;
        markCleanCacheOnce.clear();
        markCleanKeys.clear();
        cacheData.clear();
        markChanged = true;
        return true;
    }

    private void cacheDataRoll() {
        markCleanKeys.forEach(cacheData::remove);
        markCleanKeys = markCleanCacheOnce;
        markCleanCacheOnce = new HashSet<>();
    }

    //---[状态 State]---

    public boolean isPassivity() {
        return skill.inactiveEnergy == 0;
    }

    public boolean isInstantComplete() {
        return skill.activeEnergy == 0;
    }

    private void tickerHandler() {
        if (delay >= 1) {
            if (delay == 1) {
                var last = stageChangeSchedule;
                occupy = false;
                stageChangeSchedule.run();
                //防止在技能瞬间完成时新的计划被覆盖
                if (stageChangeSchedule != last) return;
                stageChangeSchedule = () -> {
                };
            }
            delay--;
        }
    }

    public boolean addEnergy() {
        return addEnergy(1) == 1;
    }

    //    @SuppressWarnings("all")
    public int addEnergy(int amount) {
        return addEnergy(amount, false);
    }

    public int addEnergy(int amount, boolean consumerChargeLessThanZero) {
        if (!enabled) return 0;

        // 计算最大可增加的能量
        int maxEnergy = skill.inactiveEnergy * skill.maxCharge;
        int chargeCache = this.inactiveEnergy / skill.inactiveEnergy;
        int energyCache = this.inactiveEnergy;

        inactiveEnergy = Math.clamp((amount > 0 || consumerChargeLessThanZero) ? 0 : chargeCache * skill.inactiveEnergy, maxEnergy, this.inactiveEnergy + amount);

        int deltaEnergy = this.inactiveEnergy - energyCache;


        if (deltaEnergy == 0) return 0;

        this.behavior.ifPresent(behavior -> {
            behavior.inactiveEnergyChange.accept(this, deltaEnergy);

            if (this.inactiveEnergy / skill.inactiveEnergy == chargeCache) return;

            behavior.chargeChange.accept(this, inactiveEnergy / skill.inactiveEnergy - chargeCache);
            if (skill.maxCharge > 1 && chargeCache <= 0 && getCharge() >= 1)
                behavior.chargeReady.accept(this);
            if (getCharge() >= skill.maxCharge && chargeCache < skill.maxCharge)
                behavior.chargeFull.accept(this);

        });

        markChanged = true;

        return deltaEnergy; // 如果没有增加charge，仍然返回消耗的总能量点数
    }

    public int modifyActiveEnergy(int amount) {
        if (amount == 0) return 0;
        int nowa = Math.clamp(0, this.skill.activeEnergy, this.activeEnergy + amount);
        int deltaEnergy = nowa - this.activeEnergy;
        this.activeEnergy = nowa;

        if (deltaEnergy == 0) return 0;

        this.behavior.ifPresent(behavior -> {
            behavior.activeEnergyChange.accept(this, deltaEnergy);
            if (activeEnergy <= 0) {
                behavior.activeEnd.accept(this);
            }
        });

        markChanged = true;
        return deltaEnergy;
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
    public String putExtendData(String key,String value){
        if (!enabled || entity == null) return null;
        return extendData.put(key, value);
    }

    public String getExtendData(String key){
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
        return inactiveEnergy;
    }

    public int getCharge() {
        return this.inactiveEnergy / skill.inactiveEnergy;
    }

    public T getEntity() {
        return entity;
    }

    public Optional<Behavior<T>> getBehavior() {
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

    public int getActiveEnergy() {
        return activeEnergy;
    }

    public Optional<String> getBehaviorName() {
        return behaviorName;
    }

    public void cacheOnce(String key) {
        markCleanCacheOnce.add(key);
        markCleanKeys.remove(key);
    }

    public boolean consumeCharge() {
        this.inactiveEnergy -= skill.inactiveEnergy;
        this.activeEnergy = skill.activeEnergy;
        this.consumerActiveStart();
        return true;
    }

    //下面的设定不会触发能量变动事件
    public void setInactiveEnergy(int inactiveEnergy) {
        int old = this.inactiveEnergy;
        this.inactiveEnergy = Math.clamp(0, skill.inactiveEnergy * skill.maxCharge, inactiveEnergy);
        if(this.inactiveEnergy != old) {
            markChanged = true;
        }
    }

    public void setCharge(int charge) {
        int old = this.inactiveEnergy;
        this.inactiveEnergy = Math.clamp(0, skill.maxCharge, charge) * skill.inactiveEnergy;
        if(this.inactiveEnergy != old) {
            markChanged = true;
        }
    }

    public void setActiveEnergy(int activeEnergy) {
        int old = this.activeEnergy;
        this.activeEnergy = Math.clamp(0, skill.activeEnergy, activeEnergy);
        if(this.activeEnergy != old) {
            markChanged = true;
        }
    }

    public void consumeChange(){
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
