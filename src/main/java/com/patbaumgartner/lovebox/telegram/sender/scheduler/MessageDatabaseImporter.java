package com.patbaumgartner.lovebox.telegram.sender.scheduler;

import lombok.RequiredArgsConstructor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Profile("import")
@RequiredArgsConstructor
public class MessageDatabaseImporter implements CommandLineRunner {

	private static final Pattern DATE_HEADING = Pattern.compile("^\\d{2}\\.\\d{2}\\.\\d{2}$");

	private static final Pattern INLINE_DATE_HEADING = Pattern.compile("^(\\d{2}\\.\\d{2}\\.\\d{2}):\\s*(.*)$");

	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.uu");

	private final MessageProperties messageProperties;

	@Override
	public void run(String... args) {
		importDocument(Path.of(messageProperties.getDocumentPath()), Path.of(messageProperties.getDatabasePath()));
	}

	void importDocument(Path documentPath, Path databasePath) {
		Map<LocalDate, String> messages = parseDocument(documentPath);
		if (messages.isEmpty()) {
			throw new IllegalArgumentException("The Word document contains no dated message sections");
		}
		writeDatabase(messages, databasePath);
	}

	Map<LocalDate, String> parseDocument(Path documentPath) {
		if (!Files.isRegularFile(documentPath)) {
			throw new IllegalArgumentException("Word document does not exist: " + documentPath);
		}
		List<Section> sections = new ArrayList<>();
		LocalDate currentDate = null;
		String currentHeading = null;
		List<String> currentParagraphs = new ArrayList<>();
		try (InputStream inputStream = Files.newInputStream(documentPath);
				XWPFDocument document = new XWPFDocument(inputStream)) {
			for (XWPFParagraph paragraph : document.getParagraphs()) {
				String text = paragraph.getText();
				String strippedText = text.strip();
				Matcher inlineHeading = INLINE_DATE_HEADING.matcher(strippedText);
				if (DATE_HEADING.matcher(strippedText).matches() || inlineHeading.matches()) {
					if (currentDate != null) {
						sections.add(new Section(currentDate, currentHeading, currentParagraphs));
					}
					String dateText = inlineHeading.matches() ? inlineHeading.group(1) : strippedText;
					currentDate = parseDate(dateText).plusYears(1);
					currentHeading = inlineHeading.matches() ? strippedText : DATE_FORMAT.format(currentDate);
					currentParagraphs = new ArrayList<>();
				}
				else if (currentDate != null) {
					currentParagraphs.add(text);
				}
			}
		}
		catch (IOException e) {
			throw new IllegalStateException("Failed to read Word document: " + documentPath, e);
		}
		if (currentDate != null) {
			sections.add(new Section(currentDate, currentHeading, currentParagraphs));
		}

		Map<LocalDate, String> messages = new LinkedHashMap<>();
		for (Section section : sections) {
			if (section.paragraphs().stream().allMatch(String::isBlank) && !hasInlineMessage(section.heading())) {
				throw new IllegalArgumentException("Message section for " + section.sendDate() + " is empty");
			}
			String message = section.paragraphs().isEmpty() ? section.heading()
					: section.heading() + "\n" + String.join("\n", section.paragraphs());
			if (messages.putIfAbsent(section.sendDate(), message) != null) {
				throw new IllegalArgumentException("Duplicate message date: " + DATE_FORMAT.format(section.sendDate()));
			}
		}
		return messages;
	}

	private LocalDate parseDate(String heading) {
		try {
			return LocalDate.parse(heading, DATE_FORMAT);
		}
		catch (DateTimeParseException e) {
			throw new IllegalArgumentException("Invalid date heading: " + heading, e);
		}
	}

	private boolean hasInlineMessage(String heading) {
		Matcher matcher = INLINE_DATE_HEADING.matcher(heading);
		return matcher.matches() && !matcher.group(2).isBlank();
	}

	private void writeDatabase(Map<LocalDate, String> messages, Path databasePath) {
		Path target = databasePath.toAbsolutePath().normalize();
		try {
			Files.createDirectories(target.getParent());
			Path temporary = Files.createTempFile(target.getParent(), "messages-", ".db");
			try {
				try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + temporary);
						Statement statement = connection.createStatement()) {
					statement.execute("CREATE TABLE messages (send_date TEXT PRIMARY KEY, message TEXT NOT NULL)");
					try (PreparedStatement insert = connection.prepareStatement(
							"INSERT INTO messages (send_date, message) VALUES (?, ?)")) {
						for (Map.Entry<LocalDate, String> entry : messages.entrySet()) {
							insert.setString(1, entry.getKey().toString());
							insert.setString(2, entry.getValue());
							insert.addBatch();
						}
						insert.executeBatch();
					}
				}
				Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			}
			finally {
				Files.deleteIfExists(temporary);
			}
		}
		catch (IOException | SQLException e) {
			throw new IllegalStateException("Failed to create message database: " + target, e);
		}
	}

	private record Section(LocalDate sendDate, String heading, List<String> paragraphs) {
	}

}
