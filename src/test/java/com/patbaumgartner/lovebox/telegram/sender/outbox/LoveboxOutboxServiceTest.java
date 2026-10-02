package com.patbaumgartner.lovebox.telegram.sender.outbox;

import com.patbaumgartner.lovebox.telegram.sender.services.LoveboxMessageStatus;
import com.patbaumgartner.lovebox.telegram.sender.services.LoveboxSendResult;
import com.patbaumgartner.lovebox.telegram.sender.services.LoveboxService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoveboxOutboxServiceTest {

	@Mock
	private LoveboxOutboxRepository repository;

	@Mock
	private LoveboxService loveboxService;

	@InjectMocks
	private LoveboxOutboxService service;

	@Test
	void sendsFirstQueuedMessageImmediatelyWhenIdle() {
		when(loveboxService.isEnabled()).thenReturn(true);
		when(repository.findActive()).thenReturn(Optional.empty());
		when(repository.findPending()).thenReturn(Optional.of(new OutboxMessage(1, "first", null)));
		when(loveboxService.sendImageMessage("first")).thenReturn(new LoveboxSendResult("remote-1"));

		service.enqueue(List.of("first", "second"));

		verify(repository).enqueue(List.of("first", "second"));
		verify(repository).markSubmitted(1, "remote-1");
	}

	@Test
	void leavesQueueBlockedWhileActiveMessageIsUnread() {
		OutboxMessage active = new OutboxMessage(1, "first", "remote-1");
		when(loveboxService.isEnabled()).thenReturn(true);
		when(repository.findActive()).thenReturn(Optional.of(active));
		when(loveboxService.getMessages()).thenReturn(List.of(new LoveboxMessageStatus("remote-1", "received")));

		service.poll();

		verify(repository, never()).delete(1);
		verify(repository, never()).findPending();
	}

	@Test
	void removesReadMessageAndSubmitsExactlyOneSuccessor() {
		OutboxMessage active = new OutboxMessage(1, "first", "remote-1");
		OutboxMessage pending = new OutboxMessage(2, "second", null);
		when(loveboxService.isEnabled()).thenReturn(true);
		when(repository.findActive()).thenReturn(Optional.of(active));
		when(loveboxService.getMessages()).thenReturn(List.of(new LoveboxMessageStatus("remote-1", "READ")));
		when(repository.findPending()).thenReturn(Optional.of(pending));
		when(loveboxService.sendImageMessage("second")).thenReturn(new LoveboxSendResult("remote-2"));

		service.poll();

		verify(repository).delete(1);
		verify(repository).markSubmitted(2, "remote-2");
	}

}
