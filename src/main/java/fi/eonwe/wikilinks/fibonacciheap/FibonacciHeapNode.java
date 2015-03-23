package fi.eonwe.wikilinks.fibonacciheap;

/**
 */
public class FibonacciHeapNode<T>
{
    //~ Instance fields --------------------------------------------------------

    /**
     * Node data.
     */
    T data;

    /**
     * first child node
     */
    FibonacciHeapNode<T> child;

    /**
     * left sibling node
     */
    FibonacciHeapNode<T> left;

    /**
     * parent node
     */
    FibonacciHeapNode<T> parent;

    /**
     * right sibling node
     */
    FibonacciHeapNode<T> right;

    /**
     * true if this node has had a child removed since this node was added to
     * its parent
     */
    boolean mark;

    /**
     * key value for this node
     */
    double key;

    /**
     * number of children of this node (does not count grandchildren)
     */
    int degree;

    //~ Constructors -----------------------------------------------------------

    /**
     * Default constructor. Initializes the right and left pointers, making this
     * a circular doubly-linked list.
     *
     * @param data data for this node
     * @param key initial key for node
     */
    public FibonacciHeapNode(T data, double key)
    {
        right = this;
        left = this;
        this.data = data;
        this.key = key;
    }

    public FibonacciHeapNode(T data)
    {
        right = this;
        left = this;
        this.data = data;
    }

    //~ Methods ----------------------------------------------------------------

    /**
     * Obtain the key for this node.
     *
     * @return the key
     */
    public final double getKey()
    {
        return key;
    }

    /**
     * Obtain the data for this node.
     */
    public final T getData()
    {
        return data;
    }

    /**
     * Return the string representation of this object.
     *
     * @return string representing this object
     */
    public String toString()
    {
        StringBuilder buf = new StringBuilder();
        buf.append("Node=[parent = ");

        if (parent != null) {
            buf.append(Double.toString(parent.key));
        } else {
            buf.append("---");
        }

        buf.append(", key = ");
        buf.append(Double.toString(key));
        buf.append(", degree = ");
        buf.append(Integer.toString(degree));
        buf.append(", right = ");

        if (right != null) {
            buf.append(Double.toString(right.key));
        } else {
            buf.append("---");
        }

        buf.append(", left = ");

        if (left != null) {
            buf.append(Double.toString(left.key));
        } else {
            buf.append("---");
        }

        buf.append(", child = ");

        if (child != null) {
            buf.append(Double.toString(child.key));
        } else {
            buf.append("---");
        }

        buf.append(']');

        return buf.toString();
    }

    // toString
}
