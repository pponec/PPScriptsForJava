// Licence: Apache License, Version 2.0, https://github.com/pponec/

package net.ponec.script;

import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.IIOImage;
import java.nio.file.Files;

/**
 * The ImageBorderMirror class extends an image by mirroring its edges and filling the corners.
 *
 * <p>It takes two arguments: the border width and the image file path.
 * The output is a new image with mirrored borders saved as "output_with_mirrored_borders.png".</p>
 *
 * <p>Usage: {@code java MirrorBorder <image-file> [border-width] }</p>
 */
public class ImageBorderMirror {
    public static void main(String[] args) {
        try {
            new ImageBorderMirror()._main(args);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public void _main(String... args) throws IOException {
        if (args.length == 0) {
            System.out.printf("Usage: java %s.java <image-file> [border-width]%n",
                    getClass().getSimpleName());
            return;
        }

        // Parse command-line arguments
        var inputImagePath = Path.of(args[0]);
        var borderWidth = args.length < 2 ? 200 : Integer.parseInt(args[1]);
        var outputImagePath = outputImagePath(inputImagePath);

        // Load the input image
        var originalImage = ImageIO.read(inputImagePath.toFile());
        var newImage = createNewImage(originalImage, borderWidth);

        // Save the new image
        writeJpeg(newImage, outputImagePath);
        System.out.println("Image saved as " + outputImagePath);
    }

    private BufferedImage createNewImage(BufferedImage originalImage, int borderWidth) {
        // Get image dimensions
        var width = originalImage.getWidth();
        var height = originalImage.getHeight();

        // Calculate new dimensions including the mirrored borders
        var newWidth = width + 2 * borderWidth;
        var newHeight = height + 2 * borderWidth;

        // Create a new image with the extended size
        var result = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        var g2d = result.createGraphics();

        // Set background color (white)
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, newWidth, newHeight);

        // Mirror left border
        var leftBorder = createMirrorImage(originalImage.getSubimage(0, 0, borderWidth, height), true);
        g2d.drawImage(leftBorder, 0, borderWidth, null);

        // Mirror right border
        var rightBorder = createMirrorImage(originalImage.getSubimage(width - borderWidth, 0, borderWidth, height), true);
        g2d.drawImage(rightBorder, newWidth - borderWidth, borderWidth, null);

        // Mirror top border (flipped horizontally)
        var topBorder = createMirrorImage(originalImage.getSubimage(0, 0, width, borderWidth), false);
        g2d.drawImage(topBorder, borderWidth, 0, null);

        // Mirror bottom border (flipped horizontally)
        var bottomBorder = createMirrorImage(originalImage.getSubimage(0, height - borderWidth, width, borderWidth), false);
        g2d.drawImage(bottomBorder, borderWidth, newHeight - borderWidth, null);

        // Draw original image in the center
        g2d.drawImage(originalImage, borderWidth, borderWidth, null);

        // Fill corners using mirrors
        g2d.drawImage(createMirrorImage(leftBorder.getSubimage(0, 0, borderWidth, borderWidth), false), 0, 0, null);
        g2d.drawImage(createMirrorImage(rightBorder.getSubimage(0, 0, borderWidth, borderWidth), false), newWidth - borderWidth, 0, null);
        g2d.drawImage(createMirrorImage(leftBorder.getSubimage(0, height - borderWidth, borderWidth, borderWidth), false), 0, newHeight - borderWidth, null);
        g2d.drawImage(createMirrorImage(rightBorder.getSubimage(0, height - borderWidth, borderWidth, borderWidth), false), newWidth - borderWidth, newHeight - borderWidth, null);

        // Release resources
        g2d.dispose();
        return result;
    }

    private static Path outputImagePath(Path inputImagePath) {
        var name = inputImagePath.getFileName().toString();
        var dotIndex = name.lastIndexOf('.');
        var outName = "%s_mirrored%s".formatted(
                name.substring(0, dotIndex),
                name.substring(dotIndex));
        return inputImagePath
                .toAbsolutePath()
                .getParent()
                .resolve(outName);
    }

    private static void writeJpeg(BufferedImage image, Path outFile) throws IOException {
        var writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        var writeParam = writer.getDefaultWriteParam();
        writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        writeParam.setCompressionQuality(0.9f);
        writeParam.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);

        try (OutputStream os = Files.newOutputStream(outFile);
             var ios = ImageIO.createImageOutputStream(os)) {
            writer.setOutput(ios);
            var iimage = new IIOImage(image, null, null);
            writer.write(null, iimage, writeParam);
        }
    }

    /**
     * Method to create a mirrored version of a given image section
     *
     * @param sourceImage A source image.
     * @param verticalFlip Flip vertically for left and right borders (else horizontally).
     * @return
     */
    private BufferedImage createMirrorImage(BufferedImage sourceImage, boolean verticalFlip) {
        var width = sourceImage.getWidth();
        var height = sourceImage.getHeight();
        var result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var g2d = result.createGraphics();

        if (verticalFlip) {
            g2d.drawImage(sourceImage, 0, 0, width, height, width - 1, 0, 0, height, null);
        } else {
            g2d.drawImage(sourceImage, 0, 0, width, height, 0, height - 1, width, 0, null);
        }

        g2d.dispose();
        return result;
    }
}
