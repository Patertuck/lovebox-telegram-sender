package com.patbaumgartner.lovebox.telegram.sender.scheduler;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDate;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ScheduledMessageRepository {

	private final MessageProperties messageProperties;

	public Optional<ScheduledMessage> findMessageForDate(LocalDate sendDate) {
		String sql = """
				SELECT send_date, message
				FROM messages
				WHERE send_date = ?
				""";
		try (Connection connection = openConnection();
				PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setString(1, sendDate.toString());
			try (ResultSet resultSet = statement.executeQuery()) {
				if (resultSet.next()) {
					return Optional.of(new ScheduledMessage(LocalDate.parse(resultSet.getString("send_date")),
							resultSet.getString("message")));
				}
			}
		}
		catch (SQLException e) {
			throw new IllegalStateException("Failed to load scheduled messages from SQLite", e);
		}
		return Optional.empty();
	}

	private Connection openConnection() throws SQLException {
		Path databasePath = Path.of(messageProperties.getDatabasePath()).toAbsolutePath().normalize();
		return DriverManager.getConnection("jdbc:sqlite:" + databasePath);
	}

}
