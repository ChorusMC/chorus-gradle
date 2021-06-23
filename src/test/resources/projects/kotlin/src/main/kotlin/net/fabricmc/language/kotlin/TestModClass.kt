package net.fabricmc.language.kotlin

import net.fabricmc.api.ModInitializer
import org.apache.logging.log4j.LogManager

class TestModClass : ModInitializer {

    val logger = LogManager.getFormatterLogger("KotlinLanguageTest")

    override fun onInitialize() {
        logger.info("**************************")
        logger.info("Hello from Kotlin TestModClass")
        logger.info("**************************")
    }
}