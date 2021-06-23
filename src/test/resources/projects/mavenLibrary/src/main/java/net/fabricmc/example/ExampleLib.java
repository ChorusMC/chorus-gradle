package net.fabricmc.example;

import net.fabricmc.api.ModInitializer;

public class ExampleLib implements ModInitializer {
	@Override
	public void onInitialize() {
	}

	public static void hello() {
		System.out.println("Hello Fabric world!");
	}
}
