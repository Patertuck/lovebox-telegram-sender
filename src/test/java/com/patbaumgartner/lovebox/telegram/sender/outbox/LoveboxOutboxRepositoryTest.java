package com.patbaumgartner.lovebox.telegram.sender.outbox;

import com.patbaumgartner.lovebox.telegram.sender.scheduler.MessageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LoveboxOutboxRepositoryTest {

	@Test
	void preservesOrderAndSubmittedStateAcrossRepositoryInstances(@TempDir Path directory) {
		MessageProperties properties = new MessageProperties();
		properties.setOutboxPath(directory.resolve("outbox.db").toString());
		LoveboxOutboxRepository repository = new LoveboxOutboxRepository(properties);
		repository.initialize();
		repository.enqueue(List.of("first", "second"));

		OutboxMessage first = repository.findPending().orElseThrow();
		repository.markSubmitted(first.id(), "remote-1");

		LoveboxOutboxRepository reopened = new LoveboxOutboxRepository(properties);
		reopened.initialize();
		assertThat(reopened.findActive().orElseThrow().remoteMessageId()).isEqualTo("remote-1");
		assertThat(reopened.findPending().orElseThrow().imageAsBase64()).isEqualTo("second");
	}

}
