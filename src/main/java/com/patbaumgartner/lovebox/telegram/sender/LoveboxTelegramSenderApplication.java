package com.patbaumgartner.lovebox.telegram.sender;

import com.patbaumgartner.lovebox.telegram.sender.rest.clients.LoveboxRestClientProperties;
import com.patbaumgartner.lovebox.telegram.sender.scheduler.MessageProperties;
import com.patbaumgartner.lovebox.telegram.sender.telegram.LoveboxBotProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Profiles;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties({ LoveboxRestClientProperties.class, LoveboxBotProperties.class,
		MessageProperties.class })
public class LoveboxTelegramSenderApplication {

	public static void main(String[] args) {
		ConfigurableApplicationContext context = SpringApplication.run(LoveboxTelegramSenderApplication.class, args);
		if (context.getEnvironment().acceptsProfiles(Profiles.of("import"))) {
			System.exit(SpringApplication.exit(context));
		}
	}

}
