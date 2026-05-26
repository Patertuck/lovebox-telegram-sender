package com.patbaumgartner.lovebox.telegram.sender.services;

import com.patbaumgartner.lovebox.telegram.sender.utils.Pair;
import com.patbaumgartner.lovebox.telegram.sender.utils.Tripple;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoveboxMessageDispatchServiceTest {

	@Mock
	private ImageService imageService;

	@Mock
	private LoveboxService loveboxService;

	@Mock
	private TelegramMessageService telegramMessageService;

	@InjectMocks
	private LoveboxMessageDispatchService dispatchService;

	@Test
	void dispatchesPreparedTextChunks() {
		Pair<String, byte[]> firstImage = new Pair<>("base64-first", new byte[] { 1 });
		Pair<String, byte[]> secondImage = new Pair<>("base64-second", new byte[] { 2 });
		when(imageService.prepareTextMessages("hello there"))
			.thenReturn(List.of(new ImageService.PreparedTextMessage("first", firstImage),
					new ImageService.PreparedTextMessage("second", secondImage)));
		when(loveboxService.sendImageMessage("base64-first"))
			.thenReturn(new Tripple<>("id-1", LocalDateTime.of(2026, 5, 1, 8, 0), "queued"));
		when(loveboxService.sendImageMessage("base64-second"))
			.thenReturn(new Tripple<>("id-2", LocalDateTime.of(2026, 5, 1, 8, 1), "queued"));

		dispatchService.dispatchText(null, "hello there");

		verify(loveboxService).sendImageMessage("base64-first");
		verify(loveboxService).sendImageMessage("base64-second");
		ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
		verify(telegramMessageService, times(2)).publishLoveboxMessage(textCaptor.capture(), any(), any());
		assertThat(textCaptor.getAllValues()).containsExactly("first", "second");
		verify(telegramMessageService, never()).sendFailureMessage(any(), any());
	}

	@Test
	void notifiesSourceChatWhenLoveboxRejectsMessage() {
		Pair<String, byte[]> image = new Pair<>("base64", new byte[] { 1 });
		when(loveboxService.sendImageMessage("base64")).thenThrow(new IllegalStateException("boom"));

		dispatchService.dispatchPreparedMessage(123L, "hello", image);

		verify(telegramMessageService).sendFailureMessage(123L,
				"Lovebox rejected the message. Check the application logs for the API response details.");
		verify(telegramMessageService, never()).publishLoveboxMessage(any(), any(), any());
	}

}
