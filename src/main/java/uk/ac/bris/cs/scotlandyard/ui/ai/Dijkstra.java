package uk.ac.bris.cs.scotlandyard.ui.ai;

import java.util.*;

public class Dijkstra {
    public int[] dist;
    private final Set<Integer> settled;
    private final PriorityQueue<Node> priorityQueue;
    private final int nodes;
    List<List<Node>> adjacencyList;

    public Dijkstra(int nodes, List<List<Node>> adjacencyList)
    {
        this.adjacencyList = adjacencyList;
        this.nodes = nodes;
        dist = new int[nodes];
        settled = new HashSet<>();
        priorityQueue = new PriorityQueue<>(nodes, new Node());
    }

    public void dijkstra(int source) {

        for (int i = 0; i < nodes; i++)
            dist[i] = Integer.MAX_VALUE;

        priorityQueue.add(new Node(source, 0));

        dist[source] = 0;
        while (settled.size() != nodes) {
            int u = priorityQueue.remove().node;
            settled.add(u);
            relax(u);
        }
    }

    private void relax(int u)
    {
        int edgeDistance;
        int newDistance;

        for (int i = 0; i < adjacencyList.get(u - 1).size(); i++) {
            Node v = adjacencyList.get(u - 1).get(i);
            
            if (!settled.contains(v.node)) {
                edgeDistance = v.weight;
                newDistance = dist[u - 1] + edgeDistance;

                if (newDistance < dist[v.node - 1])
                    dist[v.node - 1] = newDistance;

                priorityQueue.add(new Node(v.node, dist[v.node - 1]));
            }
        }
    }
}

