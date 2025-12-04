package com.example;


		import com.mojang.brigadier.arguments.BoolArgumentType;
		import com.mojang.brigadier.arguments.IntegerArgumentType;
		import com.mojang.brigadier.arguments.StringArgumentType;
		import net.fabricmc.api.EnvType;
		import net.fabricmc.api.ModInitializer;
		import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
		import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
		import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
		import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
		import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
		import net.fabricmc.loader.api.FabricLoader;
		import net.fabricmc.loader.api.ModContainer;
		import net.fabricmc.loader.api.metadata.ModMetadata;
		import net.minecraft.server.command.CommandManager;
		import net.minecraft.server.command.ServerCommandSource;
		import net.minecraft.server.network.ServerPlayerEntity;
		import net.minecraft.text.Text;
		import org.slf4j.Logger;
		import org.slf4j.LoggerFactory;

		import java.util.Random;
		import java.io.IOException;
		import java.nio.file.Files;
		import java.nio.file.Path;
		import java.util.*;
		import java.util.stream.Collectors;
		import net.minecraft.util.Identifier;



		import static net.minecraft.server.command.CommandManager.*;

public class FOBU_MC_AntiCheat implements ModInitializer {
	public static boolean spam = true; // default value

	private final Set<UUID> verifiedPlayers = new HashSet<>();
	private final Map<UUID, Integer> pendingChecks = new HashMap<>();

	private static List<String> BANNED_MODS = List.of();
	private static List<String> BANNED_PACKS = List.of();
	private static List<String> KICK_MESSAGE = List.of();

	public static final Logger LOGGER = LoggerFactory.getLogger("FOBU_MC_AntiCheat");

	void info(String text) {
		if(spam) LOGGER.info(text);
	}
	void warn(String text) {
		if(spam) LOGGER.warn(text);
	}
	@Override
	public void onInitialize() {
		commands();
		if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT)
			LOGGER.info("FOBU Minecraft Anti Cheat: Hello User!");

		String version_ = "fobu_mc_anticheat: unknown";
		Optional<ModContainer> container_ = FabricLoader.getInstance().getModContainer("fobu_mc_anticheat");
		if (container_.isPresent()) {
			ModMetadata meta = container_.get().getMetadata();
			version_ = "fobu_mc_anticheat: "+meta.getVersion().getFriendlyString();
		}
		LOGGER.info("FOBU Minecraft Anti Cheat: Version: "+version_);


