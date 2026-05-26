package com.patbaumgartner.lovebox.telegram.sender.services;

import com.patbaumgartner.lovebox.telegram.sender.telegram.LoveboxBotProperties;
import com.patbaumgartner.lovebox.telegram.sender.utils.Pair;
import com.patbaumgartner.lovebox.telegram.sender.utils.Tripple;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageCaption;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.photo.PhotoSize;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramMessageService {

	private final LoveboxBotProperties botProperties;

	private final Set<Long> chatIds = new TreeSet<>();

	private final ConcurrentHashMap<String, String> loveboxMessageStore = new ConcurrentHashMap<>();

	private final ConcurrentHashMap<String, Collection<Pair<Long, Message>>> telegramMessageStore = new ConcurrentHashMap<>();

	private TelegramClient telegramClient;

	@PostConstruct
	public void init() {
		telegramClient = new OkHttpTelegramClient(botProperties.getToken());
	}

	public boolean isAllowedChat(Long chatId) {
		return chatId != null && chatId.equals(botProperties.getAllowedChatId());
	}

	public void registerChat(long chatId) {
		chatIds.add(chatId);
	}

	public void updateKnownMessageStatuses(List<Pair<String, String>> messages) {
		messages.forEach(p -> {
			loveboxMessageStore.computeIfPresent(p.left(), (key, value) -> {
				if (!value.equals(p.right())) {
					Collection<Pair<Long, Message>> pairs = telegramMessageStore.get(p.left());
					if (pairs != null) {
						for (Pair<Long, Message> pair : pairs) {
							Message message = pair.right();
							if (message != null) {
								updatePhotoMessageCaption(message, p.right());
							}
						}
					}
				}
				return value;
			});
			loveboxMessageStore.put(p.left(), p.right());
		});
	}

	public void notifyWaterfallOfHearts() {
		chatIds.forEach(chatId -> sendTextMessage(chatId, "You received a waterfall of hearts! ❤❤❤"));
	}

	public void sendFailureMessage(Long sourceChatId, String text) {
		if (sourceChatId != null) {
			sendTextMessage(sourceChatId, text);
		}
	}

	public void publishLoveboxMessage(String text, Pair<String, byte[]> imagePair,
			Tripple<String, LocalDateTime, String> statusTripple) {
		loveboxMessageStore.put(statusTripple.left(), statusTripple.right());

		for (long chatId : chatIds) {
			Message sentMessage = sendPhotoMessage(chatId, text, imagePair, statusTripple);
			telegramMessageStore
				.compute(statusTripple.left(), (key, value) -> value == null ? new ArrayList<>() : value)
				.add(new Pair<>(chatId, sentMessage));
		}
	}

	public File downloadImageFromPhotoMessage(Message message) {
		List<PhotoSize> photoSizes = message.getPhoto();
		PhotoSize photoSize = photoSizes.get(photoSizes.size() - 1);

		GetFile getFile = new GetFile(photoSize.getFileId());
		try {
			String filePath = telegramClient.execute(getFile).getFilePath();
			File file = telegramClient.downloadFile(filePath);
			log.debug("Download photo \"{}\" from {}", photoSize.getFileId(), filePath);
			return file;
		}
		catch (TelegramApiException | RuntimeException e) {
			log.error("Failed to download photo \"{}\" due to error: {}", photoSize.getFileId(), e.getMessage(), e);
		}
		return null;
	}

	protected void sendTextMessage(long chatId, String text) {
		String textMessage = text != null ? text : "";
		SendMessage message = new SendMessage(String.valueOf(chatId), textMessage);
		try {
			telegramClient.execute(message);
			log.atDebug()
				.addArgument(() -> textMessage.replaceAll("\n", " "))
				.addArgument(chatId)
				.log("Sent message \"{}\" to {}");
		}
		catch (TelegramApiException | RuntimeException e) {
			log.error("Failed to send message \"{}\" to {} due to error: {}", textMessage, chatId, e.getMessage(), e);
		}
	}

	protected Message sendPhotoMessage(long chatId, String text, Pair<String, byte[]> imagePair,
			Tripple<String, LocalDateTime, String> statusTripple) {
		String textMessage = text != null ? text : "";
		SendPhoto message = new SendPhoto(String.valueOf(chatId),
				new InputFile(new ByteArrayInputStream(imagePair.right()), "image.png"));

		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");
		String formattedDateTime = ZonedDateTime.of(statusTripple.middle(), ZoneId.of("Europe/London"))
			.format(formatter);
		String caption = "Message: \"%s\" \nStatus: [%s].\nExecuted: %s".formatted(textMessage.replaceAll("\n", " "),
				statusTripple.right(), formattedDateTime);
		message.setCaption(caption);

		Message sentMessage = null;
		try {
			sentMessage = telegramClient.execute(message);
			log.atDebug()
				.addArgument(() -> textMessage.replaceAll("\n", " "))
				.addArgument(chatId)
				.log("Sent message \"{}\" to {}");
		}
		catch (TelegramApiException | RuntimeException e) {
			log.error("Failed to send message \"{}\" to {} due to error: {}", textMessage, chatId, e.getMessage(), e);
		}
		return sentMessage;
	}

	protected void updatePhotoMessageCaption(Message message, String status) {
		String text = message.getCaption().replaceAll("\\[.*\\]\\.", "[" + status + "].");
		String chatId = String.valueOf(message.getChatId());
		EditMessageCaption editMessage = EditMessageCaption.builder()
			.messageId(message.getMessageId())
			.chatId(chatId)
			.caption(text)
			.build();
		try {
			telegramClient.execute(editMessage);
			log.atDebug()
				.addArgument(() -> text.replaceAll("\n", " "))
				.addArgument(chatId)
				.log("Sent message \"{}\" to {}");
		}
		catch (TelegramApiException | RuntimeException e) {
			log.error("Failed to send message \"{}\" to {} due to error: {}", text, chatId, e.getMessage(), e);
		}
	}

}
