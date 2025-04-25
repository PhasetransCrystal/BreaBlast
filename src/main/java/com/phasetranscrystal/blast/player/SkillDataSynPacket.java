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
                                 Optional<Integer> inactiveEnergy, Optional<Integer> activeEnergy,
                                 Optional<Integer> activeTimes) implements CustomPacketPayload {
    public static final Type<SkillDataSynPacket> TYPE = new Type<>(Blast.location("player_skill_syn"));
    public static final StreamCodec<ByteBuf, SkillDataSynPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(ByteBufCodecs.fromCodec(Registries.SKILL.byNameCodec())), packet -> packet.skill.map(s -> s),
            ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8), SkillDataSynPacket::stage,
            ByteBufCodecs.optional(ByteBufCodecs.VAR_INT), SkillDataSynPacket::inactiveEnergy,
            ByteBufCodecs.optional(ByteBufCodecs.VAR_INT), SkillDataSynPacket::activeEnergy,
            ByteBufCodecs.optional(ByteBufCodecs.VAR_INT), SkillDataSynPacket::activeTimes,
            SkillDataSynPacket::decode
    );

    @SuppressWarnings("all")
    private static SkillDataSynPacket decode(Optional<Skill<?>> skill, Optional<String> stage,
                                             Optional<Integer> inactiveEnergy, Optional<Integer> activeEnergy, Optional<Integer> activeTimes) {
        return new SkillDataSynPacket(skill.map(s -> (Skill<Player>) s), stage, inactiveEnergy, activeEnergy, activeTimes);
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
        public Optional<String> stage = Optional.empty();
        public Optional<Integer> inactiveEnergy = Optional.empty();
        public Optional<Integer> activeEnergy = Optional.empty();
        public Optional<Integer> activeTimes = Optional.empty();


        public void setSkill(Skill<Player> skill) {
            this.skill = Optional.of(skill);
        }

        public void setStage(String stage) {
            this.stage = Optional.of(stage);
        }

        public void setInactiveEnergy(int inactiveEnergy) {
            this.inactiveEnergy = Optional.of(inactiveEnergy);
        }

        public void setActiveEnergy(int activeEnergy) {
            this.activeEnergy = Optional.of(activeEnergy);
        }

        public void setActiveTimes(int activeTimes) {
            this.activeTimes = Optional.of(activeTimes);
        }

        public SkillDataSynPacket build() {
            return new SkillDataSynPacket(skill, stage, inactiveEnergy, activeEnergy, activeTimes);
        }
    }
}
