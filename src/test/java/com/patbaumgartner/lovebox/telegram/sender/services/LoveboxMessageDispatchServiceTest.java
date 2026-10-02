package com.patbaumgartner.lovebox.telegram.sender.services;

import com.patbaumgartner.lovebox.telegram.sender.outbox.LoveboxOutboxService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoveboxMessageDispatchServiceTest {

	private static final String TWO_PAGE_MESSAGE = """
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

	@Mock
	private ImageService imageService;

	@Mock
	private LoveboxOutboxService outboxService;

	@Mock
	private TelegramMessageService telegramMessageService;

	@InjectMocks
	private LoveboxMessageDispatchService dispatchService;

	@Test
	void dispatchesEachPreparedTextChunkToLovebox() {
		when(imageService.prepareTextMessages("hello")).thenReturn(List.of("base64"));

		dispatchService.dispatchTextForScheduler("hello");

		verify(outboxService).enqueue(List.of("base64"));
	}

	@Test
	void queuesLongTextAsExactlyTwoLoveboxPages() {
		LoveboxMessageDispatchService service = new LoveboxMessageDispatchService(new ImageService(), outboxService,
				telegramMessageService);

		service.dispatchTextForScheduler(TWO_PAGE_MESSAGE);

		@SuppressWarnings("unchecked")
		org.mockito.ArgumentCaptor<List<String>> pages = org.mockito.ArgumentCaptor.forClass(List.class);
		verify(outboxService).enqueue(pages.capture());
		assertThat(pages.getValue()).hasSize(2).allSatisfy(page -> assertThat(page).startsWith("data:image/png;base64,"));
	}

}
