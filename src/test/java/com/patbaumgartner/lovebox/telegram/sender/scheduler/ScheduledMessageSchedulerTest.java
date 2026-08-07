package com.patbaumgartner.lovebox.telegram.sender.scheduler;

import com.patbaumgartner.lovebox.telegram.sender.services.LoveboxMessageDispatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduledMessageSchedulerTest {

	@Mock
	private ScheduledMessageRepository repository;

	@Mock
	private LoveboxMessageDispatchService dispatchService;

	@Mock
	private FallbackPictureService fallbackPictureService;

	private ScheduledMessageScheduler scheduler;

	@BeforeEach
	void setUp() {
		scheduler = new ScheduledMessageScheduler(repository, dispatchService, fallbackPictureService,
				Clock.fixed(Instant.parse("2026-05-26T16:00:00Z"), ZoneId.of("Europe/Zurich")));
	}

	@Test
	void sendsTheMessageScheduledForToday() {
		when(repository.findMessageForDate(LocalDate.of(2026, 5, 26)))
			.thenReturn(Optional.of(new ScheduledMessage(LocalDate.of(2026, 5, 26), "26.05.26\nHello")));

		scheduler.sendScheduledMessage();

		verify(dispatchService).dispatchTextForScheduler("26.05.26\nHello");
		verifyNoInteractions(fallbackPictureService);
	}

	@Test
	void sendsFallbackPictureWhenNoMessageIsScheduledForToday() {
		when(repository.findMessageForDate(LocalDate.of(2026, 5, 26))).thenReturn(Optional.empty());

		scheduler.sendScheduledMessage();

		verifyNoInteractions(dispatchService);
		verify(fallbackPictureService).sendRandomPicture();
	}

}
