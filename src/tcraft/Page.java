package tcraft;

import javafx.scene.Node;

/** 双栏页面：左栏可选，右栏是主内容。 */
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

    /** 左栏该选中哪一项，和 Ui.ListItem 的 userData 对应。 */
    default String sectionKey() {
        return null;
    }

    /** 页面被换掉时调，用来停计时器。 */
    default void onHide() {
    }
}
