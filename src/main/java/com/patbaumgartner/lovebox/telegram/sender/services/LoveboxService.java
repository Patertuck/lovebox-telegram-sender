package com.patbaumgartner.lovebox.telegram.sender.services;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.patbaumgartner.lovebox.telegram.sender.rest.clients.GraphqlRequestBody;
import com.patbaumgartner.lovebox.telegram.sender.rest.clients.LoginWithPasswordResponseBody;
import com.patbaumgartner.lovebox.telegram.sender.rest.clients.LoginWithPasswordRequestBody;
import com.patbaumgartner.lovebox.telegram.sender.rest.clients.LoveboxRestClient;
import com.patbaumgartner.lovebox.telegram.sender.rest.clients.LoveboxRestClientProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoveboxService {

	private final LoveboxRestClientProperties restClientProperties;

	private final LoveboxRestClient restClient;

	public void sendImageMessage(String imageAsBase64) {
		if (!restClientProperties.isEnabled()) {
			log.info("Lovebox sending is disabled; skipped message submission.");
			return;
		}

		String mutation = """
				mutation sendPixNote($channel: ChannelsTypes, $appVersion: String, $base64: String, $recipient: String,
						$options: JSON, $contentType: [String], $timezone: Int) {
				  sendPixNote(channel: $channel, appVersion: $appVersion, base64: $base64, recipient: $recipient,
						options: $options, contentType: $contentType, timezone: $timezone) { _id }
				}
				""";
		Map<String, Object> variables = new HashMap<>();
		variables.put("channel", "LOVEBOX");
		variables.put("base64", imageAsBase64);
		variables.put("recipient", restClientProperties.getBoxId());
		variables.put("contentType", new Object[] {});
		variables.put("timezone", 60);
		variables.put("appVersion", "5.4.9");
		Map<String, Object> options = new HashMap<>();
		options.put("framesBase64", null);
		options.put("deviceId", restClientProperties.getDeviceId());
		options.put("privacyPolicy", "ADMIN_AND_ME");
		options.put("templateId", null);
		variables.put("options", options);

		ResponseEntity<String> response = restClient.graphql("Bearer " + loginAndResolveToken(),
				new GraphqlRequestBody("sendPixNote", variables, mutation));
		ensureSubmitted(response.getBody());
	}

	private String loginAndResolveToken() {
		ResponseEntity<LoginWithPasswordResponseBody> response = restClient.loginWithPassword(
				new LoginWithPasswordRequestBody(restClientProperties.getEmail(), restClientProperties.getPassword()));
		if (response.getBody() == null || response.getBody().token() == null) {
			throw new IllegalStateException("Lovebox login did not return an authorization token");
		}
		return response.getBody().token();
	}

	private void ensureSubmitted(String body) {
		if (body == null || body.isBlank()) {
			throw new IllegalStateException("Lovebox send response is empty");
		}
		JsonElement root = JsonParser.parseString(body);
		JsonObject data = root.isJsonObject() ? root.getAsJsonObject().getAsJsonObject("data") : null;
		if (data == null || !data.has("sendPixNote") || data.get("sendPixNote").isJsonNull()) {
			throw new IllegalStateException("Lovebox did not accept the message submission");
		}
	}

}
