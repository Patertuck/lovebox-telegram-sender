package com.patbaumgartner.lovebox.telegram.sender.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
	void dispatchesEachPreparedTextChunkToLovebox() {
		when(imageService.prepareTextMessages("hello"))
			.thenReturn(List.of("base64"));

		dispatchService.dispatchTextForScheduler("hello");

		verify(loveboxService).sendImageMessage("base64");
	}

}
