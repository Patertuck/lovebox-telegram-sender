package com.patbaumgartner.lovebox.telegram.sender.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

	static final String PROPERTY_SOURCE_NAME = "dotenv";

	private final Supplier<Path> dotenvPathSupplier;

	public DotenvEnvironmentPostProcessor() {
		this(() -> Path.of(System.getProperty("user.dir"), ".env"));
	}

	DotenvEnvironmentPostProcessor(Supplier<Path> dotenvPathSupplier) {
		this.dotenvPathSupplier = dotenvPathSupplier;
	}

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		Path dotenvPath = this.dotenvPathSupplier.get();
		Map<String, Object> properties = loadProperties(dotenvPath);
		if (properties.isEmpty()) {
			return;
		}

		MutablePropertySources propertySources = environment.getPropertySources();
		MapPropertySource propertySource = new MapPropertySource(PROPERTY_SOURCE_NAME, properties);
		if (propertySources.contains(PROPERTY_SOURCE_NAME)) {
			propertySources.replace(PROPERTY_SOURCE_NAME, propertySource);
			return;
		}

		if (propertySources.contains(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) {
			propertySources.addAfter(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, propertySource);
			return;
		}

		propertySources.addFirst(propertySource);
	}

	private Map<String, Object> loadProperties(Path dotenvPath) {
		if (!Files.isRegularFile(dotenvPath)) {
			return Map.of();
		}

		try {
			return parseLines(Files.readAllLines(dotenvPath));
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to read .env file from " + dotenvPath, ex);
		}
	}

	static Map<String, Object> parseLines(List<String> lines) {
		Map<String, Object> properties = new LinkedHashMap<>();
		for (String rawLine : lines) {
			String line = rawLine.trim();
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}

			int separatorIndex = line.indexOf('=');
			if (separatorIndex <= 0) {
				continue;
			}

			String key = line.substring(0, separatorIndex).trim();
			String value = line.substring(separatorIndex + 1).trim();
			properties.put(key, stripMatchingQuotes(value));
		}
		return properties;
	}

	private static String stripMatchingQuotes(String value) {
		if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
			return value.substring(1, value.length() - 1);
		}
		return value;
	}

	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE + 20;
	}

}
