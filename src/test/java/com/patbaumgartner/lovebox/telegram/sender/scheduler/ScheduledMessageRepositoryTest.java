package com.patbaumgartner.lovebox.telegram.sender.scheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduledMessageRepositoryTest {

	@TempDir
	Path tempDir;

	private ScheduledMessageRepository repository;

	@BeforeEach
	void setUp() throws Exception {
		Path databasePath = tempDir.resolve("messages.db");
		MessageProperties properties = new MessageProperties();
		properties.setDatabasePath(databasePath.toString());
		repository = new ScheduledMessageRepository(properties);
		try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
				Statement statement = connection.createStatement()) {
			statement.execute("CREATE TABLE messages (send_date TEXT PRIMARY KEY, message TEXT NOT NULL)");
			statement.execute("INSERT INTO messages VALUES ('2026-05-26', '26.05.26\\nSend me')");
		}
	}

	@Test
	void findsOnlyTheMessageForTheExactDate() {
		assertThat(repository.findMessageForDate(LocalDate.of(2026, 5, 26)))
			.contains(new ScheduledMessage(LocalDate.of(2026, 5, 26), "26.05.26\\nSend me"));
		assertThat(repository.findMessageForDate(LocalDate.of(2026, 5, 27))).isEmpty();
	}

}
