package net.fabricmc.examplemod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.example.ExampleLib;

public class ExampleMod implements ModInitializer {
	@Override
	public void onInitialize() {
		// Lets make sure we can compile against the lib
		ExampleLib.hello();
	}
}
