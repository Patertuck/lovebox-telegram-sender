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
		LocalDate sourceDate = LocalDate.now(clock.withZone(BUSINESS_ZONE)).minusYears(1);
		List<ScheduledMessage> dueMessages = scheduledMessageRepository.findPendingMessagesForSourceDate(sourceDate);
		if (dueMessages.isEmpty()) {
			log.debug("No anniversary messages due for source date {}", sourceDate);
			return;
		}

		for (ScheduledMessage dueMessage : dueMessages) {
			try {
				dispatchService.dispatchTextForScheduler(dueMessage.body());
				scheduledMessageRepository.markSent(dueMessage.id(), OffsetDateTime.now(clock.withZone(BUSINESS_ZONE)));
			}
			catch (RuntimeException e) {
				log.error("Failed to send anniversary message with id {}: {}", dueMessage.id(), e.getMessage(), e);
				scheduledMessageRepository.markPendingWithError(dueMessage.id(),
						OffsetDateTime.now(clock.withZone(BUSINESS_ZONE)), e.getMessage());
			}
		}
	}

}
