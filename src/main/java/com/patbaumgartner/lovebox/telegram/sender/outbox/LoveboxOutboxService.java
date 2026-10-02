package com.patbaumgartner.lovebox.telegram.sender.outbox;

import com.patbaumgartner.lovebox.telegram.sender.services.LoveboxMessageStatus;
import com.patbaumgartner.lovebox.telegram.sender.services.LoveboxSendResult;
import com.patbaumgartner.lovebox.telegram.sender.services.LoveboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@Profile("!import")
@RequiredArgsConstructor
public class LoveboxOutboxService {

	private final LoveboxOutboxRepository repository;

	private final LoveboxService loveboxService;

	private final Object processingLock = new Object();

	public void enqueue(List<String> imagesAsBase64) {
		if (!loveboxService.isEnabled()) {
			imagesAsBase64.forEach(loveboxService::sendImageMessage);
			return;
		}
		repository.enqueue(imagesAsBase64);
		trySendIfIdle();
	}

	@Scheduled(fixedDelayString = "${lovebox.poll-interval:10s}", initialDelayString = "1s")
	public void poll() {
		if (!loveboxService.isEnabled()) {
			return;
		}
		synchronized (processingLock) {
			try {
				var active = repository.findActive();
				if (active.isPresent()) {
					if (!isRead(active.get())) {
						return;
					}
					repository.delete(active.get().id());
					log.info("Lovebox message {} was read; advancing the outbox.", active.get().remoteMessageId());
				}
				sendNextPending();
			}
			catch (RuntimeException e) {
				log.warn("Could not advance the Lovebox outbox; it will be retried.", e);
			}
		}
	}

	private boolean isRead(OutboxMessage active) {
		List<LoveboxMessageStatus> statuses = loveboxService.getMessages();
		return statuses.stream()
			.filter(status -> active.remoteMessageId().equals(status.messageId()))
			.map(LoveboxMessageStatus::status)
			.anyMatch(status -> "read".equalsIgnoreCase(status));
	}

	private void trySendIfIdle() {
		synchronized (processingLock) {
			try {
				if (repository.findActive().isEmpty()) {
					sendNextPending();
				}
			}
			catch (RuntimeException e) {
				log.warn("Lovebox message is safely queued but could not be submitted yet; it will be retried.", e);
			}
		}
	}

	private void sendNextPending() {
		repository.findPending().ifPresent(message -> {
			LoveboxSendResult result = loveboxService.sendImageMessage(message.imageAsBase64());
			repository.markSubmitted(message.id(), result.messageId());
			log.info("Submitted Lovebox outbox item {} as message {}.", message.id(), result.messageId());
		});
	}

}
