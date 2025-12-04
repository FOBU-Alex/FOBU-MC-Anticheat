package com.example;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class FOBU_MC_AntiCheatClient implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("FOBU_MC_AntiCheat");

	@Override
	public void onInitializeClient() {
		// Also send whenever packs are changed/reloaded
		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES)
				.registerReloadListener(new SimpleResourceReloadListener<Object>() {
					@Override
					public Identifier getFabricId() {
						return Identifier.of("fobu_mc_anticheat", "pack_reload_listener");
					}

					@Override
					public CompletableFuture<Object> load(ResourceManager manager, Executor executor) {
						// nothing to load, just pass through
						return CompletableFuture.completedFuture(null);
					}

					@Override
					public CompletableFuture<Void> apply(Object data, ResourceManager manager, Executor executor) {
						// This runs after packs reload or F3+T or /reload
						sendPacks();
						return CompletableFuture.completedFuture(null);
					}
				});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			client.execute(() -> {
				List<String> mods = FabricLoader.getInstance()
						.getAllMods()
						.stream()
						.map(mod -> mod.getMetadata().getId().toLowerCase()) // normalize lowercase
						.toList();
				List<String> packs = MinecraftClient.getInstance()
						.getResourcePackManager()
						.getProfiles()
						.stream()
						.map(profile -> profile.getId())           // internal id (usually folder/pack name)
						//.map(profile -> profile.getDisplayName().getString()) // user-facing name
						.map(String::toLowerCase)
						.toList();
				List<String> packs_desc = MinecraftClient.getInstance()
						.getResourcePackManager()
						.getProfiles()
						.stream()
						.map(profile -> profile.getDescription().getString())       // internal id (usually folder/pack name)
						//.map(profile -> profile.getDisplayName().getString()) // user-facing name
						.map(String::toLowerCase)
						.toList();

				List<String> resource_packs = new java.util.ArrayList<>(List.of());
				resource_packs.addAll(packs);
				resource_packs.addAll(packs_desc);

				List<String> mods_final = new java.util.ArrayList<>(List.of());
				mods_final.addAll(mods);
				String version = "unknown";
				Optional<ModContainer> container = FabricLoader.getInstance().getModContainer("fobu_mc_anticheat");
				if (container.isPresent()) {
					ModMetadata meta = container.get().getMetadata();
					version = "fobu_mc_anticheat:" + meta.getVersion().getFriendlyString();
				}
				mods_final.add(version);
				LOGGER.info("FOBU Minecraft Anti Cheat: Version:"+version);


				ClientPlayNetworking.send(new NetworkHandler.ModListPayload(mods_final));
				ClientPlayNetworking.send(new NetworkHandler.PackListPayload(resource_packs));
			});
		});
	}
	private void sendPacks() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) return; // not yet in a world

		List<String> packs = client.getResourcePackManager()
				.getProfiles() // all packs (enabled + disabled)
				.stream()
				.map(profile -> profile.getId()) // or getDisplayName().getString()
				.map(String::toLowerCase)
				.toList();
		List<String> packs_desc = client.getResourcePackManager()
				.getProfiles() // all packs (enabled + disabled)
				.stream()
				.map(profile -> profile.getDescription().getString()) // or getDisplayName().getString()
				.map(String::toLowerCase)
				.toList();
		List<String> resource_packs = new java.util.ArrayList<>(List.of());
		resource_packs.addAll(packs);
		resource_packs.addAll(packs_desc);

		ClientPlayNetworking.send(new NetworkHandler.PackListPayload(resource_packs));
	}
}
