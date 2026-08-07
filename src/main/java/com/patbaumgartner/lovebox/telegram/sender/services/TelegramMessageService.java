package com.patbaumgartner.lovebox.telegram.sender.services;

import com.patbaumgartner.lovebox.telegram.sender.telegram.LoveboxBotProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.photo.PhotoSize;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.File;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramMessageService {

	private final LoveboxBotProperties botProperties;

	private TelegramClient telegramClient;

	@PostConstruct
	public void init() {
		telegramClient = new OkHttpTelegramClient(botProperties.getToken());
	}

	public boolean isAllowedChat(Long chatId) {
		return chatId != null && chatId.equals(botProperties.getAllowedChatId());
	}

	public void sendFailureMessage(Long sourceChatId, String text) {
		if (sourceChatId != null) {
			sendTextMessage(sourceChatId, text);
		}
	}

	public File downloadImageFromPhotoMessage(Message message) {
		List<PhotoSize> photoSizes = message.getPhoto();
		PhotoSize photoSize = photoSizes.get(photoSizes.size() - 1);
		try {
			String filePath = telegramClient.execute(new GetFile(photoSize.getFileId())).getFilePath();
			return telegramClient.downloadFile(filePath);
		}
		catch (TelegramApiException | RuntimeException e) {
			log.error("Failed to download Telegram photo {}.", photoSize.getFileId(), e);
			return null;
		}
	}

	public void sendTextMessage(long chatId, String text) {
		try {
			telegramClient.execute(new SendMessage(String.valueOf(chatId), text == null ? "" : text));
		}
		catch (TelegramApiException | RuntimeException e) {
			log.error("Failed to send Telegram response to {}.", chatId, e);
		}
	}

}
