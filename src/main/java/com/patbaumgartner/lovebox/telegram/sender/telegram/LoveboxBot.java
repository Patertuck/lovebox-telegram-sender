package com.patbaumgartner.lovebox.telegram.sender.telegram;

import com.patbaumgartner.lovebox.telegram.sender.services.ImageService;
import com.patbaumgartner.lovebox.telegram.sender.services.LoveboxMessageDispatchService;
import com.patbaumgartner.lovebox.telegram.sender.services.TelegramMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.telegram.telegrambots.longpolling.BotSession;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.AfterBotRegistration;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.io.File;

@Slf4j
@Component
@Profile("!import")
@ConditionalOnProperty(name = "bot.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class LoveboxBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

	private final LoveboxBotProperties botProperties;

	private final ImageService imageService;

	private final TelegramMessageService telegramMessageService;

	private final LoveboxMessageDispatchService dispatchService;

	@Override
	public void consume(Update update) {
		if (!update.hasMessage()) {
			return;
		}

		Message message = update.getMessage();
		if (!telegramMessageService.isAllowedChat(message.getChat().getId())) {
			log.warn("Blocked unauthorized message from Chat ID: {}", message.getChat().getId());
			return;
		}
		if (message.getText() != null && message.getText().startsWith("/start")) {
			return;
		}

		try {
			if (message.hasText()) {
				dispatchService.dispatchText(message.getChatId(), message.getText());
			}
			else if (message.hasPhoto()) {
				dispatchMediaMessage(message);
			}
			else {
				telegramMessageService.sendTextMessage(message.getChatId(), "Only text and photos are supported.");
				return;
			}
			telegramMessageService.sendTextMessage(message.getChatId(), "Message submitted to Lovebox.");
		}
		catch (RuntimeException e) {
			log.error("Failed to submit Telegram message to Lovebox.", e);
			// Text sends already report their error; media sends need a response here.
			if (!message.hasText()) {
				telegramMessageService.sendFailureMessage(message.getChatId(), "Failed to submit message to Lovebox.");
			}
		}
	}

	private void dispatchMediaMessage(Message message) {
		File file = telegramMessageService.downloadImageFromPhotoMessage(message);
		if (file == null) {
			throw new IllegalStateException("Telegram photo could not be downloaded");
		}
		dispatchService.dispatchImage(imageService.resizeImageToBase64(file, message.getCaption()));
	}

	@AfterBotRegistration
	public void afterRegistration(BotSession botSession) {
		log.info("Registered TelegramBot with Username: {} running state is: {}", botProperties.getUsername(),
				botSession.isRunning());
	}

	@Override
	public String getBotToken() {
		return botProperties.getToken();
	}

	@Override
	public LongPollingUpdateConsumer getUpdatesConsumer() {
		return this;
	}

}
