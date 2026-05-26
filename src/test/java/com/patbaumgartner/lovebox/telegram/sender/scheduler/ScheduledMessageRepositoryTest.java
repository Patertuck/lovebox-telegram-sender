package com.patbaumgartner.lovebox.telegram.sender.scheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduledMessageRepositoryTest {

	@TempDir
	Path tempDir;

	private Path databasePath;

	private ScheduledMessageRepository repository;

	@BeforeEach
	void setUp() throws Exception {
		databasePath = tempDir.resolve("messages.db");
		SchedulerIntegrationProperties properties = new SchedulerIntegrationProperties();
		properties.setMessagesDbPath(databasePath.toString());
		repository = new ScheduledMessageRepository(properties);

		try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
				Statement statement = connection.createStatement()) {
			statement.execute("""
					CREATE TABLE messages (
					    id INTEGER PRIMARY KEY AUTOINCREMENT,
					    source_doc TEXT NOT NULL,
					    source_date TEXT NOT NULL,
					    source_key TEXT NOT NULL UNIQUE,
					    scheduled_at TEXT NOT NULL,
					    body TEXT NOT NULL,
					    status TEXT NOT NULL,
					    created_at TEXT NOT NULL,
					    updated_at TEXT NOT NULL,
					    sent_at TEXT,
					    error TEXT
					)
					""");
			statement.execute("""
					INSERT INTO messages
					    (source_doc, source_date, source_key, scheduled_at, body, status, created_at, updated_at, sent_at, error)
					VALUES
					    ('doc', '26.05.25', 'key-1', '2026-05-26T18:00:00+02:00', 'send me', 'pending', '2026-05-26T09:44:00+02:00', '2026-05-26T09:44:00+02:00', NULL, NULL),
					    ('doc', '26.05.25', 'key-2', '2026-05-26T18:00:00+02:00', 'already sent', 'sent', '2026-05-26T09:44:00+02:00', '2026-05-26T09:44:00+02:00', '2026-05-26T18:00:00+02:00', NULL),
					    ('doc', '27.05.25', 'key-3', '2026-05-27T18:00:00+02:00', 'wrong date', 'pending', '2026-05-26T09:44:00+02:00', '2026-05-26T09:44:00+02:00', NULL, NULL)
					""");
		}
	}

	@Test
	void findsOnlyPendingMessagesForMatchingSourceDate() {
		List<ScheduledMessage> result = repository.findPendingMessagesForSourceDate(LocalDate.of(2025, 5, 26));

		assertThat(result).containsExactly(new ScheduledMessage(1L, "send me"));
	}

	@Test
	void marksSentMessages() throws Exception {
		OffsetDateTime sentAt = OffsetDateTime.parse("2026-05-26T18:00:00+02:00");

		repository.markSent(1L, sentAt);

		try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
				Statement statement = connection.createStatement();
				var resultSet = statement.executeQuery("SELECT status, sent_at, error FROM messages WHERE id = 1")) {
			assertThat(resultSet.next()).isTrue();
			assertThat(resultSet.getString("status")).isEqualTo("sent");
			assertThat(resultSet.getString("sent_at")).isEqualTo("2026-05-26T18:00+02:00");
			assertThat(resultSet.getString("error")).isNull();
		}
	}

	@Test
	void storesFailureAndKeepsMessagePending() throws Exception {
		OffsetDateTime updatedAt = OffsetDateTime.parse("2026-05-26T18:00:00+02:00");

		repository.markPendingWithError(1L, updatedAt, "boom");

		try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
				Statement statement = connection.createStatement();
				var resultSet = statement
					.executeQuery("SELECT status, sent_at, error, updated_at FROM messages WHERE id = 1")) {
			assertThat(resultSet.next()).isTrue();
			assertThat(resultSet.getString("status")).isEqualTo("pending");
			assertThat(resultSet.getString("sent_at")).isNull();
			assertThat(resultSet.getString("error")).isEqualTo("boom");
			assertThat(resultSet.getString("updated_at")).isEqualTo("2026-05-26T18:00+02:00");
		}
	}

}
