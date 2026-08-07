package com.patbaumgartner.lovebox.telegram.sender.scheduler;

import com.patbaumgartner.lovebox.telegram.sender.rest.clients.LoveboxRestClientProperties;
import com.patbaumgartner.lovebox.telegram.sender.services.ImageService;
import com.patbaumgartner.lovebox.telegram.sender.services.LoveboxMessageDispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class FallbackPictureService {

	private final MessageProperties messageProperties;

	private final LoveboxRestClientProperties loveboxProperties;

	private final ImageService imageService;

	private final LoveboxMessageDispatchService dispatchService;

	public boolean sendRandomPicture() {
		if (!loveboxProperties.isEnabled()) {
			log.info("Lovebox sending is disabled; skipped fallback picture submission.");
			return false;
		}

		Path picturesDirectory = picturesDirectory();
		List<Path> candidates = findCandidates(picturesDirectory);
		Collections.shuffle(candidates);
		for (Path candidate : candidates) {
			Path pendingPicture;
			try {
				pendingPicture = reserve(candidate, picturesDirectory.resolve(".sending"));
			}
			catch (IOException e) {
				log.warn("Could not reserve fallback picture {}. Trying another picture.", candidate.getFileName(), e);
				continue;
			}

			try {
				dispatchService.dispatchImage(imageService.resizeImageToBase64(pendingPicture.toFile(), null));
			}
			catch (RuntimeException e) {
				returnToPicturesDirectory(pendingPicture, picturesDirectory);
				log.warn("Could not submit fallback picture {}. Trying another picture.", candidate.getFileName(), e);
				continue;
			}

			try {
				Path archivedPicture = moveToDirectory(pendingPicture, picturesDirectory.resolve("sent"));
				deleteOriginal(candidate, archivedPicture);
			}
			catch (IOException e) {
				log.error("Submitted fallback picture {}, but could not archive it. It remains in .sending to prevent reuse.",
						candidate.getFileName(), e);
			}
			log.info("Submitted fallback picture {}.", candidate.getFileName());
			return true;
		}

		log.info("No usable fallback pictures found in {}.", picturesDirectory);
		return false;
	}

	private List<Path> findCandidates(Path picturesDirectory) {
		if (!Files.isDirectory(picturesDirectory)) {
			return new ArrayList<>();
		}

		try (var files = Files.list(picturesDirectory)) {
			return new ArrayList<>(files.filter(Files::isRegularFile).filter(this::isSupportedImage)
				.filter(path -> isUnused(path, picturesDirectory)).toList());
		}
		catch (IOException e) {
			log.warn("Could not read fallback picture directory {}.", picturesDirectory, e);
			return new ArrayList<>();
		}
	}

	private Path picturesDirectory() {
		if (messageProperties.getPicturesPath() != null && !messageProperties.getPicturesPath().isBlank()) {
			return Path.of(messageProperties.getPicturesPath()).toAbsolutePath().normalize();
		}
		Path databasePath = Path.of(messageProperties.getDatabasePath()).toAbsolutePath().normalize();
		return databasePath.getParent().resolve("pictures");
	}

	private boolean isSupportedImage(Path path) {
		String filename = path.getFileName().toString().toLowerCase(Locale.ROOT);
		return filename.endsWith(".jpg") || filename.endsWith(".jpeg") || filename.endsWith(".png");
	}

	private boolean isUnused(Path picture, Path picturesDirectory) {
		String filename = picture.getFileName().toString();
		return !Files.exists(picturesDirectory.resolve(".sending").resolve(filename))
				&& !Files.exists(picturesDirectory.resolve("sent").resolve(filename));
	}

	private Path reserve(Path source, Path sendingDirectory) throws IOException {
		Files.createDirectories(sendingDirectory);
		Path target = sendingDirectory.resolve(source.getFileName());
		if (Files.exists(target)) {
			throw new IOException("Picture is already reserved: " + source.getFileName());
		}
		return Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
	}

	private Path moveToDirectory(Path source, Path destinationDirectory) throws IOException {
		Files.createDirectories(destinationDirectory);
		Path target = destinationDirectory.resolve(source.getFileName());
		if (Files.exists(target)) {
			throw new IOException("Picture is already archived: " + source.getFileName());
		}
		return Files.move(source, target);
	}

	private void returnToPicturesDirectory(Path pendingPicture, Path picturesDirectory) {
		try {
			Files.deleteIfExists(pendingPicture);
		}
		catch (IOException e) {
			log.error("Could not return failed fallback picture {}. It remains in .sending to prevent reuse.",
					pendingPicture.getFileName(), e);
		}
	}

	private void deleteOriginal(Path originalPicture, Path archivedPicture) {
		try {
			Files.deleteIfExists(originalPicture);
		}
		catch (IOException e) {
			log.warn("Could not delete submitted fallback picture {}. It is ignored because it was archived as {}.",
					originalPicture.getFileName(), archivedPicture.getFileName(), e);
		}
	}

}
