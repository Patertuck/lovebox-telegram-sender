package com.patbaumgartner.lovebox.telegram.sender.outbox;

import com.patbaumgartner.lovebox.telegram.sender.scheduler.MessageProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Profile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

@Repository
@Profile("!import")
@RequiredArgsConstructor
public class LoveboxOutboxRepository {

	private final MessageProperties messageProperties;

	@PostConstruct
	void initialize() {
		Path path = databasePath();
		try {
			Path parent = path.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
		}
		catch (IOException e) {
			throw new IllegalStateException("Failed to create Lovebox outbox directory", e);
		}
		try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
			statement.execute("""
					CREATE TABLE IF NOT EXISTS lovebox_outbox (
					  id INTEGER PRIMARY KEY AUTOINCREMENT,
					  image_base64 TEXT NOT NULL,
					  remote_message_id TEXT
					)
					""");
		}
		catch (SQLException e) {
			throw new IllegalStateException("Failed to initialize Lovebox outbox", e);
		}
	}

	public void enqueue(List<String> imagesAsBase64) {
		if (imagesAsBase64.isEmpty()) {
			return;
		}
		try (Connection connection = openConnection();
				PreparedStatement statement = connection
					.prepareStatement("INSERT INTO lovebox_outbox (image_base64) VALUES (?)")) {
			connection.setAutoCommit(false);
			try {
				for (String image : imagesAsBase64) {
					statement.setString(1, image);
					statement.addBatch();
				}
				statement.executeBatch();
				connection.commit();
			}
			catch (SQLException e) {
				connection.rollback();
				throw e;
			}
		}
		catch (SQLException e) {
			throw new IllegalStateException("Failed to enqueue Lovebox message", e);
		}
	}

	public Optional<OutboxMessage> findActive() {
		return findFirst("remote_message_id IS NOT NULL");
	}

	public Optional<OutboxMessage> findPending() {
		return findFirst("remote_message_id IS NULL");
	}

	public void markSubmitted(long id, String remoteMessageId) {
		try (Connection connection = openConnection();
				PreparedStatement statement = connection.prepareStatement(
						"UPDATE lovebox_outbox SET remote_message_id = ? WHERE id = ? AND remote_message_id IS NULL")) {
			statement.setString(1, remoteMessageId);
			statement.setLong(2, id);
			if (statement.executeUpdate() != 1) {
				throw new IllegalStateException("Lovebox outbox item could not be marked submitted: " + id);
			}
		}
		catch (SQLException e) {
			throw new IllegalStateException("Failed to update Lovebox outbox", e);
		}
	}

	public void delete(long id) {
		try (Connection connection = openConnection();
				PreparedStatement statement = connection.prepareStatement("DELETE FROM lovebox_outbox WHERE id = ?")) {
			statement.setLong(1, id);
			statement.executeUpdate();
		}
		catch (SQLException e) {
			throw new IllegalStateException("Failed to remove Lovebox outbox item", e);
		}
	}

	private Optional<OutboxMessage> findFirst(String condition) {
		String sql = "SELECT id, image_base64, remote_message_id FROM lovebox_outbox WHERE " + condition
				+ " ORDER BY id LIMIT 1";
		try (Connection connection = openConnection();
				PreparedStatement statement = connection.prepareStatement(sql);
				ResultSet resultSet = statement.executeQuery()) {
			if (!resultSet.next()) {
				return Optional.empty();
			}
			return Optional.of(new OutboxMessage(resultSet.getLong("id"), resultSet.getString("image_base64"),
					resultSet.getString("remote_message_id")));
		}
		catch (SQLException e) {
			throw new IllegalStateException("Failed to read Lovebox outbox", e);
		}
	}

	private Connection openConnection() throws SQLException {
		return DriverManager.getConnection("jdbc:sqlite:" + databasePath());
	}

	private Path databasePath() {
		return Path.of(messageProperties.getOutboxPath()).toAbsolutePath().normalize();
	}

}
