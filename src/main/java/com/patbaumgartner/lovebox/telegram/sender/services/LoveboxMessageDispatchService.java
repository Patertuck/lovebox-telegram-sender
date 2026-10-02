package com.patbaumgartner.lovebox.telegram.sender.services;

import com.patbaumgartner.lovebox.telegram.sender.outbox.LoveboxOutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Profile("!import")
@RequiredArgsConstructor
public class LoveboxMessageDispatchService {

	private final ImageService imageService;

	private final LoveboxOutboxService outboxService;

	private final TelegramMessageService telegramMessageService;

	public void dispatchText(Long sourceChatId, String text) {
		try {
			outboxService.enqueue(imageService.prepareTextMessages(text));
		}
		catch (RuntimeException e) {
			log.error("Failed to submit message to Lovebox.", e);
			telegramMessageService.sendFailureMessage(sourceChatId, "Failed to submit message to Lovebox.");
			throw e;
		}
	}

	public void dispatchTextForScheduler(String text) {
		outboxService.enqueue(imageService.prepareTextMessages(text));
	}

	public void dispatchImage(String imageAsBase64) {
		outboxService.enqueue(java.util.List.of(imageAsBase64));
	}

}
