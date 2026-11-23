package tcraft;

import java.util.AbstractList;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

// 定长缓冲 满了丢最旧一条
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

    // 快照迭代 不长期持锁
    @Override
    public synchronized Iterator<String> iterator() {
        return new java.util.ArrayList<>(items).iterator();
    }

    // 直接迭代会 CME 先拷贝
    public synchronized List<String> snapshot(int last) {
        ArrayList<String> copy = new ArrayList<>(items);
        int from = Math.max(0, copy.size() - Math.max(0, last));
        return copy.subList(from, copy.size());
    }
}
