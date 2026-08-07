package com.patbaumgartner.lovebox.telegram.sender.scheduler;

import com.patbaumgartner.lovebox.telegram.sender.rest.clients.LoveboxRestClientProperties;
import com.patbaumgartner.lovebox.telegram.sender.services.ImageService;
import com.patbaumgartner.lovebox.telegram.sender.services.LoveboxMessageDispatchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class FallbackPictureServiceTest {

	@TempDir
	Path temporaryDirectory;

	@Test
	void sendsAndArchivesAnUnusedPicture() throws Exception {
		Path picturesDirectory = temporaryDirectory.resolve("pictures");
		Files.createDirectories(picturesDirectory);
		Path picture = Files.writeString(picturesDirectory.resolve("photo.png"), "picture");
		FallbackPictureService service = serviceFor(picturesDirectory);

		assertTrue(service.sendRandomPicture());
		assertFalse(service.sendRandomPicture());

		verify(dispatchService, times(1)).dispatchImage("base64");
		assertFalse(Files.exists(picture));
		assertTrue(Files.exists(picturesDirectory.resolve("sent/photo.png")));
	}

	@Test
	void skipsUnsupportedFilesWhenNoPictureIsAvailable() throws Exception {
		Path picturesDirectory = temporaryDirectory.resolve("pictures");
		Files.createDirectories(picturesDirectory);
		Files.writeString(picturesDirectory.resolve("notes.txt"), "not a picture");

		assertFalse(serviceFor(picturesDirectory).sendRandomPicture());

		verifyNoInteractions(imageService, dispatchService);
	}

	@Test
	void skipsUnreadablePictureAndSendsAnotherCandidate() throws Exception {
		Path picturesDirectory = temporaryDirectory.resolve("pictures");
		Files.createDirectories(picturesDirectory);
		Files.writeString(picturesDirectory.resolve("broken.png"), "broken");
		Files.writeString(picturesDirectory.resolve("valid.jpg"), "valid");
		when(imageService.resizeImageToBase64(argThat(file -> file.getName().equals("broken.png")), isNull()))
			.thenThrow(new IllegalStateException("Unreadable image"));

		assertTrue(serviceFor(picturesDirectory).sendRandomPicture());

		assertTrue(Files.exists(picturesDirectory.resolve("broken.png")));
		assertTrue(Files.exists(picturesDirectory.resolve("sent/valid.jpg")));
	}

	@Test
	void returnsPictureToSourceDirectoryWhenSubmissionFails() throws Exception {
		Path picturesDirectory = temporaryDirectory.resolve("pictures");
		Files.createDirectories(picturesDirectory);
		Path picture = Files.writeString(picturesDirectory.resolve("photo.jpg"), "picture");
		doThrow(new IllegalStateException("Lovebox unavailable")).when(dispatchService).dispatchImage("base64");

		assertFalse(serviceFor(picturesDirectory).sendRandomPicture());

		assertTrue(Files.exists(picture));
		assertFalse(Files.exists(picturesDirectory.resolve("sent/photo.jpg")));
	}

	private final ImageService imageService = mock(ImageService.class);

	private final LoveboxMessageDispatchService dispatchService = mock(LoveboxMessageDispatchService.class);

	private FallbackPictureService serviceFor(Path picturesDirectory) {
		MessageProperties messageProperties = new MessageProperties();
		messageProperties.setPicturesPath(picturesDirectory.toString());
		LoveboxRestClientProperties loveboxProperties = new LoveboxRestClientProperties();
		loveboxProperties.setEnabled(true);
		when(imageService.resizeImageToBase64(any(), isNull())).thenReturn("base64");
		return new FallbackPictureService(messageProperties, loveboxProperties, imageService, dispatchService);
	}

}
