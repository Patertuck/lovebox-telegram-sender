package com.patbaumgartner.lovebox.telegram.sender.scheduler;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "integration")
public class SchedulerIntegrationProperties {

	/* Shared secret for scheduler-triggered HTTP requests. */
	private String schedulerToken;

	/* Path to the SQLite database containing scheduled messages. */
	private String messagesDbPath = "data/messages.db";

}
