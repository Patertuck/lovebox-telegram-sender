package com.patbaumgartner.lovebox.telegram.sender.services;

import com.patbaumgartner.lovebox.telegram.sender.utils.Pair;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.imgscalr.Scalr;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;

@Slf4j
@Component
@RequiredArgsConstructor
public class ImageService {

	public static final int DISPLAY_WIDTH = 1280;

	public static final int DISPLAY_HEIGHT = 960;

	public static final int BORDER_WIDTH = 20;

	public static final int INITIAL_FONT_SIZE = 18;

	// Emoji Font Limitations:
	// https://mail.openjdk.java.net/pipermail/2d-dev/2021-May/012975.html
	public static final int MAX_EMOJI_FONT_SIZE = 100;

	public static final int MIN_TEXT_FONT_SIZE = 44;

	public static final String FONT_NAME = "Sans";

	public final ResourceLoader resourceLoader;

	@SneakyThrows
	public Pair<String, byte[]> resizeImageToPair(File file, String text) {
		BufferedImage originalImage = ImageIO.read(file);
		BufferedImage resizedImage = Scalr.resize(originalImage, Scalr.Method.AUTOMATIC, Scalr.Mode.AUTOMATIC,
				DISPLAY_WIDTH, DISPLAY_HEIGHT, Scalr.OP_ANTIALIAS);

		BufferedImage image = new BufferedImage(DISPLAY_WIDTH, DISPLAY_HEIGHT, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setColor(Color.black);
		graphics.fillRect(0, 0, DISPLAY_WIDTH, DISPLAY_HEIGHT);

		int x = (DISPLAY_WIDTH - resizedImage.getWidth()) / 2;
		int y = (DISPLAY_HEIGHT - resizedImage.getHeight()) / 2;

		graphics.drawImage(resizedImage, x, y, null);

		if (text != null) {
			drawCenteredMessage(graphics, text);
		}

		graphics.dispose();

		return constructImagePair(image);
	}

	@SneakyThrows
	public Pair<String, byte[]> createTextImageToPair(String message) {
		return createTextImageToPair(message, null, createRandomBackgroundColor(), false);
	}

	@SneakyThrows
	public Pair<String, byte[]> createTextImageToPair(String message, Integer lockedFontSize) {
		return createTextImageToPair(message, lockedFontSize, createRandomBackgroundColor(), false);
	}

	@SneakyThrows
	public Pair<String, byte[]> createTextImageToPair(String message, Integer lockedFontSize, Color backgroundColor,
			boolean topAligned) {
		BufferedImage image = new BufferedImage(DISPLAY_WIDTH, DISPLAY_HEIGHT, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();

		graphics.setColor(backgroundColor);
		graphics.fillRect(0, 0, DISPLAY_WIDTH, DISPLAY_HEIGHT);

		if (message != null) {
			drawWrappedMessage(graphics, message, lockedFontSize, topAligned);
		}

		graphics.dispose();

		return constructImagePair(image);
	}

	public List<PreparedTextMessage> prepareTextMessages(String text) {
		String normalizedMessage = normalizeMessage(text);
		if (normalizedMessage.isEmpty()) {
			return List.of(new PreparedTextMessage("", createTextImageToPair("")));
		}

		BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try {
			String firstChunk = extractFirstChunk(normalizedMessage, graphics);
			LayoutResult firstLayout = resolveBestLayout(graphics, firstChunk);
			int lockedFontSize = firstLayout.font().getSize();

			List<String> chunks = splitTextIntoMessageChunks(normalizedMessage, graphics, lockedFontSize);
			boolean splitSequence = chunks.size() > 1;
			Color backgroundColor = splitSequence ? createRandomBackgroundColor() : null;
			List<PreparedTextMessage> preparedMessages = new ArrayList<>();
			for (String chunk : chunks) {
				Pair<String, byte[]> imagePair = splitSequence
						? createTextImageToPair(chunk, lockedFontSize, backgroundColor, true)
						: createTextImageToPair(chunk);
				preparedMessages.add(new PreparedTextMessage(chunk, imagePair));
			}
			return preparedMessages;
		}
		finally {
			graphics.dispose();
		}
	}

	@SneakyThrows
	public Pair<String, byte[]> createFixedImageToPair() {
		Resource resource = resourceLoader.getResource("lovebox.jpeg");
		Image image = ImageIO.read(resource.getInputStream());
		image = image.getScaledInstance(DISPLAY_WIDTH, DISPLAY_HEIGHT, Image.SCALE_SMOOTH);
		BufferedImage bufferedImage = new BufferedImage(DISPLAY_WIDTH, DISPLAY_HEIGHT, BufferedImage.TYPE_INT_ARGB);

		Graphics2D graphics = bufferedImage.createGraphics();
		graphics.drawImage(image, 0, 0, null);

		graphics.dispose();

		return constructImagePair(bufferedImage);
	}

	protected void drawCenteredMessage(Graphics2D graphics, String text) {
		String message = text.strip();

		FontMetrics initialFm = graphics.getFontMetrics();
		log.debug("Using initial font with settings: {}", initialFm);

		// Calculate max font
		graphics.setColor(Color.white);
		Font newFont = new Font(FONT_NAME, Font.PLAIN, INITIAL_FONT_SIZE);
		graphics.setFont(newFont);
		FontMetrics newFm = graphics.getFontMetrics();
		String[] lines = message.split("\n");
		// int stringWidth = initialFm.stringWidth(text) + 2 * BORDER_WIDTH;
		int stringWidth = Arrays.stream(lines)
			.map(line -> newFm.stringWidth(line) + 2 * BORDER_WIDTH)
			.mapToInt(v -> v)
			.max()
			.orElseThrow(NoSuchElementException::new);
		double widthRatio = (double) DISPLAY_WIDTH / (double) stringWidth;

		int newFontSize = (int) (newFont.getSize() * widthRatio);
		int fontSizeToUse = Math.min(newFontSize, MAX_EMOJI_FONT_SIZE);
		Font finalFont = new Font(newFont.getName(), newFont.getStyle(), fontSizeToUse);
		graphics.setFont(finalFont);

		// Draw centered string
		FontMetrics fm = graphics.getFontMetrics();
		log.debug("Using new/recalculated font with settings: {}", fm);

		int lineHeight = fm.getHeight();
		int yInitialOffset = (lines.length - 1) * lineHeight;

		int x = 0;
		int y = fm.getAscent() + (DISPLAY_HEIGHT - (fm.getAscent() + fm.getDescent()) - yInitialOffset) / 2;

		for (String line : lines) {
			x = (DISPLAY_WIDTH - fm.stringWidth(line)) / 2;
			graphics.drawString(line, x, y);
			y += lineHeight;
		}
	}

	protected void drawWrappedMessage(Graphics2D graphics, String text, Integer lockedFontSize, boolean topAligned) {
		String message = normalizeMessage(text);
		if (message.isEmpty()) {
			return;
		}

		graphics.setColor(Color.white);
		LayoutResult layoutResult = lockedFontSize == null ? resolveBestLayout(graphics, message)
				: resolveLayoutForFontSize(graphics, message, lockedFontSize);
		graphics.setFont(layoutResult.font());

		FontMetrics fontMetrics = graphics.getFontMetrics();
		log.debug("Using wrapped text layout with font size {}", layoutResult.font().getSize());

		int lineHeight = fontMetrics.getHeight();
		int totalHeight = layoutResult.lines().size() * lineHeight;
		int blockX = topAligned ? BORDER_WIDTH : (DISPLAY_WIDTH - layoutResult.blockWidth()) / 2;
		int y = topAligned ? BORDER_WIDTH + fontMetrics.getAscent()
				: (DISPLAY_HEIGHT - totalHeight) / 2 + fontMetrics.getAscent();

		for (String line : layoutResult.lines()) {
			graphics.drawString(line, blockX, y);
			y += lineHeight;
		}
	}

	private LayoutResult resolveBestLayout(Graphics2D graphics, String message) {
		for (int fontSize = MAX_EMOJI_FONT_SIZE; fontSize >= MIN_TEXT_FONT_SIZE; fontSize--) {
			Font font = new Font(FONT_NAME, Font.PLAIN, fontSize);
			graphics.setFont(font);
			FontMetrics fontMetrics = graphics.getFontMetrics();
			List<String> lines = wrapTextToLines(message, fontMetrics);
			int blockWidth = calculateBlockWidth(lines, fontMetrics);
			int totalHeight = lines.size() * fontMetrics.getHeight();
			if (totalHeight <= getAvailableHeight()) {
				return new LayoutResult(lines, font, blockWidth);
			}
		}

		Font minimumFont = new Font(FONT_NAME, Font.PLAIN, MIN_TEXT_FONT_SIZE);
		graphics.setFont(minimumFont);
		List<String> lines = wrapTextToLines(message, graphics.getFontMetrics());
		return new LayoutResult(lines, minimumFont, calculateBlockWidth(lines, graphics.getFontMetrics()));
	}

	private LayoutResult resolveLayoutForFontSize(Graphics2D graphics, String message, int fontSize) {
		Font font = new Font(FONT_NAME, Font.PLAIN, fontSize);
		graphics.setFont(font);
		FontMetrics fontMetrics = graphics.getFontMetrics();
		List<String> lines = wrapTextToLines(message, fontMetrics);
		return new LayoutResult(lines, font, calculateBlockWidth(lines, fontMetrics));
	}

	private String extractFirstChunk(String normalizedMessage, Graphics2D graphics) {
		Font font = new Font(FONT_NAME, Font.PLAIN, MIN_TEXT_FONT_SIZE);
		graphics.setFont(font);
		FontMetrics fontMetrics = graphics.getFontMetrics();
		List<String> wrappedLines = wrapTextToLines(normalizedMessage, fontMetrics);
		int maxLinesPerChunk = Math.max(1, getAvailableHeight() / fontMetrics.getHeight());
		int firstChunkEnd = Math.min(maxLinesPerChunk, wrappedLines.size());
		return joinLines(wrappedLines.subList(0, firstChunkEnd));
	}

	private List<String> splitTextIntoMessageChunks(String normalizedMessage, Graphics2D graphics, int fontSize) {
		Font font = new Font(FONT_NAME, Font.PLAIN, fontSize);
		graphics.setFont(font);
		FontMetrics fontMetrics = graphics.getFontMetrics();
		List<String> wrappedLines = wrapTextToLines(normalizedMessage, fontMetrics);
		int maxLinesPerChunk = Math.max(1, getAvailableHeight() / fontMetrics.getHeight());

		List<String> chunks = new ArrayList<>();
		List<String> currentChunk = new ArrayList<>();
		for (String line : wrappedLines) {
			if (currentChunk.size() == maxLinesPerChunk) {
				chunks.add(joinLines(currentChunk));
				currentChunk = new ArrayList<>();
			}
			currentChunk.add(line);
		}

		if (!currentChunk.isEmpty()) {
			chunks.add(joinLines(currentChunk));
		}

		return chunks;
	}

	private int calculateBlockWidth(List<String> lines, FontMetrics fontMetrics) {
		return lines.stream().mapToInt(fontMetrics::stringWidth).max().orElse(0);
	}

	private List<String> wrapTextToLines(String message, FontMetrics fontMetrics) {
		List<String> wrappedLines = new ArrayList<>();
		String[] paragraphs = message.split("\n", -1);

		for (String paragraph : paragraphs) {
			if (paragraph.isEmpty()) {
				wrappedLines.add("");
				continue;
			}

			StringBuilder currentLine = new StringBuilder();
			for (String word : paragraph.split("\\s+")) {
				appendToken(wrappedLines, currentLine, word, fontMetrics);
			}

			if (!currentLine.isEmpty()) {
				wrappedLines.add(currentLine.toString());
			}
		}

		return wrappedLines.isEmpty() ? List.of("") : wrappedLines;
	}

	private void appendToken(List<String> wrappedLines, StringBuilder currentLine, String token,
			FontMetrics fontMetrics) {
		if (fontMetrics.stringWidth(token) <= getAvailableWidth()) {
			String candidate = currentLine.isEmpty() ? token : currentLine + " " + token;
			if (fontMetrics.stringWidth(candidate) <= getAvailableWidth()) {
				currentLine.setLength(0);
				currentLine.append(candidate);
				return;
			}

			if (!currentLine.isEmpty()) {
				wrappedLines.add(currentLine.toString());
				currentLine.setLength(0);
			}
			currentLine.append(token);
			return;
		}

		if (!currentLine.isEmpty()) {
			wrappedLines.add(currentLine.toString());
			currentLine.setLength(0);
		}

		List<String> tokenParts = splitLongToken(token, fontMetrics);
		for (int i = 0; i < tokenParts.size() - 1; i++) {
			wrappedLines.add(tokenParts.get(i));
		}
		currentLine.append(tokenParts.get(tokenParts.size() - 1));
	}

	private List<String> splitLongToken(String token, FontMetrics fontMetrics) {
		List<String> parts = new ArrayList<>();
		StringBuilder currentPart = new StringBuilder();

		for (int i = 0; i < token.length(); i++) {
			char character = token.charAt(i);
			String candidate = currentPart.toString() + character;
			if (!currentPart.isEmpty() && fontMetrics.stringWidth(candidate) > getAvailableWidth()) {
				parts.add(currentPart.toString());
				currentPart.setLength(0);
			}
			currentPart.append(character);
		}

		if (!currentPart.isEmpty()) {
			parts.add(currentPart.toString());
		}

		return parts;
	}

	private String joinLines(List<String> lines) {
		int start = 0;
		int end = lines.size();

		while (start < end && lines.get(start).isBlank()) {
			start++;
		}
		while (end > start && lines.get(end - 1).isBlank()) {
			end--;
		}

		return String.join("\n", lines.subList(start, end));
	}

	private String normalizeMessage(String text) {
		if (text == null) {
			return "";
		}
		return text.replace("\r\n", "\n").replace('\r', '\n').replace('\n', ' ').replaceAll("\\s+", " ").strip();
	}

	private Color createRandomBackgroundColor() {
		Random random = new Random();
		return new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256));
	}

	private int getAvailableWidth() {
		return DISPLAY_WIDTH - 2 * BORDER_WIDTH;
	}

	private int getAvailableHeight() {
		return DISPLAY_HEIGHT - 2 * BORDER_WIDTH;
	}

	private record LayoutResult(List<String> lines, Font font, int blockWidth) {
	}

	public record PreparedTextMessage(String text, Pair<String, byte[]> imagePair) {
	}

	protected Pair<String, byte[]> constructImagePair(BufferedImage image) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ImageIO.write(image, "png", output);
		String base64Image = Base64.getEncoder().encodeToString(output.toByteArray());

		return new Pair("data:image/png;base64," + base64Image, output.toByteArray());
	}

}
