package tcraft;

import javafx.scene.Node;

public interface Page {
    default Node left() {
        return null;
    }

    default double leftWidth() {
        return 0;
    }

    Node right();

    default String key() {
        return "";
    }

    default String sectionKey() {
        return null;
    }

    default void onHide() {
    }
}
