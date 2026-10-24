import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 生成 PicTool 的 Windows 图标 (pictool.ico)。
 * 用法: java IconGenerator.java [输出路径.ico]
 */
public class IconGenerator {

    public static void main(String[] args) throws Exception {
        int[] sizes = {256, 48, 32, 16};
        byte[][] pngs = new byte[sizes.length][];
        for (int i = 0; i < sizes.length; i++) {
            BufferedImage image = draw(sizes[i]);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", out);
            pngs[i] = out.toByteArray();
        }

        ByteArrayOutputStream ico = new ByteArrayOutputStream();
        writeShort(ico, 0);            // reserved
        writeShort(ico, 1);            // type: icon
        writeShort(ico, sizes.length); // count
        int offset = 6 + 16 * sizes.length;
        for (int i = 0; i < sizes.length; i++) {
            int s = sizes[i];
            ico.write(s >= 256 ? 0 : s);  // width  (0 = 256)
            ico.write(s >= 256 ? 0 : s);  // height (0 = 256)
            ico.write(0);                 // colors
            ico.write(0);                 // reserved
            writeShort(ico, 1);           // planes
            writeShort(ico, 32);          // bit count
            writeInt(ico, pngs[i].length);
            writeInt(ico, offset);
            offset += pngs[i].length;
        }
        for (byte[] png : pngs) {
            ico.write(png);
        }

        Path out = Paths.get(args.length > 0 ? args[0] : "src/packaging/pictool.ico");
        Files.createDirectories(out.toAbsolutePath().getParent());
        Files.write(out, ico.toByteArray());
        System.out.println("written " + out.toAbsolutePath() + " (" + ico.size() + " bytes)");

        // 窗口图标 PNG（打进 jar，供 stage.getIcons() 使用）
        Path resDir = Paths.get("src/main/resources/icons");
        Files.createDirectories(resDir);
        for (int s : sizes) {
            ImageIO.write(draw(s), "PNG", resDir.resolve("pictool-" + s + ".png").toFile());
        }
        System.out.println("icons: " + resDir.toAbsolutePath());
    }

    private static BufferedImage draw(int s) {
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        double r = s * 0.22;
        g.setClip(new RoundRectangle2D.Double(0, 0, s, s, r, r));

        g.setPaint(new GradientPaint(
                0, 0, new Color(0x1E, 0x88, 0xE5),
                0, s, new Color(0x0D, 0x47, 0xA1)));
        g.fillRect(0, 0, s, s);

        // 后山
        g.setColor(new Color(255, 255, 255, 130));
        Path2D back = new Path2D.Double();
        back.moveTo(s * 0.48, s * 0.86);
        back.lineTo(s * 0.78, s * 0.40);
        back.lineTo(s * 1.04, s * 0.86);
        back.closePath();
        g.fill(back);

        // 前山
        g.setColor(new Color(255, 255, 255, 242));
        Path2D front = new Path2D.Double();
        front.moveTo(-s * 0.04, s * 0.88);
        front.lineTo(s * 0.34, s * 0.38);
        front.lineTo(s * 0.72, s * 0.88);
        front.closePath();
        g.fill(front);

        // 太阳
        g.setColor(new Color(255, 255, 255, 235));
        g.fill(new Ellipse2D.Double(s * 0.24, s * 0.22, s * 0.20, s * 0.20));

        g.dispose();
        return img;
    }

    private static void writeShort(ByteArrayOutputStream out, int v) {
        out.write(v & 0xFF);
        out.write((v >>> 8) & 0xFF);
    }

    private static void writeInt(ByteArrayOutputStream out, long v) {
        out.write((int) (v & 0xFF));
        out.write((int) ((v >>> 8) & 0xFF));
        out.write((int) ((v >>> 16) & 0xFF));
        out.write((int) ((v >>> 24) & 0xFF));
    }
}
