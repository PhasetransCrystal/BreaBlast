package com.phasetranscrystal.blast;


import com.phasetranscrystal.blast.registry.SkillRegistry;
import com.phasetranscrystal.blast.player.SkillGroup;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

@Mod(Blast.MODID)
public class Blast {
    public static final String MODID = "brea_blast";

    public Blast(IEventBus bus) {
        ATTACHMENT.register(bus);
        SkillRegistry.SKILL.register(bus);
    }

    public static ResourceLocation location(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    //PLAYER SKILLS
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Blast.MODID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<SkillGroup>> SKILL_ATTACHMENT =
            ATTACHMENT.register("skill", () -> AttachmentType.builder(SkillGroup::new).serialize(SkillGroup.CODEC).copyOnDeath().build());



}
