package uk.ac.bris.cs.scotlandyard.ui.ai;

import uk.ac.bris.cs.scotlandyard.model.GameSetup;

import java.util.*;

public class Dijkstra {
    public int dist[];
    private Set<Integer> settled;
    private PriorityQueue<Node> pq;
    private int V; // Number of vertices
    private GameSetup setup;
    List<List<Node>> adj;

    public Dijkstra(int V, List<List<Node>> adj)
    {
        this.adj = adj;
        this.V = V;
        dist = new int[V];
        settled = new HashSet<Integer>();
        pq = new PriorityQueue<Node>(V, new Node());
    }

    // Function for Dijkstra's Algorithm
    public void dijkstra(List<List<Node>> adj, int src)
    {

        for (int i = 0; i < V; i++)
            dist[i] = Integer.MAX_VALUE;

        // Add source node to the priority queue
        pq.add(new Node(src, 0));

        // Distance to the source is 0
        dist[src] = 0;
        while (settled.size() != V) {

            // remove the minimum distance node
            // from the priority queue
            int u = pq.remove().node;

            // adding the node whose distance is
            // finalized
            settled.add(u);

            e_Neighbours(u);
        }
    }

    // Function to process all the neighbours
    // of the passed node
    private void e_Neighbours(int u)
    {
        int edgeDistance = -1;
        int newDistance = -1;

        // All the neighbors of v
        for (int i = 0; i < adj.get(u - 1).size(); i++) {
            Node v = adj.get(u - 1).get(i);

            // If current node hasn't already been processed
            if (!settled.contains(v.node)) {
                edgeDistance = v.cost;
                newDistance = dist[u - 1] + edgeDistance;

                // If new distance is cheaper in cost
                if (newDistance < dist[v.node - 1])
                    dist[v.node - 1] = newDistance;

                // Add the current node to the queue
                pq.add(new Node(v.node, dist[v.node - 1]));
            }
        }
    }
}

