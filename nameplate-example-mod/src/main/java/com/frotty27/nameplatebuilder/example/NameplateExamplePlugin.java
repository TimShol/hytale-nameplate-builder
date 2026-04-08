package com.frotty27.nameplatebuilder.example;

import com.frotty27.nameplatebuilder.api.NameplateAPI;
import com.frotty27.nameplatebuilder.api.NameplateData;
import com.frotty27.nameplatebuilder.api.SegmentTarget;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.List;

public final class NameplateExamplePlugin extends JavaPlugin {

    public NameplateExamplePlugin(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        NameplateAPI.define(this, "level", "Level", SegmentTarget.ALL, "Lv. 42");
        NameplateAPI.defineVariants(this, "level", List.of("Compact", "Full", "Number Only"));
        NameplateAPI.define(this, "title", "Title", SegmentTarget.ALL, "The Brave");
        NameplateAPI.define(this, "custom-tag", "Custom Tag", SegmentTarget.ALL, "[Custom]");

        NameplateAPI.define(this, "buff", "Active Buff", SegmentTarget.NPCS, "Burning");
        NameplateAPI.define(this, "faction", "Faction", SegmentTarget.NPCS, "<Undead>");
        NameplateAPI.define(this, "mood", "Mood", SegmentTarget.NPCS, "Aggressive");
        NameplateAPI.define(this, "quest", "Active Quest", SegmentTarget.NPCS, "[!!] Slay 10");
        NameplateAPI.define(this, "bounty", "Bounty", SegmentTarget.NPCS, "500g");
        NameplateAPI.define(this, "lifetime", "Lifetime", SegmentTarget.NPCS, "3m 24s");

        NameplateAPI.define(this, "guild", "Guild Tag", SegmentTarget.PLAYERS, "[Warriors]");
        NameplateAPI.define(this, "score", "Score", SegmentTarget.PLAYERS, "1,250");
        NameplateAPI.define(this, "rank", "Server Rank", SegmentTarget.PLAYERS, "VIP+");
        NameplateAPI.define(this, "clan", "Clan", SegmentTarget.PLAYERS, "<Phoenix>");
        NameplateAPI.define(this, "vip-label", "VIP Label", SegmentTarget.PLAYERS, "VIP");

        ComponentType<EntityStore, NameplateData> nameplateDataType = NameplateAPI.getComponentType();
        getEntityStoreRegistry().registerSystem(new ArchaeopteryxNameplateSystem(nameplateDataType));
        getEntityStoreRegistry().registerSystem(new LifetimeNameplateSystem(nameplateDataType));
    }

    public void initializeEntity(Store<EntityStore> store, Ref<EntityStore> entityRef) {
        NameplateAPI.setText(store, entityRef, "health", "67/67");
        NameplateAPI.setText(store, entityRef, "guild", "[Warriors]");
        NameplateAPI.setText(store, entityRef, "title", "The Brave");
        NameplateAPI.setText(store, entityRef, "rank", "VIP");
        NameplateAPI.setText(store, entityRef, "lifetime", "0s");
    }

    public void markElite(Store<EntityStore> store, Ref<EntityStore> entityRef) {
        NameplateAPI.setText(store, entityRef, "tier", "[Elite]");
    }

    public void removeElite(Store<EntityStore> store, Ref<EntityStore> entityRef) {
        NameplateAPI.clearText(store, entityRef, "tier");
    }

    public void updateHealth(Store<EntityStore> store, Ref<EntityStore> entityRef, int current, int max) {
        NameplateAPI.setText(store, entityRef, "health", current + "/" + max);
    }

    public void applyBossOverrides(Store<EntityStore> store, Ref<EntityStore> entityRef) {
        NameplateAPI.setText(store, entityRef, "health", "1000/1000");
        NameplateAPI.setText(store, entityRef, "tier", "[World Boss]");
        NameplateAPI.setText(store, entityRef, "quest", "[!!] Final Boss");
    }

    public void removeBuff(Store<EntityStore> store, Ref<EntityStore> entityRef) {
        NameplateAPI.clearText(store, entityRef, "buff");
    }

    public void clearAll(Store<EntityStore> store, Ref<EntityStore> entityRef) {
        store.tryRemoveComponent(entityRef, NameplateAPI.getComponentType());
    }
}
