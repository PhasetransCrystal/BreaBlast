package com.phasetranscrystal.blast.registry;

import com.phasetranscrystal.blast.Blast;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class AttributeRegistry {
    public static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(Registries.ATTRIBUTE, Blast.MODID);

    public static final DeferredHolder<Attribute, RangedAttribute> SKILL_INACTIVE_ENERGY =
            ATTRIBUTES.register("inactive_energy", () -> new RangedAttribute("attribute.name.skill.inactive_energy", 1024, 0, Integer.MAX_VALUE));

    public static final DeferredHolder<Attribute, RangedAttribute> SKILL_ACTIVE_ENERGY =
            ATTRIBUTES.register("active_energy", () -> new RangedAttribute("attribute.name.skill.active_energy", 1024, 0, Integer.MAX_VALUE));

    public static final DeferredHolder<Attribute, RangedAttribute> SKILL_MAX_CHARGE =
            ATTRIBUTES.register("max_charge", () -> new RangedAttribute("attribute.name.skill.max_charge", 1, 1, Integer.MAX_VALUE));

    public static final DeferredHolder<Attribute, RangedAttribute> ENERGY =
            ATTRIBUTES.register("energy", () -> new RangedAttribute("attribute.name.skill.energy", 0, Integer.MIN_VALUE, Integer.MAX_VALUE));
}
