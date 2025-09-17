package net.ponec.script;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

class ImageBorderMirrorTest {

    @TempDir
    Path tempDir;

    /**
     * Test for the _main method, which is the main implementation method of ImageBorderMirror
     */
    @Test
    void testMain() throws IOException {
        // Prepare test image
        var testImage = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
        var testImagePath = tempDir.resolve("test_image.jpg");
        ImageIO.write(testImage, "jpg", testImagePath.toFile());

        // Capture console output
        var outContent = new ByteArrayOutputStream();
        var originalOut = System.out;
        System.setOut(new PrintStream(outContent));

        try {
            // Create instance and call the tested method
            var mirror = new ImageBorderMirror();
            mirror._main(testImagePath.toString(), "50");

            // Check that the output file was created
            var expectedOutputPath = tempDir.resolve("test_image_mirrored.jpg");
            assertTrue(Files.exists(expectedOutputPath), "Output file should be created");

            // Check that the output file has correct parameters
            var outputImage = ImageIO.read(expectedOutputPath.toFile());
            assertNotNull(outputImage, "Output image should be valid");

            // Check dimensions of the output image (original + 2*border)
            assertEquals(testImage.getWidth() + 2 * 50, outputImage.getWidth(),
                    "Width of the output image should be original width + 2*border");
            assertEquals(testImage.getHeight() + 2 * 50, outputImage.getHeight(),
                    "Height of the output image should be original height + 2*border");

            // Check console output
            var consoleOutput = outContent.toString();
            assertTrue(consoleOutput.contains("Image saved as"),
                    "Output should contain information about saving the image");
            assertTrue(consoleOutput.contains(expectedOutputPath.toString()),
                    "Output should contain the path to the output file");
        } finally {
            // Restore original System.out
            System.setOut(originalOut);
        }
    }

    /**
     * Test for the behavior of _main method with missing arguments
     */
    @Test
    void testMainWithoutArguments() throws IOException {
        var outContent = new ByteArrayOutputStream();
        var originalOut = System.out;
        System.setOut(new PrintStream(outContent));

        try {
            // Call method without arguments
            var mirror = new ImageBorderMirror();
            mirror._main();

            // Check console output - help information should be displayed
            var consoleOutput = outContent.toString();
            assertTrue(consoleOutput.contains("Usage:"),
                    "Output should contain usage information");
            assertTrue(consoleOutput.contains("<image-file>"),
                    "Output should contain information about parameters");
        } finally {
            System.setOut(originalOut);
        }
    }

    /**
     * Test for the behavior of _main method when using the default border width
     */
    @Test
    void testMainWithDefaultBorderWidth() throws IOException {
        // Prepare test image
        var testImage = new BufferedImage(500, 500, BufferedImage.TYPE_INT_RGB);
        var testImagePath = tempDir.resolve("default_border_test.jpg");
        ImageIO.write(testImage, "jpg", testImagePath.toFile());

        // Create instance and call the tested method only with image path
        var mirror = new ImageBorderMirror();
        mirror._main(testImagePath.toString());

        // Check that the output file was created
        var expectedOutputPath = tempDir.resolve("default_border_test_mirrored.jpg");
        assertTrue(Files.exists(expectedOutputPath), "Output file should be created");

        // Check dimensions of the output image (should use default value of 200)
        var outputImage = ImageIO.read(expectedOutputPath.toFile());
        var expectedWidth = testImage.getWidth() + 2 * 200;
        var expectedHeight = testImage.getHeight() + 2 * 200;

        assertEquals(expectedWidth, outputImage.getWidth(),
                "Width of the output image should be original width + 2*default border");
        assertEquals(expectedHeight, outputImage.getHeight(),
                "Height of the output image should be original height + 2*default border");
    }

    /**
     * Test for handling too large requested border width
     */
    @Test
    void testMainWithTooLargeBorderWidth() throws IOException {
        // Prepare small test image
        var testImage = new BufferedImage(100, 80, BufferedImage.TYPE_INT_RGB);
        var testImagePath = tempDir.resolve("small_test.jpg");
        ImageIO.write(testImage, "jpg", testImagePath.toFile());

        // Create instance and call the tested method with too large border
        var mirror = new ImageBorderMirror();
        mirror._main(testImagePath.toString(), "1000");

        // Check that the output file was created
        var expectedOutputPath = tempDir.resolve("small_test_mirrored.jpg");
        assertTrue(Files.exists(expectedOutputPath), "Output file should be created");

        // Check dimensions of the output image (border should be limited to the smaller dimension of the image)
        var outputImage = ImageIO.read(expectedOutputPath.toFile());
        var expectedBorderWidth = Math.min(testImage.getWidth(), testImage.getHeight());
        var expectedWidth = testImage.getWidth() + 2 * expectedBorderWidth;
        var expectedHeight = testImage.getHeight() + 2 * expectedBorderWidth;

        assertEquals(expectedWidth, outputImage.getWidth(),
                "Width of the output image should be original width + 2*adjusted border");
        assertEquals(expectedHeight, outputImage.getHeight(),
                "Height of the output image should be original height + 2*adjusted border");
    }
}
