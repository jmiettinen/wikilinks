package fi.eonwe.wikilinks.utils;

/**
 */
public class FixedSizeIntQueue {

    private int head;
    private int tail;
    private final int[] buffer;

    public FixedSizeIntQueue(int maxLength) {
        this.buffer = new int[maxLength + 1];
    }

    public boolean addLast(int value) {
        final int oldTail = this.tail;
        final int[] buffer = this.buffer;
        final int len = buffer.length;
        int newTail = incrementIndex(oldTail, len);
        if (newTail == this.head) {
            if (size() + 1 == len) {
                return false;
            }
            newTail = incrementIndex(oldTail, len);
        }
        buffer[oldTail] = value;
        this.tail = newTail;
        return true;
    }

    public int removeFirst() {
        final int oldHead = this.head;
        final int[] buffer = this.buffer;
        final int result = buffer[oldHead];

        this.head = incrementIndex(oldHead, buffer.length);
        return result;
    }

    public int peekFirst() {
        return this.buffer[this.head];
    }

    private static int incrementIndex(int oldIndex, int onePastMax) {
        int nextIndex = oldIndex + 1;
        if (nextIndex == onePastMax) return 0;
        return nextIndex;
    }

    public int size() {
        final int head = this.head;
        final int tail = this.tail;
        int size = tail - head;
        return size < 0 ? size + buffer.length: size;
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public int capacity() {
        return buffer.length - 1;
    }

}