		// Register for client->server communication
		PayloadTypeRegistry.playC2S().register(NetworkHandler.ModListPayload.ID, NetworkHandler.ModListPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(NetworkHandler.PackListPayload.ID, NetworkHandler.PackListPayload.CODEC);
		if (FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER) {
			LOGGER.info("FOBU Minecraft Anti Cheat: ACTIVE------------------------------------------------------------------------------------------------");
			LOGGER.info("FOBU Minecraft Anti Cheat: Loading Kick Message...");
			loadKickMessage();
			LOGGER.info("FOBU Minecraft Anti Cheat: Loading Banned Mod file list...");
			loadBannedMods();
			LOGGER.info("FOBU Minecraft Anti Cheat: Loading Banned Pack file list...");
			loadBannedPacks();
			LOGGER.info("FOBU Minecraft Anti Cheat: DONE! Waiting for Players to join...");

			// On join → schedule check (5 seconds later = 100 ticks)
			ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {

				ServerPlayerEntity player = handler.player;
				pendingChecks.put(player.getUuid(), 150); // wait 100 ticks = 7.5 Sec
			});


			// On disconnect → cleanup
			ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
				UUID id = handler.player.getUuid();
				verifiedPlayers.remove(id);
				pendingChecks.remove(id);
			});

			// Tick loop for countdowns
			ServerTickEvents.END_SERVER_TICK.register(server -> {
				// decrement counters
				pendingChecks.replaceAll((uuid, ticksLeft) -> ticksLeft - 1);

				// collect players to kick
				Set<UUID> toRemove = new HashSet<>();
				for (var entry : pendingChecks.entrySet()) {
					if (entry.getValue() <= 0) {
						ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
						if (player != null && !verifiedPlayers.contains(entry.getKey())) {
							player.networkHandler.disconnect(Text.of("Missing required mod: fobu_mc_anticheat\n" +
									"\nOr just try to rejoin\n(It happens because of system against players who don't have the Anticheat)"));

						}
						toRemove.add(entry.getKey());
					}
				}

				// remove after iteration
				for (UUID id : toRemove) {
					pendingChecks.remove(id);
				}
			});
			ServerPlayNetworking.registerGlobalReceiver(
					NetworkHandler.ModListPayload.ID,
					(payload, context) -> {
						context.server().execute(() -> {
							info("FOBU Minecraft Anti Cheat: Player joined: " + context.player().getName().getString()+" - "+context.player().getUuidAsString());

							info("FOBU Minecraft Anti Cheat: START CHECKING BANNED MOD ELEMENTS-------------------------------------------------------------------------------------");
							List<String> mods = payload.mods();

							String version = "fobu_mc_anticheat: unknown";
							Optional<ModContainer> container = FabricLoader.getInstance().getModContainer("fobu_mc_anticheat");
							if (container.isPresent()) {
								ModMetadata meta = container.get().getMetadata();
								version = "fobu_mc_anticheat:"+meta.getVersion().getFriendlyString();
							}
							boolean exists = false;
							String user_version = "---";


							for (String users_mod : mods) {
								if(users_mod.contains("fobu_mc_anticheat:")) {
									exists = true;
									info("FOBU Minecraft Anti Cheat: "+context.player().getName().getString()+" Anticheat Version: "+users_mod);
									user_version = users_mod;
								}
								info("FOBU Minecraft Anti Cheat: "+context.player().getName().getString()+" Mod: "+users_mod);
								for(String banned_element : BANNED_MODS) {
									if(users_mod.toLowerCase().contains(banned_element)) {
										Random rand = new Random();
										context.player().networkHandler.disconnect(
												Text.of("This Mod is incompatible with the Server: " + users_mod+"\n"+
														KICK_MESSAGE.get(rand.nextInt(0,KICK_MESSAGE.size()))));
										LOGGER.warn("FOBU Minecraft Anti Cheat: User "+context.player().getName().getString()+" have an SUS mod: "+users_mod);
										info("FOBU Minecraft Anti Cheat: KICKED-------------------------------------------------------------------------------------");
										return;
									}
								}
							}
							if(!exists) context.player().networkHandler.disconnect(
									Text.of("FOBU Minecraft Anti Cheat is missing!"));
							else if(!user_version.equals(version)) context.player().networkHandler.disconnect(
									Text.of("FOBU Minecraft Anti Cheat is the wrong version!\n You need: "+version));
							else {
								//info("=========PLAYER VERIFIED: "+context.player().getUuid());
								verifiedPlayers.add(context.player().getUuid());
								pendingChecks.remove(context.player().getUuid()); // no need to kick
							}

							info("FOBU Minecraft Anti Cheat: END-------------------------------------------------------------------------------------");
						});
					});
			ServerPlayNetworking.registerGlobalReceiver(NetworkHandler.PackListPayload.ID,
					(payload, context) -> context.server().execute(() -> {
						info("START CHECKING BANNED PACK ELEMENTS-------------------------------------------------------------------------------------");
						info("FOBU Minecraft Anti Cheat: Player joined: " + context.player().getName().getString());

						List<String> packs = payload.packs().stream().map(String::toLowerCase).toList();
						//LOGGER.info("User resource packs: {}", packs);

						for (String users_pack : packs) {
							info("FOBU Minecraft Anti Cheat: Players Pack: "+users_pack);
							for(String banned_element : BANNED_PACKS) {
								if(users_pack.toLowerCase().contains(banned_element)) {
									Random rand = new Random();
									context.player().networkHandler.disconnect(
											Text.of("This ResourcePack is incompatible with the Server: " + users_pack+"\n"+
													KICK_MESSAGE.get(rand.nextInt(0,KICK_MESSAGE.size()))));
									LOGGER.warn("FOBU Minecraft Anti Cheat: User "+context.player().getName().getString()+" have an SUS pack: "+users_pack);
									info("KICKED-------------------------------------------------------------------------------------");
									return;
								}
							}
						}
						info("END-------------------------------------------------------------------------------------");
					}));
		}
	}
	private void loadBannedMods() {
		Path configDir = FabricLoader.getInstance()
				.getConfigDir()
				.resolve("fobu_mc_anticheat");

		Path file = configDir.resolve("banned_mods.txt");

		try {
			if (!Files.exists(configDir)) {
				Files.createDirectories(configDir);
			}
			if (!Files.exists(file)) {
				// create default file
				Files.writeString(file, "example1\nexample2\nexample3\n");
			}

			BANNED_MODS = Files.readAllLines(file).stream()
					.map(String::trim)
					.filter(s -> !s.isEmpty() && !s.startsWith("#"))
					.map(String::toLowerCase)
					.collect(Collectors.toList());

			LOGGER.info("FOBU Minecraft Anti Cheat: Loaded ban mod elements: {}", BANNED_MODS);
		} catch (IOException e) {
			LOGGER.error("Failed to load banned_mods.txt", e);
			BANNED_MODS = Collections.emptyList();
		}
	}
	private void loadBannedPacks() {
		Path configDir = FabricLoader.getInstance()
				.getConfigDir()
				.resolve("fobu_mc_anticheat");

		Path file = configDir.resolve("banned_packs.txt");

		try {
			if (!Files.exists(configDir)) {
				Files.createDirectories(configDir);
			}
			if (!Files.exists(file)) {
				// create default file
				Files.writeString(file, "example1\nexample2\nexample3\n");
			}

			BANNED_PACKS = Files.readAllLines(file).stream()
					.map(String::trim)
					.filter(s -> !s.isEmpty() && !s.startsWith("#"))
					.map(String::toLowerCase)
					.collect(Collectors.toList());

			LOGGER.info("FOBU Minecraft Anti Cheat: Loaded ban pack elements: {}", BANNED_PACKS);
		} catch (IOException e) {
			LOGGER.error("Failed to load banned_packs.txt", e);
			BANNED_PACKS = Collections.emptyList();
		}
	}
	private void loadKickMessage() {
		Path configDir = FabricLoader.getInstance()
				.getConfigDir()
				.resolve("fobu_mc_anticheat");

		Path file = configDir.resolve("kick_message.txt");

		try {
			if (!Files.exists(configDir)) {
				Files.createDirectories(configDir);
			}
			if (!Files.exists(file)) {
				// create default file
				Files.writeString(file, "Some Mods or Texture packs can be falsely marked as banned\nContact an Admin about this, be ready to show the problematic mod or texture pack");
				/*
				 ---
				 sad, just sad.
				 No no no Mister Fish, you're not allowed to cheat, only Admins do!
				 SUS\nAMOGUS?
				 (-_-)
				 Pray
				 :)
				 :|
				 :(

				 */

			}

			KICK_MESSAGE = Files.readAllLines(file).stream()
					.map(String::trim)
					.filter(s -> !s.isEmpty() && !s.startsWith("#"))
					.map(String::toLowerCase)
					.collect(Collectors.toList());

			LOGGER.info("FOBU Minecraft Anti Cheat: Kick Message: {}", KICK_MESSAGE);
		} catch (IOException e) {
			LOGGER.error("Failed to load banned_packs.txt", e);
			KICK_MESSAGE = Collections.emptyList();
		}
	}
	public static void commands() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(
					literal("FOBU_Spam") // Toggle the amount of information the anti-cheat writes to console:
							.then(argument("value", BoolArgumentType.bool())
									.executes(context -> {
										ServerCommandSource source = context.getSource();

										// Only allow console
										if (source.getEntity() != null) {
											source.sendFeedback(() -> Text.literal("This command can only be run from the server console."), false);
											return 0;
										}

										boolean newValue = BoolArgumentType.getBool(context, "value");
										com.example.FOBU_MC_AntiCheat.spam = newValue;

										source.sendFeedback(() -> Text.literal("FOBU Spam toggled to: " + newValue), false);
										return 1;
									})
							)
			);
		});
	}
	/*
	void commands() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

			// /hello
			dispatcher.register(
					literal("FOBU_Spam") // works now
							.then(argument("value", BoolArgumentType.bool()))
								.executes(context -> {
									LOGGER.info("FOBU Minecraft Anti Cheat: Command Executed!");
									ServerCommandSource source = context.getSource();

									// Only allow console (no player entity)
									if (source.getEntity() != null) {
										source.sendFeedback(() -> Text.literal("This command can only be run from the server console."), false);
										return 0;
									}

									Objects.requireNonNull(context.getSource().getPlayer().getServer()).getCommandManager().executeWithPrefix(context.getSource().getPlayer().getServer().getCommandSource(),
											"msg @a lol");

									boolean newValue = BoolArgumentType.getBool(context, "value");
									FOBUMinecraftAntiCheat.spam = newValue;

									source.sendFeedback(() -> Text.literal("allowPlayerJoin set to " + newValue), false);
									return 1;
								})
			);

		});
	}*/
}
