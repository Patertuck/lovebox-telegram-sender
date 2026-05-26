package com.patbaumgartner.lovebox.telegram.sender.scheduler;

public record SchedulerMessageRequest(String command, String text, String source) {

}
