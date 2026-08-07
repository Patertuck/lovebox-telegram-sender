package com.patbaumgartner.lovebox.telegram.sender.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoveboxMessageDispatchService {

	private final ImageService imageService;

	private final LoveboxService loveboxService;

	private final TelegramMessageService telegramMessageService;

	public void dispatchText(Long sourceChatId, String text) {
		try {
			for (String imageAsBase64 : imageService.prepareTextMessages(text)) {
				dispatchImage(imageAsBase64);
			}
		}
		catch (RuntimeException e) {
			log.error("Failed to submit message to Lovebox.", e);
			telegramMessageService.sendFailureMessage(sourceChatId, "Failed to submit message to Lovebox.");
			throw e;
		}
	}

	public void dispatchTextForScheduler(String text) {
		for (String imageAsBase64 : imageService.prepareTextMessages(text)) {
			dispatchImage(imageAsBase64);
		}
	}

	public void dispatchImage(String imageAsBase64) {
		loveboxService.sendImageMessage(imageAsBase64);
	}

}
