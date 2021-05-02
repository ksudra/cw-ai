package uk.ac.bris.cs.scotlandyard.ui.ai;

import java.util.Comparator;

public class Node implements Comparator<Node> {
    public int node;
    public int weight;

    public Node() {
    }

    public Node(int node, int weight) {
        this.node = node;
        this.weight = weight;
    }

    @Override
    public int compare(Node node1, Node node2) {
        return Integer.compare(node1.weight, node2.weight);
    }
}
