package com.patbaumgartner.lovebox.telegram.sender.scheduler;

import java.time.LocalDate;

public record ScheduledMessage(LocalDate sendDate, String message) {

}
