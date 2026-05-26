package com.patbaumgartner.lovebox.telegram.sender.scheduler;

import com.patbaumgartner.lovebox.telegram.sender.services.LoveboxMessageDispatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SchedulerMessageControllerTest {

	private MockMvc mockMvc;

	private LoveboxMessageDispatchService dispatchService;

	@BeforeEach
	void setUp() {
		dispatchService = mock(LoveboxMessageDispatchService.class);
		SchedulerIntegrationProperties properties = new SchedulerIntegrationProperties();
		properties.setSchedulerToken("test-token");
		mockMvc = MockMvcBuilders.standaloneSetup(new SchedulerMessageController(dispatchService, properties)).build();
	}

	@Test
	void acceptsValidRequest() throws Exception {
		mockMvc
			.perform(post("/api/lovebox/messages").header("X-Lovebox-Token", "test-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"command":"send","text":"hello from scheduler","source":"scheduler"}
						"""))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.status").value("accepted"));

		verify(dispatchService).dispatchText(null, "hello from scheduler");
	}

	@Test
	void rejectsMissingToken() throws Exception {
		mockMvc.perform(post("/api/lovebox/messages").contentType(MediaType.APPLICATION_JSON).content("""
				{"command":"send","text":"hello from scheduler"}
				""")).andExpect(status().isUnauthorized());

		verifyNoInteractions(dispatchService);
	}

	@Test
	void rejectsBlankText() throws Exception {
		mockMvc
			.perform(post("/api/lovebox/messages").header("X-Lovebox-Token", "test-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"command":"send","text":"   "}
						"""))
			.andExpect(status().isBadRequest());

		verifyNoInteractions(dispatchService);
	}

	@Test
	void rejectsUnsupportedCommand() throws Exception {
		mockMvc
			.perform(post("/api/lovebox/messages").header("X-Lovebox-Token", "test-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"command":"preview","text":"hello from scheduler"}
						"""))
			.andExpect(status().isBadRequest());

		verifyNoInteractions(dispatchService);
	}

}
