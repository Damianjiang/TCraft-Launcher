package tcraft;

import java.util.AbstractList;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

// 定长日志缓冲：满了丢最旧一条，写入和淘汰都是 O(1)
final class LogRing extends AbstractList<String> {

    private final int capacity;
    private final ArrayDeque<String> items;

    LogRing(int capacity) {
        this.capacity = Math.max(1, capacity);
        this.items = new ArrayDeque<>(this.capacity + 1);
    }

    @Override
    public synchronized boolean add(String value) {
        if (items.size() >= capacity) items.pollFirst();
        items.addLast(value);
        modCount++;
        return true;
    }

    @Override
    public synchronized String get(int index) {
        if (index < 0 || index >= items.size()) throw new IndexOutOfBoundsException("index " + index);
        int i = 0;
        for (String s : items) {
            if (i++ == index) return s;
        }
        throw new IndexOutOfBoundsException("index " + index);
    }

    @Override
    public synchronized int size() {
        return items.size();
    }

    @Override
    public synchronized void clear() {
        items.clear();
        modCount++;
    }

    @Override
    public synchronized boolean removeIf(Predicate<? super String> filter) {
        boolean removed = items.removeIf(filter);
        if (removed) modCount++;
        return removed;
    }

    // 返回快照迭代器，遍历不长期持锁
    @Override
    public synchronized Iterator<String> iterator() {
        return new java.util.ArrayList<>(items).iterator();
    }

    // 取最后 last 行的拷贝。subList 迭代会比对 modCount，游戏运行中后台
    // 还在写日志，页面构建时直接迭代会抛 ConcurrentModificationException
    public synchronized List<String> snapshot(int last) {
        ArrayList<String> copy = new ArrayList<>(items);
        int from = Math.max(0, copy.size() - Math.max(0, last));
        return copy.subList(from, copy.size());
    }
}
