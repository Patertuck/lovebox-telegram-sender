package com.patbaumgartner.lovebox.telegram.sender.scheduler;

import com.patbaumgartner.lovebox.telegram.sender.services.LoveboxMessageDispatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnniversaryMessageSchedulerTest {

	private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-05-26T16:00:00Z"),
			ZoneId.of("Europe/Zurich"));

	@Mock
	private ScheduledMessageRepository repository;

	@Mock
	private LoveboxMessageDispatchService dispatchService;

	private AnniversaryMessageScheduler scheduler;

	@BeforeEach
	void setUp() {
		scheduler = new AnniversaryMessageScheduler(repository, dispatchService, FIXED_CLOCK);
	}

	@Test
	void doesNothingWhenNoMessagesAreDue() {
		when(repository.findPendingMessagesForSourceDate(java.time.LocalDate.of(2025, 5, 26))).thenReturn(List.of());

		scheduler.sendAnniversaryMessages();

		verifyNoMoreInteractions(dispatchService);
		verify(repository, never()).markSent(anyLong(), any(OffsetDateTime.class));
		verify(repository, never()).markPendingWithError(anyLong(), any(OffsetDateTime.class), anyString());
	}

	@Test
	void sendsDueMessagesInRepositoryOrder() {
		when(repository.findPendingMessagesForSourceDate(java.time.LocalDate.of(2025, 5, 26)))
			.thenReturn(List.of(new ScheduledMessage(4L, "first"), new ScheduledMessage(9L, "second")));

		scheduler.sendAnniversaryMessages();

		InOrder inOrder = inOrder(dispatchService, repository);
		inOrder.verify(dispatchService).dispatchTextForScheduler("first");
		inOrder.verify(repository).markSent(eq(4L), any(OffsetDateTime.class));
		inOrder.verify(dispatchService).dispatchTextForScheduler("second");
		inOrder.verify(repository).markSent(eq(9L), any(OffsetDateTime.class));
		verify(repository, never()).markPendingWithError(anyLong(), any(OffsetDateTime.class), anyString());
	}

	@Test
	void keepsMessagePendingWhenDispatchFails() {
		when(repository.findPendingMessagesForSourceDate(java.time.LocalDate.of(2025, 5, 26)))
			.thenReturn(List.of(new ScheduledMessage(4L, "first")));
		doThrow(new IllegalStateException("boom")).when(dispatchService).dispatchTextForScheduler("first");

		scheduler.sendAnniversaryMessages();

		verify(repository, never()).markSent(anyLong(), any(OffsetDateTime.class));
		verify(repository).markPendingWithError(eq(4L), any(OffsetDateTime.class), eq("boom"));
	}

}
