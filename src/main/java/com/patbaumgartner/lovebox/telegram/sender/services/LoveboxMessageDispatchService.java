package com.patbaumgartner.lovebox.telegram.sender.services;

import com.patbaumgartner.lovebox.telegram.sender.utils.Pair;
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
			for (ImageService.PreparedTextMessage preparedMessage : imageService.prepareTextMessages(text)) {
				dispatchPreparedMessage(preparedMessage.text(), preparedMessage.imagePair());
			}
		}
		catch (RuntimeException e) {
			log.error("Failed to submit message to Lovebox.", e);
			telegramMessageService.sendFailureMessage(sourceChatId, "Failed to submit message to Lovebox.");
			throw e;
		}
	}

	public void dispatchTextForScheduler(String text) {
		for (ImageService.PreparedTextMessage preparedMessage : imageService.prepareTextMessages(text)) {
			dispatchPreparedMessage(preparedMessage.text(), preparedMessage.imagePair());
		}
	}

	public void dispatchPreparedMessage(String text, Pair<String, byte[]> imagePair) {
		loveboxService.sendImageMessage(imagePair.left());
	}

}
