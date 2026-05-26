package com.patbaumgartner.lovebox.telegram.sender.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DotenvEnvironmentPostProcessorTest {

	@Test
	void parsesQuotedValuesAndSkipsComments() {
		assertThat(DotenvEnvironmentPostProcessor.parseLines(List.of("# comment", "", "BOT_TOKEN=\"abc123\"",
				"BOT_USERNAME=bot_name", "MALFORMED", "BOT_ALLOWED_CHAT_ID=\"8782720476\"")))
			.containsEntry("BOT_TOKEN", "abc123")
			.containsEntry("BOT_USERNAME", "bot_name")
			.containsEntry("BOT_ALLOWED_CHAT_ID", "8782720476")
			.doesNotContainKey("MALFORMED");
	}

	@Test
	void loadsDotenvBelowSystemEnvironmentButAboveApplicationProperties(@TempDir Path tempDir) throws Exception {
		Path dotenvFile = tempDir.resolve(".env");
		Files.writeString(dotenvFile, "BOT_TOKEN=\"dotenv-token\"\nBOT_USERNAME=dotenv-bot\n");

		ConfigurableEnvironment environment = new StandardEnvironment();
		environment.getPropertySources().addLast(new MapPropertySource("applicationConfig: [classpath:/application.properties]",
				java.util.Map.of("BOT_TOKEN", "app-token", "BOT_USERNAME", "app-bot")));

		new DotenvEnvironmentPostProcessor(() -> dotenvFile).postProcessEnvironment(environment, new SpringApplication());

		assertThat(environment.getProperty("BOT_TOKEN")).isEqualTo("dotenv-token");
		assertThat(environment.getProperty("BOT_USERNAME")).isEqualTo("dotenv-bot");
		assertThat(environment.getPropertySources().contains(DotenvEnvironmentPostProcessor.PROPERTY_SOURCE_NAME)).isTrue();
	}

}
