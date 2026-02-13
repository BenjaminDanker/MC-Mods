// This mod is a mess and I don't care

package com.silver.atlantis;

import com.silver.atlantis.construct.ConstructCommandManager;
import com.silver.atlantis.construct.ConstructService;
import com.silver.atlantis.cycle.CycleCommandManager;
import com.silver.atlantis.cycle.CycleService;
import com.silver.atlantis.find.FindCommandManager;
import com.silver.atlantis.find.FlatAreaSearchService;
import com.silver.atlantis.heightcap.HeightCapCommandManager;
import com.silver.atlantis.heightcap.HeightCapService;
import com.silver.atlantis.protect.ProtectionBootstrap;
import com.silver.atlantis.protect.ProtectionService;
import com.silver.atlantis.spawn.SpawnCommandManager;
import com.silver.atlantis.spawn.SpecialDropManager;
import com.silver.atlantis.spawn.SpecialItemConversionManager;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AtlantisMod implements ModInitializer {
	public static final String MOD_ID = "atlantis";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private final SpawnCommandManager spawnManager = new SpawnCommandManager();
	private final FlatAreaSearchService flatAreaSearchService = new FlatAreaSearchService();
	private final FindCommandManager findCommandManager = new FindCommandManager(flatAreaSearchService);
	private final ProtectionService protectionService = new ProtectionService();
	private final HeightCapService heightCapService = new HeightCapService();
	private final HeightCapCommandManager heightCapCommandManager = new HeightCapCommandManager(heightCapService);
	private final ConstructService constructService = new ConstructService();
	private final ConstructCommandManager constructCommandManager = new ConstructCommandManager(flatAreaSearchService, constructService);
	private final CycleService cycleService = new CycleService(flatAreaSearchService, constructService, spawnManager);
	private final CycleCommandManager cycleCommandManager = new CycleCommandManager(cycleService);
	private final AtlantisCommandManager atlantisCommandManager = new AtlantisCommandManager(
		findCommandManager,
		constructCommandManager,
		cycleCommandManager,
		spawnManager,
		constructService,
		heightCapCommandManager
	);

	@Override
	public void onInitialize() {
		protectionService.register();
		ProtectionBootstrap.loadAllPersisted();
		heightCapService.register();
		SpecialDropManager.init();
		spawnManager.register();
		SpecialItemConversionManager.init();
		flatAreaSearchService.register();
		constructService.register();
		cycleService.register();
		atlantisCommandManager.register();
		LOGGER.info("Loaded Atlantis structure spawn manager");
		LOGGER.info("Registered Atlantis root command: /atlantis");
	}
}
