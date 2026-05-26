package com.patbaumgartner.lovebox.telegram.sender.services;

import com.patbaumgartner.lovebox.telegram.sender.utils.Pair;
import com.patbaumgartner.lovebox.telegram.sender.utils.Tripple;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoveboxMessageDispatchService {

	private final ImageService imageService;

	private final LoveboxService loveboxService;

	private final TelegramMessageService telegramMessageService;

	public void dispatchText(Long sourceChatId, String text) {
		List<ImageService.PreparedTextMessage> preparedMessages = imageService.prepareTextMessages(text);
		for (ImageService.PreparedTextMessage preparedMessage : preparedMessages) {
			try {
				dispatchPreparedMessage(sourceChatId, preparedMessage.text(), preparedMessage.imagePair());
			}
			catch (RuntimeException e) {
				log.error("Exception occurred while creating Lovebox text image: {}", e.getMessage(), e);
				telegramMessageService.sendFailureMessage(sourceChatId,
						"Failed to create the Lovebox image. Check the application logs for details.");
				break;
			}
		}
	}

	public void dispatchTextForScheduler(String text) {
		List<ImageService.PreparedTextMessage> preparedMessages = imageService.prepareTextMessages(text);
		for (ImageService.PreparedTextMessage preparedMessage : preparedMessages) {
			dispatchPreparedMessageForScheduler(preparedMessage.text(), preparedMessage.imagePair());
		}
	}

	public void dispatchPreparedMessage(Long sourceChatId, String text, Pair<String, byte[]> imagePair) {
		try {
			Tripple<String, LocalDateTime, String> statusTripple = dispatchPreparedMessageForScheduler(text, imagePair);
			telegramMessageService.publishLoveboxMessage(text, imagePair, statusTripple);
		}
		catch (RuntimeException e) {
			log.error("Failed to send message to Lovebox: {}", e.getMessage(), e);
			telegramMessageService.sendFailureMessage(sourceChatId,
					"Lovebox rejected the message. Check the application logs for the API response details.");
		}
	}

	public Tripple<String, LocalDateTime, String> dispatchPreparedMessageForScheduler(String text,
			Pair<String, byte[]> imagePair) {
		return loveboxService.sendImageMessage(imagePair.left());
	}

}
