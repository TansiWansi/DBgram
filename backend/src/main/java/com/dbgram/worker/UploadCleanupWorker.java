package com.dbgram.worker;

import com.dbgram.service.MediaUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class UploadCleanupWorker {
    private final MediaUploadService uploads;

    @Scheduled(fixedDelay = 3600000, initialDelay = 60000)
    public void cleanup() throws IOException {
        uploads.cleanupExpired();
    }
}
