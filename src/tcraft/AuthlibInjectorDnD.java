package tcraft;

import javafx.event.EventHandler;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Consumer;

public final class AuthlibInjectorDnD {

    private static final String SCHEME = "authlib-injector";
    private static final String PATH = "yggdrasil-server";

    private AuthlibInjectorDnD() {
    }

    public static Optional<String> parse(Dragboard board) {
        if (board == null || !board.hasString()) return Optional.empty();
        return parseText(board.getString());
    }

    static Optional<String> parseText(String raw) {
        if (raw == null) return Optional.empty();
        String text = raw.trim();
        String prefix = SCHEME + ":" + PATH + ":";
        if (!text.startsWith(prefix)) return Optional.empty();
        String encoded = text.substring(prefix.length());
        if (encoded.isEmpty()) return Optional.empty();
        try {
            String url = URLDecoder.decode(encoded, StandardCharsets.UTF_8).trim();
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (scheme == null || uri.getHost() == null) return Optional.empty();
            if (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")) {
                return Optional.empty();
            }
            return Optional.of(url);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public static EventHandler<DragEvent> dragOverHandler() {
        return event -> parse(event.getDragboard()).ifPresent(url -> {
            event.acceptTransferModes(TransferMode.COPY);
            event.consume();
        });
    }

    public static EventHandler<DragEvent> dragDroppedHandler(Consumer<String> onUrl) {
        return event -> parse(event.getDragboard()).ifPresent(url -> {
            event.setDropCompleted(true);
            event.consume();
            onUrl.accept(url);
        });
    }
}
