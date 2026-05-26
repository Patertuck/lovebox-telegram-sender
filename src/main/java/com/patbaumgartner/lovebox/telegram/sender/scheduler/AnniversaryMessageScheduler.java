package com.patbaumgartner.lovebox.telegram.sender.scheduler;

import com.patbaumgartner.lovebox.telegram.sender.services.LoveboxMessageDispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnniversaryMessageScheduler {

	static final ZoneId BUSINESS_ZONE = ZoneId.of("Europe/Zurich");

	private final ScheduledMessageRepository scheduledMessageRepository;

	private final LoveboxMessageDispatchService dispatchService;

	private final Clock clock;

	@Scheduled(cron = "0 0 11 * * *", zone = "Europe/Zurich")
	public void sendAnniversaryMessages() {
		OffsetDateTime runStartedAt = OffsetDateTime.now(clock.withZone(BUSINESS_ZONE));
		LocalDate sourceDate = runStartedAt.toLocalDate().minusYears(1);
		log.info("Anniversary scheduler woke up at {}. Looking for pending messages with source date {}.",
				runStartedAt, sourceDate);

		List<ScheduledMessage> dueMessages = scheduledMessageRepository.findPendingMessagesForSourceDate(sourceDate);
		if (dueMessages.isEmpty()) {
			log.info("Anniversary scheduler found no pending messages for source date {}.", sourceDate);
			return;
		}

		log.info("Anniversary scheduler found {} pending message(s) for source date {}.", dueMessages.size(),
				sourceDate);
		for (ScheduledMessage dueMessage : dueMessages) {
			try {
				log.info("Sending anniversary message with id {} and body length {}.", dueMessage.id(),
						dueMessage.body().length());
				dispatchService.dispatchTextForScheduler(dueMessage.body());
				OffsetDateTime sentAt = OffsetDateTime.now(clock.withZone(BUSINESS_ZONE));
				scheduledMessageRepository.markSent(dueMessage.id(), sentAt);
				log.info("Successfully sent anniversary message with id {} at {}.", dueMessage.id(), sentAt);
			}
			catch (RuntimeException e) {
				log.error("Failed to send anniversary message with id {}: {}", dueMessage.id(), e.getMessage(), e);
				OffsetDateTime failedAt = OffsetDateTime.now(clock.withZone(BUSINESS_ZONE));
				scheduledMessageRepository.markPendingWithError(dueMessage.id(), failedAt, e.getMessage());
				log.info("Left anniversary message with id {} in pending state for retry after failure at {}.",
						dueMessage.id(), failedAt);
			}
		}
	}

}
