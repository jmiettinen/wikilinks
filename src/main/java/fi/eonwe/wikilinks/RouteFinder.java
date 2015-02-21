package fi.eonwe.wikilinks;

import com.carrotsearch.hppc.LongArrayList;
import com.carrotsearch.hppc.LongIntMap;
import com.carrotsearch.hppc.LongObjectMap;
import com.carrotsearch.hppc.LongObjectOpenHashMap;
import com.carrotsearch.hppc.procedures.LongProcedure;
import org.jgrapht.util.FibonacciHeap;
import org.jgrapht.util.FibonacciHeapNode;

import java.util.List;

/**
 */
public class RouteFinder {

    private final FibonacciHeap<PackedWikiPage> heap;
    private final LongObjectMap<FibonacciHeapNode<PackedWikiPage>> nodes = new LongObjectOpenHashMap<>();
    private final LongIntMap idIndexMap;
    private final List<PackedWikiPage> graph;
    private final long startId;
    private final long endId;

    private RouteFinder(long startId, long endId, List<PackedWikiPage> graph, LongIntMap idIndexMap) {
        this.graph = graph;
        this.idIndexMap = idIndexMap;
        this.startId = startId;
        this.endId = endId;
        this.heap = setup(startId, graph);
    }

    public static LongArrayList find(long startId, long endId, List<PackedWikiPage> graph, LongIntMap idIndexMap) {
        RouteFinder finder = new RouteFinder(startId, endId, graph, idIndexMap);
        LongArrayList route = finder.find();
        return route;
    }

    private FibonacciHeap<PackedWikiPage> setup(long startId, List<PackedWikiPage> graph) {
        FibonacciHeap<PackedWikiPage> heap = new FibonacciHeap<>();
        FibonacciHeapNode<PackedWikiPage> node = new FibonacciHeapNode<>(forId(startId), 0.0);
        heap.insert(node, node.getKey());
        return heap;
    }

    private PackedWikiPage forId(long id) {
        int index = idIndexMap.getOrDefault(id, -1);
        if (index < 0 || index >= graph.size()) return null;
        return graph.get(index);
    }

    private LongArrayList find() {
        LongArrayList route = new LongArrayList();
        while (!heap.isEmpty()) {
            FibonacciHeapNode<PackedWikiPage> min = heap.removeMin();
            final double distance = min.getKey();
            route.add(min.getData().getId());
            if (min.getData().getId() == endId) break;
            min.getData().forEachLink(new LongProcedure() {
                @Override
                public void apply(long linkTarget) {
                    FibonacciHeapNode<PackedWikiPage> node = nodes.getOrDefault(linkTarget, null);
                    final double newDistance = distance + 1.0;
                    if (node == null) {
                        node = new FibonacciHeapNode<>(forId(linkTarget), newDistance);
                        nodes.put(linkTarget, node);
                        heap.insert(node, node.getKey());
                    } else if (node.getKey() > newDistance) {
                        heap.decreaseKey(node, newDistance);
                    }
                }
            });
        }
        return route;
    }


}
