package com.silver.atlantis;

import com.silver.atlantis.spawn.SpawnCommandManager;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AtlantisMod implements ModInitializer {
	public static final String MOD_ID = "atlantis";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private final SpawnCommandManager spawnManager = new SpawnCommandManager();

	@Override
	public void onInitialize() {
		spawnManager.register();
		LOGGER.info("Loaded Atlantis structure spawn manager");
	}
}
