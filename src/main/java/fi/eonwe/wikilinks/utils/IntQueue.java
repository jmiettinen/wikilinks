package fi.eonwe.wikilinks.utils;

import java.io.IOException;
import java.util.function.IntConsumer;

import com.google.common.primitives.Ints;

import javax.annotation.Nonnull;

/**
 */
public class IntQueue {

    private final boolean isGrowing;
    private int head;
    private int tail;
    private int[] buffer;

    private IntQueue(int initialSize, boolean isGrowing) {
        this.buffer = new int[initialSize + 1];
        this.isGrowing = isGrowing;
    }

    public static IntQueue growingQueue(int startSize) {
        return new IntQueue(startSize, true);
    }

    @Nonnull
    public static IntQueue fixedSizeQueue(int maxLength) {
        return new IntQueue(maxLength, false);
    }

    public boolean addLast(int value) {
        int oldTail = this.tail;
        int[] buffer = this.buffer;
        int len = buffer.length;
        int newTail = incrementIndex(oldTail, len);
        if (size() == capacity()) {
            if (isGrowing) {
                doGrow();
                oldTail = this.tail;
                buffer = this.buffer;
                len = buffer.length;
            } else {
                return false;
            }
            newTail = incrementIndex(oldTail, len);
        }
        buffer[oldTail] = value;
        this.tail = newTail;
        return true;
    }

    private void doGrow() {
        int newSize = Ints.saturatedCast(Math.max(8, buffer.length * 3L / 2));
        int[] newBuffer = new int[newSize];
        final int[] oldBuffer = this.buffer;
        final int oldHead = head;
        final int oldTail = tail;
        final int newTail = size();
        final int newHead = 0;
        this.buffer = newBuffer;
        if (oldHead < oldTail) {
            System.arraycopy(oldBuffer, oldHead, newBuffer, 0, oldTail - oldHead);
        } else {
            final int sizeToEnd = oldBuffer.length - oldHead;
            System.arraycopy(oldBuffer, oldHead, newBuffer, 0, sizeToEnd);
            System.arraycopy(oldBuffer, 0, newBuffer, sizeToEnd, oldTail);
        }
        this.head = newHead;
        this.tail = newTail;

    }

    public void forEach(IntConsumer consumer) {
        forEach(buffer, head, tail, consumer);
    }

    private static void forEach(int[] buffer, int head, int tail, IntConsumer consumer) {
        final int len = buffer.length;
        for (int current = head; current != tail; current = incrementIndex(current, len)) {
            consumer.accept(buffer[current]);
        }
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
        return size < 0 ? size + buffer.length : size;
    }

    public <T extends Appendable> T printTo(T output) throws IOException {
        final boolean[] isFirst = { true };
        IgnoringAppendable out = new IgnoringAppendable(output);
        out.append("[ ");
        forEach(val -> {
            if (!isFirst[0]) {
                out.append(", ");
            } else {
                isFirst[0] = false;
            }
            out.append(String.valueOf(val));
        });
        out.append(" ]");
        if (out.exception != null) throw out.exception;
        return output;
    }

    private static class IgnoringAppendable {

        private final Appendable wrapped;
        private IOException exception;

        public IgnoringAppendable(Appendable wrapped) {
            this.wrapped = wrapped;
        }

        public void append(CharSequence chars) {
            if (exception == null) {
                try {
                    wrapped.append(chars);
                } catch (IOException e) {
                    exception = e;
                }
            }
        }

    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        try {
            printTo(sb);
        } catch (IOException e) {
            throw new Error(e);
        }
        return sb.toString();
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public int capacity() {
        return buffer.length - 1;
    }

}
