package com.patbaumgartner.lovebox.telegram.sender.outbox;

import com.patbaumgartner.lovebox.telegram.sender.services.ImageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "bot.enabled=false")
@EnabledIfSystemProperty(named = "lovebox.live", matches = "true")
class LiveLoveboxQueueTest {

	private static final String MESSAGE = """
			Here is a little reminder of how much joy you bring into my life. Even on ordinary days, you have a way
			of making everything feel warmer, brighter, and more meaningful. I love the small moments we share:
			laughing about something silly, talking about our day, making plans together, or simply enjoying the quiet
			when no words are needed. Those moments may seem simple, but they are some of my happiest memories.

			I hope that whenever you see this message, you remember how deeply appreciated you are. You deserve
			kindness, patience, encouragement, and all the happiness the world can offer. No matter how busy life
			becomes or how far apart we might sometimes feel, you are always close to my heart and never far from my
			thoughts.

			This message was made extra long for our Lovebox test, but every word is still true. The first page should
			remain visible until you open it. Only after the Lovebox reports that page as read should the second page
			arrive. So turn the heart, enjoy the first part, and watch for the rest of this message afterward. ❤️
			""";

	@Autowired
	private ImageService imageService;

	@Autowired
	private LoveboxOutboxRepository repository;

	@Autowired
	private LoveboxOutboxService outboxService;

	@Test
	void sendsTwoPagesOneAtATimeToTheConfiguredLovebox() throws InterruptedException {
		assertThat(repository.findActive()).as("active outbox message before live test").isEmpty();
		assertThat(repository.findPending()).as("pending outbox message before live test").isEmpty();

		List<String> pages = imageService.prepareTextMessages(MESSAGE);
		assertThat(pages).hasSize(2);
		outboxService.enqueue(pages);

		Instant deadline = Instant.now().plus(Duration.ofMinutes(10));
		while (Instant.now().isBefore(deadline)
				&& (repository.findActive().isPresent() || repository.findPending().isPresent())) {
			Thread.sleep(Duration.ofSeconds(5));
		}

		assertThat(repository.findActive()).as("active message after waiting for both pages to be read").isEmpty();
		assertThat(repository.findPending()).as("pending message after waiting for both pages to be read").isEmpty();
	}

}
