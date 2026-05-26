package com.patbaumgartner.lovebox.telegram.sender.scheduler;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class ScheduledMessageRepository {

	private static final DateTimeFormatter SOURCE_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yy");

	private final SchedulerIntegrationProperties integrationProperties;

	public List<ScheduledMessage> findPendingMessagesForSourceDate(LocalDate sourceDate) {
		String sql = """
				SELECT id, body
				FROM messages
				WHERE status = ? AND source_date = ?
				ORDER BY id ASC
				""";
		List<ScheduledMessage> messages = new ArrayList<>();
		try (Connection connection = openConnection();
				PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setString(1, "pending");
			statement.setString(2, SOURCE_DATE_FORMATTER.format(sourceDate));
			try (ResultSet resultSet = statement.executeQuery()) {
				while (resultSet.next()) {
					messages.add(new ScheduledMessage(resultSet.getLong("id"), resultSet.getString("body")));
				}
			}
		}
		catch (SQLException e) {
			throw new IllegalStateException("Failed to load scheduled messages from SQLite", e);
		}
		return messages;
	}

	public void markSent(long id, OffsetDateTime sentAt) {
		updateMessageState(id, "sent", sentAt, sentAt, null);
	}

	public void markPendingWithError(long id, OffsetDateTime updatedAt, String error) {
		updateMessageState(id, "pending", null, updatedAt, abbreviateError(error));
	}

	private void updateMessageState(long id, String status, OffsetDateTime sentAt, OffsetDateTime updatedAt,
			String error) {
		String sql = """
				UPDATE messages
				SET status = ?, sent_at = ?, error = ?, updated_at = ?
				WHERE id = ?
				""";
		try (Connection connection = openConnection();
				PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setString(1, status);
			statement.setString(2, sentAt == null ? null : sentAt.toString());
			statement.setString(3, error);
			statement.setString(4, updatedAt.toString());
			statement.setLong(5, id);
			statement.executeUpdate();
		}
		catch (SQLException e) {
			throw new IllegalStateException("Failed to update scheduled message state in SQLite", e);
		}
	}

	private Connection openConnection() throws SQLException {
		Path databasePath = Path.of(integrationProperties.getMessagesDbPath()).toAbsolutePath().normalize();
		return DriverManager.getConnection("jdbc:sqlite:" + databasePath);
	}

	private String abbreviateError(String error) {
		if (error == null || error.length() <= 500) {
			return error;
		}
		return error.substring(0, 500);
	}

}
