package com.patbaumgartner.lovebox.telegram.sender.scheduler;

import com.patbaumgartner.lovebox.telegram.sender.services.LoveboxMessageDispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

@Slf4j
@Component
@Profile("!import")
@RequiredArgsConstructor
public class ScheduledMessageScheduler {

	private final ScheduledMessageRepository scheduledMessageRepository;

	private final LoveboxMessageDispatchService dispatchService;

	private final FallbackPictureService fallbackPictureService;

	private final Clock clock;

	@Scheduled(cron = "${messages.schedule-cron:0 30 21 * * *}", zone = "Europe/Zurich")
	public void sendScheduledMessage() {
		LocalDate sendDate = LocalDate.now(clock);
		log.info("Message scheduler woke up. Looking for a message scheduled for {}.", sendDate);
		scheduledMessageRepository.findMessageForDate(sendDate).ifPresentOrElse(dueMessage -> {
			try {
				dispatchService.dispatchTextForScheduler(dueMessage.message());
				log.info("Submitted scheduled message for {}.", sendDate);
			}
			catch (RuntimeException e) {
				log.error("Failed to submit scheduled message for {}.", sendDate, e);
			}
		}, () -> {
			log.info("No scheduled message found for {}. Looking for a fallback picture.", sendDate);
			fallbackPictureService.sendRandomPicture();
		});
	}

}
