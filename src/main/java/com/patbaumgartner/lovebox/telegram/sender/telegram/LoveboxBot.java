package com.patbaumgartner.lovebox.telegram.sender.telegram;

import com.patbaumgartner.lovebox.telegram.sender.services.ImageService;
import com.patbaumgartner.lovebox.telegram.sender.services.LoveboxMessageDispatchService;
import com.patbaumgartner.lovebox.telegram.sender.services.LoveboxService;
import com.patbaumgartner.lovebox.telegram.sender.services.TelegramMessageService;
import com.patbaumgartner.lovebox.telegram.sender.utils.Pair;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.BotSession;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.AfterBotRegistration;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.io.File;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoveboxBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

	private final LoveboxBotProperties botProperties;

	private final ImageService imageService;

	private final LoveboxService loveboxService;

	private final TelegramMessageService telegramMessageService;

	private final LoveboxMessageDispatchService dispatchService;

	@Scheduled(fixedRate = 20_000)
	public void readMessageBox() {
		List<Pair<String, String>> messages = loveboxService.getMessages();
		telegramMessageService.updateKnownMessageStatuses(messages);
	}

	@Scheduled(fixedRate = 20_000)
	public void receiveWaterfallOfHearts() {
		String heartsRainId = loveboxService.receiveWaterfallOfHearts();
		if (heartsRainId != null) {
			telegramMessageService.notifyWaterfallOfHearts();
		}
	}

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

		telegramMessageService.registerChat(message.getChat().getId());

		String text = message.getText();
		if (text != null && text.startsWith("/start")) {
			return;
		}

		if (message.hasText()) {
			dispatchService.dispatchText(message.getChatId(), text);
			return;
		}

		Pair<String, byte[]> imagePair = null;
		try {
			if (message.hasPhoto()) {
				File file = telegramMessageService.downloadImageFromPhotoMessage(message);
				text = message.getCaption();
				imagePair = imageService.resizeImageToPair(file, text);
			}

			if (imagePair == null) {
				imagePair = imageService.createFixedImageToPair();
			}
		}
		catch (RuntimeException e) {
			log.error("Exception occurred: {}", e.getMessage(), e);
		}

		dispatchService.dispatchPreparedMessage(message.getChatId(), text, imagePair);
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
