package com.patbaumgartner.lovebox.telegram.sender.outbox;

public record OutboxMessage(long id, String imageAsBase64, String remoteMessageId) {
}
