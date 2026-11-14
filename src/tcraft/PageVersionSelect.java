package tcraft;

import javafx.scene.Node;

/** 版本选择页，点击即选中并返回首页。 */
public class PageVersionSelect implements Page {

    private final MainWindow win;
    private final PageInstance inner;

    public PageVersionSelect(MainWindow win) {
        this.win = win;
        this.inner = new PageInstance(win, true);
    }

    @Override
    public double leftWidth() {
        return 300;
    }

    @Override
    public Node left() {
        return PageInstance.buildFolderPanel(win);
    }

    @Override
    public Node right() {
        return inner.right();
    }
}
