package com.phasetranscrystal.blast.player;

import com.phasetranscrystal.blast.Blast;
import com.phasetranscrystal.blast.Registries;
import com.phasetranscrystal.blast.skill.Skill;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Optional;

public record SkillDataSynPacket(Optional<Skill<Player>> skill,
                                 Optional<String> stage,
                                 Optional<Integer> energy, Optional<Integer> maxStageEnergy,
                                 Optional<Integer> maxCharge,
                                 Optional<Integer> activeTimes) implements CustomPacketPayload {
    public static final Type<SkillDataSynPacket> TYPE = new Type<>(Blast.location("player_skill_syn"));
    public static final StreamCodec<ByteBuf, SkillDataSynPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(ByteBufCodecs.fromCodec(Registries.SKILL.byNameCodec())), packet -> packet.skill.map(s -> s),
            ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8), SkillDataSynPacket::stage,
            ByteBufCodecs.optional(ByteBufCodecs.VAR_INT), SkillDataSynPacket::energy,
            ByteBufCodecs.optional(ByteBufCodecs.VAR_INT), SkillDataSynPacket::maxStageEnergy,
            ByteBufCodecs.optional(ByteBufCodecs.VAR_INT), SkillDataSynPacket::maxCharge,
            ByteBufCodecs.optional(ByteBufCodecs.VAR_INT), SkillDataSynPacket::activeTimes,
            SkillDataSynPacket::decode
    );

    @SuppressWarnings("all")
    private static SkillDataSynPacket decode(Optional<Skill<?>> skill, Optional<String> stage,
                                             Optional<Integer> energy, Optional<Integer> maxStageEnergy,Optional<Integer> maxCharge, Optional<Integer> activeTimes) {
        return new SkillDataSynPacket(skill.map(s -> (Skill<Player>) s), stage, energy, maxStageEnergy, maxCharge, activeTimes);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    //Client Only
    public static void consume(SkillDataSynPacket packet, IPayloadContext context) {
        context.player().getData(Blast.SKILL_ATTACHMENT).consumeSynPacket(packet);
    }

    public static class Mutable {
        public Optional<Skill<Player>> skill = Optional.empty();
        public Optional<String> behaviorName = Optional.empty();
        public Optional<Integer> energy = Optional.empty();
        public Optional<Integer> maxStageEnergy = Optional.empty();
        public Optional<Integer> maxCharge = Optional.empty();
        public Optional<Integer> activeTimes = Optional.empty();


        public void setSkill(Skill<Player> skill) {
            this.skill = Optional.of(skill);
        }

        public void setBehaviorName(String behaviorName) {
            this.behaviorName = Optional.of(behaviorName);
        }

        public void setEnergy(int energy) {
            this.energy = Optional.of(energy);
        }

        public void setMaxStageEnergy(int maxStageEnergy) {
            this.maxStageEnergy = Optional.of(maxStageEnergy);
        }

        public void setMaxCharge(int maxCharge) {
            this.maxCharge = Optional.of(maxCharge);
        }

        public void setActiveTimes(int activeTimes) {
            this.activeTimes = Optional.of(activeTimes);
        }

        public SkillDataSynPacket build() {
            return new SkillDataSynPacket(skill, behaviorName, energy, maxStageEnergy, maxCharge, activeTimes);
        }
    }
}
