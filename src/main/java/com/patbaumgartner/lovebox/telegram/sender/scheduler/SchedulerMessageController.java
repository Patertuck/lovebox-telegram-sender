package com.patbaumgartner.lovebox.telegram.sender.scheduler;

import com.patbaumgartner.lovebox.telegram.sender.services.LoveboxMessageDispatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/lovebox/messages")
public class SchedulerMessageController {

	private static final String SCHEDULER_TOKEN_HEADER = "X-Lovebox-Token";

	private final LoveboxMessageDispatchService dispatchService;

	private final SchedulerIntegrationProperties integrationProperties;

	@PostMapping
	public ResponseEntity<Map<String, String>> sendMessage(
			@RequestHeader(name = SCHEDULER_TOKEN_HEADER, required = false) String schedulerToken,
			@RequestBody(required = false) SchedulerMessageRequest request) {
		if (!StringUtils.hasText(integrationProperties.getSchedulerToken())
				|| !integrationProperties.getSchedulerToken().equals(schedulerToken)) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid scheduler token");
		}
		if (request == null || !StringUtils.hasText(request.text())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Text must not be blank");
		}
		if (StringUtils.hasText(request.command()) && !"send".equalsIgnoreCase(request.command().trim())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported command");
		}

		dispatchService.dispatchText(null, request.text());
		return ResponseEntity.accepted().body(Map.of("status", "accepted"));
	}

}
