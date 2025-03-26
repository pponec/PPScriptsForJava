package net.ponec.script;

import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * The MirrorBorder class extends an image by mirroring its edges and filling the corners.
 *
 * <p>It takes two arguments: the border width and the image file path.
 * The output is a new image with mirrored borders saved as "output_with_mirrored_borders.png".</p>
 *
 * <p>Usage: {@code java MirrorBorder <border-width> <image-file>}</p>
 */
public class MirrorBorder {
    public static void main(String[] args) {
        new MirrorBorder()._main(args);
    }

    public void _main(String... args) {
        if (args.length != 2) {
            System.out.printf("Usage: java %s.java <border-width> <image-file>%n",
                    getClass().getSimpleName());
            return;
        }

        // Parse command-line arguments
        var borderWidth = Integer.parseInt(args[0]);
        var inputImagePath = Path.of(args[1]);
        var outputImagePath = inputImagePath
                .toAbsolutePath()
                .getParent()
                .resolve("mirrored_borders.png");

        try {
            // Load the input image
            var originalImage = ImageIO.read(inputImagePath.toFile());

            // Get image dimensions
            var width = originalImage.getWidth();
            var height = originalImage.getHeight();

            // Calculate new dimensions including the mirrored borders
            var newWidth = width + 2 * borderWidth;
            var newHeight = height + 2 * borderWidth;

            // Create a new image with the extended size
            var newImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
            var g2d = newImage.createGraphics();

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

            // Save the new image
            ImageIO.write(newImage, "PNG", outputImagePath.toFile());
            System.out.println("Image saved as " + outputImagePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Method to create a mirrored version of a given image section
    private BufferedImage createMirrorImage(BufferedImage sourceImage, boolean verticalFlip) {
        var width = sourceImage.getWidth();
        var height = sourceImage.getHeight();
        var result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var g2d = result.createGraphics();

        if (verticalFlip) {
            // Flip vertically (for left and right borders)
            g2d.drawImage(sourceImage, 0, 0, width, height, width - 1, 0, 0, height, null);
        } else {
            // Flip horizontally (for top and bottom borders)
            g2d.drawImage(sourceImage, 0, 0, width, height, 0, height - 1, width, 0, null);
        }

        g2d.dispose();
        return result;
    }
}
