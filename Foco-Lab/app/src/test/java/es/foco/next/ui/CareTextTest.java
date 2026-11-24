package es.foco.next.ui;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.Test;

/**
 * Cuidado, principio 2: ningún texto visible usa "fallaste" ni "perdiste", y no hay rojo en la
 * interfaz. Recorre el código y los recursos de la app (el directorio de trabajo de las pruebas es
 * el del módulo). Rojo = color opaco con mucho rojo y poco verde y azul.
 */
public class CareTextTest {
    private static final Pattern HEX = Pattern.compile("(?:0x|#)([0-9A-Fa-f]{8}|[0-9A-Fa-f]{6})\\b");

    private static List<Path> sources() throws IOException {
        Path root = Paths.get("src/main");
        if (!Files.exists(root)) root = Paths.get("app/src/main");
        List<Path> out = new ArrayList<>();
        try (Stream<Path> s = Files.walk(root)) {
            s.filter(p -> {
                        String n = p.toString();
                        return n.endsWith(".java") || n.endsWith(".kt") || n.endsWith(".xml");
                    })
                    .forEach(out::add);
        }
        return out;
    }

    @Test
    public void sinLenguajeDeFallaNiRojo() throws IOException {
        List<String> bad = new ArrayList<>();
        for (Path p : sources()) {
            String text = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
            String low = text.toLowerCase(Locale.ROOT);
            if (low.contains("fallaste") || low.contains("perdiste")) bad.add(p + ": texto de falla");
            if (text.contains("Color.RED")) bad.add(p + ": Color.RED");
            Matcher m = HEX.matcher(text);
            while (m.find()) {
                String h = m.group(1);
                int alpha = h.length() == 8 ? Integer.parseInt(h.substring(0, 2), 16) : 255;
                String rgb = h.substring(h.length() - 6);
                int r = Integer.parseInt(rgb.substring(0, 2), 16),
                        g = Integer.parseInt(rgb.substring(2, 4), 16),
                        b = Integer.parseInt(rgb.substring(4, 6), 16);
                if (alpha >= 0x40 && r >= 0xB0 && g <= 0x70 && b <= 0x70) bad.add(p + ": rojo " + m.group());
            }
        }
        assertTrue(String.join("\n", bad), bad.isEmpty());
    }
}
