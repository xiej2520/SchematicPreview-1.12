package dev.froyln.schematicpreview.gui;

import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;

import net.minecraft.client.MinecraftClient;

/** Saves or copies a rendered schematic preview image. */
public final class ScreenshotUtil
{
    private ScreenshotUtil()
    {
    }

    @Nullable
    public static File save(BufferedImage image, File schematic)
    {
        try
        {
            Path directory = MinecraftClient.getInstance().runDirectory.toPath()
                    .resolve("screenshots").resolve("schematicpreview");
            Files.createDirectories(directory);

            String name = schematic.getName();
            int dot = name.lastIndexOf('.');

            if (dot > 0)
            {
                name = name.substring(0, dot);
            }

            name = name.replaceAll("[^A-Za-z0-9_-]", "_");
            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            File output = directory.resolve(name + "_" + timestamp + ".png").toFile();
            ImageIO.write(image, "png", output);
            return output;
        }
        catch (IOException e)
        {
            return null;
        }
    }

    public static boolean copyToClipboard(BufferedImage image)
    {
        if (System.getenv("WAYLAND_DISPLAY") != null && copyViaWlCopy(image))
        {
            return true;
        }

        // AWT clipboard access is not reliable from a GLFW client on Wayland.  Try the
        // X11 clipboard helpers as well, which covers XWayland sessions and older desktop
        // environments where the Java toolkit owns no display connection.
        if (System.getenv("DISPLAY") != null && copyViaCommand(image,
                "xclip", "-selection", "clipboard", "-t", "image/png", "-i"))
        {
            return true;
        }

        try
        {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(new TransferableImage(toOpaque(image)), null);
            return true;
        }
        catch (Exception e)
        {
            return false;
        }
    }

    private static boolean copyViaWlCopy(BufferedImage image)
    {
        return copyViaCommand(image, "wl-copy", "--type", "image/png");
    }

    private static boolean copyViaCommand(BufferedImage image, String... command)
    {
        try
        {
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(image, "png", png);

            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.PIPE)
                    .start();

            try (OutputStream stdin = process.getOutputStream())
            {
                png.writeTo(stdin);
            }

            boolean finished = process.waitFor(10, TimeUnit.SECONDS);

            if (finished == false)
            {
                process.destroy();
                return false;
            }

            // Drain the merged error stream after the process exits.  The helper normally
            // prints nothing, but this also prevents a diagnostic pipe from being left open.
            while (process.getInputStream().read() >= 0)
            {
                // drain
            }

            return process.exitValue() == 0;
        }
        catch (IOException e)
        {
            return false;
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static BufferedImage toOpaque(BufferedImage image)
    {
        BufferedImage opaque = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = opaque.createGraphics();
        graphics.setColor(new java.awt.Color(13, 13, 13));
        graphics.fillRect(0, 0, opaque.getWidth(), opaque.getHeight());
        graphics.drawImage(image, 0, 0, null);
        graphics.dispose();
        return opaque;
    }

    private static final class TransferableImage implements Transferable
    {
        private final BufferedImage image;

        private TransferableImage(BufferedImage image)
        {
            this.image = image;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors()
        {
            return new DataFlavor[] { DataFlavor.imageFlavor };
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor)
        {
            return DataFlavor.imageFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException
        {
            if (this.isDataFlavorSupported(flavor) == false)
            {
                throw new UnsupportedFlavorException(flavor);
            }

            return this.image;
        }
    }
}
