package com.phasetranscrystal.blast.player;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.phasetranscrystal.blast.Registries;
import com.phasetranscrystal.blast.registry.SkillRegistry;
import com.phasetranscrystal.blast.skill.Skill;
import com.phasetranscrystal.blast.skill.SkillData;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import java.util.*;

public class SkillGroup {
    public static final Logger LOGGER = LogManager.getLogger("BreaBlast:Skill/Group");
    public static final Codec<SkillGroup> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Registries.SKILL.byNameCodec().orElse(null).listOf().fieldOf("allowed").forGetter(i -> (List<Skill<?>>) ((Object) i.unlockedSkills.stream().toList())),
            SkillData.CODEC.fieldOf("data").orElse(new SkillData<>(SkillRegistry.EMPTY.get())).forGetter(s -> s.currentSkill)
    ).apply(instance, SkillGroup::new));

    private final Set<Skill<Player>> unlockedSkills = new HashSet<>();
    private @Nonnull SkillData<Player> currentSkill;
    private ServerPlayer player;

    private boolean changed = true;

    //infoCaches don't change
    private Skill<Player> skillCache;
    private String stageCache;
    private int energyCache = 0;
    private int maxStageEnergyCache = 0;
    private int maxChargeCache = 0;
    private int activeTimesCache = 0;

    public SkillGroup() {
        this.currentSkill = new SkillData<>((Skill<Player>) SkillRegistry.EMPTY.get());
    }

    @SuppressWarnings("unchecked")
    private SkillGroup(List<Skill<?>> allowed, SkillData<?> data) {
        allowed.stream().filter(s -> s != null && s.bindingEntityClass.isAssignableFrom(Player.class)).map(s -> (Skill<Player>) s).forEach(unlockedSkills::add);
        this.currentSkill = (SkillData<Player>) data;
    }

    public void bindEntity(ServerPlayer entity) {
        if (this.player != null && !entity.getUUID().equals(this.player.getUUID())) {
            LOGGER.warn("Player instance (class={}) already exists. Skipped.", entity.getClass());
            return;
        }
        changed = true;

        this.player = entity;
        currentSkill.bindEntity(entity);
    }

    public Skill<Player> getCurrentSkill() {
        return currentSkill.skill;
    }

    public SkillData<Player> getCurrentSkillData() {
        return currentSkill;
    }

    public boolean changeToEmpty() {
        boolean flag = currentSkill.requestDisable();
        currentSkill = new SkillData<>((Skill<Player>) SkillRegistry.EMPTY.get());
        changed = true;
        return flag;
    }

    public boolean changeTo(Skill<?> skill) {
        if (skill == null || !unlockedSkills.contains(skill) || currentSkill.skill == skill)
            return false;
        currentSkill.requestDisable();
        currentSkill = new SkillData<>((Skill<Player>) skill);
        if (this.player != null) {
            this.currentSkill.bindEntity(this.player);
        }
        changed = true;
        return true;
    }

    //Client only
    protected void consumeSynPacket(SkillDataSynPacket packet) {
        packet.skill().ifPresent(s -> this.skillCache = s);
        packet.stage().ifPresent(s -> this.stageCache = s);
        packet.energy().ifPresent(e -> this.energyCache = e);
        packet.maxStageEnergy().ifPresent(e -> this.maxStageEnergyCache = e);
        packet.maxCharge().ifPresent(e -> this.maxChargeCache = e);
        packet.activeTimes().ifPresent(e -> this.activeTimesCache = e);
    }

    protected void consumeInputPacket(KeyInputPacket packet) {
        int compoundKey = packet.var() & 0x7FFF;
        if (currentSkill.skill.keys.contains(compoundKey))
            currentSkill.skill.keyChange.accept(currentSkill, packet);
    }

    public boolean unlock(Holder<Skill<?>> holder) {
        return unlock(holder.value());
    }

    public boolean unlock(Skill<?> skill) {
        if (!unlockedSkills.contains(skill) && skill.bindingEntityClass.isAssignableFrom(Player.class)) {
            unlockedSkills.add((Skill<Player>) skill);
            return true;
        }
        return false;
    }

    public boolean lock(Holder<Skill<?>> holder) {
        return lock(holder.value());
    }

    public boolean lock(Skill<?> skill) {
        return unlockedSkills.remove(skill);
    }


    //ONLY CACHE
    public Skill<Player> getSkillCache() {
        return skillCache;
    }

    public String getStageCache() {
        return stageCache;
    }

    public int getEnergyCache() {
        return energyCache;
    }

    public int getMaxStageEnergyCache() {
        return maxStageEnergyCache;
    }

    public int getMaxChargeCache() {
        return maxChargeCache;
    }

    public int getActiveTimesCache() {
        return activeTimesCache;
    }

    protected void tick() {
        if (player == null || !changed && !currentSkill.isChanged()) return;

        changed = false;
        currentSkill.consumeChanged();

        SkillDataSynPacket.Mutable mutable = new SkillDataSynPacket.Mutable();
        Skill<Player> skill = currentSkill.skill;
        if (skill.equals(skillCache)) {
            mutable.setSkill(skill);
            this.skillCache = skill;
        }
        String stage = currentSkill.getBehaviorName();
        if (stage.equals(stageCache)) {
            mutable.setBehaviorName(stage);
            this.stageCache = stage;
        }
        int energy = currentSkill.getEnergy();
        if (energyCache != energy) {
            mutable.setEnergy(energy);
            this.energyCache = energy;
        }
        int maxStageEnergy = currentSkill.getMaxStageEnergy();
        if (maxStageEnergyCache != maxStageEnergy) {
            mutable.setMaxStageEnergy(maxStageEnergy);
            this.maxStageEnergyCache = maxStageEnergy;
        }
        int maxCharge = currentSkill.getMaxCharge();
        if (maxChargeCache != maxCharge) {
            mutable.setMaxCharge(maxCharge);
            this.maxChargeCache = maxCharge;
        }
        int activeTimes = currentSkill.getActiveTimes();
        if (activeTimesCache != activeTimes) {
            mutable.setActiveTimes(activeTimes);
            this.activeTimesCache = activeTimes;
        }

        PacketDistributor.sendToPlayer(player, mutable.build());

    }

}
