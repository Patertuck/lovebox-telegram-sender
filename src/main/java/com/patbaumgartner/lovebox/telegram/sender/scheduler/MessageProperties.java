package com.patbaumgartner.lovebox.telegram.sender.scheduler;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "messages")
public class MessageProperties {

	private String databasePath = "data/messages.db";

	private String documentPath = "message.docx";

}
