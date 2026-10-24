package com.menta.bff.infrastructure.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards what gets committed under {@code static/images} (#654): the repository is public
 * and history is forever, so unoptimized, unused or oddly named media must not slip in.
 */
@DisplayName("Static images budget")
class StaticImagesBudgetTest {

    private static final Path STATIC = Path.of("src/main/resources/static");
    private static final Path IMAGES = STATIC.resolve("images");
    private static final long PHOTO_BUDGET_BYTES = 2L * 1024 * 1024;
    private static final long SINGLE_IMAGE_BYTES = 300L * 1024;
    private static final long VIDEO_BYTES = 250L * 1024;

    @Test
    @DisplayName("los nombres de archivo son en minúscula, sin espacios ni caracteres raros")
    void fileNamesAreWebFriendly() throws IOException {
        assertThat(files(STATIC)).allSatisfy(file -> assertThat(file.getFileName().toString())
                .matches("[a-z0-9][a-z0-9.-]*"));
    }

    @Test
    @DisplayName("las imágenes pesan poco: ninguna pasa los 300 KB y el conjunto queda bajo los 2 MB")
    void imagesStayWithinTheBudget() throws IOException {
        List<Path> images = files(IMAGES).stream().filter(file -> !isVideo(file)).toList();

        assertThat(images).isNotEmpty().allSatisfy(file -> assertThat(Files.size(file))
                .as(file.toString()).isLessThanOrEqualTo(SINGLE_IMAGE_BYTES));
        assertThat(images.stream().mapToLong(StaticImagesBudgetTest::size).sum())
                .isLessThanOrEqualTo(PHOTO_BUDGET_BYTES);
    }

    @Test
    @DisplayName("el video es mp4 liviano: no se commitean .mov ni videos de más de 250 KB")
    void videoIsLight() throws IOException {
        List<Path> videos = files(IMAGES).stream().filter(StaticImagesBudgetTest::isVideo).toList();

        assertThat(videos).isNotEmpty().allSatisfy(file -> {
            assertThat(file.getFileName().toString()).endsWith(".mp4");
            assertThat(Files.size(file)).as(file.toString()).isLessThanOrEqualTo(VIDEO_BYTES);
        });
    }

    @Test
    @DisplayName("cada archivo de static/images se usa en alguna plantilla o en la hoja de estilos")
    void everyImageIsReferenced() throws IOException {
        String sources = sources();

        assertThat(files(IMAGES)).allSatisfy(file -> assertThat(sources)
                .as("%s is not referenced by any template or stylesheet", file)
                .contains("/" + STATIC.relativize(file)));
    }

    private static String sources() throws IOException {
        StringBuilder all = new StringBuilder();
        for (Path root : List.of(Path.of("src/main/resources/templates"), Path.of("src/main/frontend/css"))) {
            for (Path file : files(root)) {
                all.append(Files.readString(file));
            }
        }
        return all.toString();
    }

    private static List<Path> files(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile).sorted().toList();
        }
    }

    private static boolean isVideo(Path file) {
        String name = file.getFileName().toString();
        return name.endsWith(".mp4") || name.endsWith(".mov") || name.endsWith(".webm");
    }

    private static long size(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
