package uk.ac.bris.cs.scotlandyard.ui.ai;

import com.google.common.collect.ImmutableSet;
import com.google.common.graph.EndpointPair;

import uk.ac.bris.cs.scotlandyard.model.*;
import uk.ac.bris.cs.scotlandyard.model.Piece.Detective;

import java.util.*;

import static uk.ac.bris.cs.scotlandyard.model.ScotlandYard.Ticket.SECRET;

public class DijkstraSP {
    Board.GameState state;
    GameSetup setup;
    Player player;
    private double[] distTo;          // distTo[v] = distance  of shortest s->v path
    private List<EndpointPair<Integer>> edgeTo;            // edgeTo[v] = last edge on shortest s->v path
    private IndexMinPQ<Double> pq;    // priority queue of vertices

    public DijkstraSP(GameSetup setup, int s, Player player) {
        this.player = player;
        this.setup = setup;

        for (EndpointPair<Integer> edge: setup.graph.edges()) {
            if (weight(edge) < 0)
                throw new IllegalArgumentException("edge " + edge + " has negative weight");
        }

        distTo = new double[setup.graph.nodes().size()];
        edgeTo = new ArrayList<>();

        for (int v = 0; v < setup.graph.nodes().size(); v++)
            distTo[v] = Double.POSITIVE_INFINITY;
        distTo[s] = 0.0;

        // relax vertices in order of distance from s
        pq = new IndexMinPQ<Double>(setup.graph.nodes().size());
        pq.insert(s, distTo[s]);
        while (!pq.isEmpty()) {
            int v = pq.delMin();
            for (EndpointPair<Integer> edge : adjacent(List.copyOf(state.getPlayers())))
                relax(edge, v);
        }
    }

    // relax edge e and update pq if changed
    private void relax(EndpointPair<Integer> edge, int v) {
        int w = other(edge, v);
        if (distTo[w] > distTo[v] + weight(edge)) {
            distTo[w] = distTo[v] + weight(edge);
            edgeTo.set(w, edge);
            if (pq.contains(w)) pq.decreaseKey(w, distTo[w]);
            else pq.insert(w, distTo[w]);
        }
    }

    public double distTo(int v) {
        return distTo[v];
    }

    public boolean hasPathTo(int v) {
        return distTo[v] < Double.POSITIVE_INFINITY;
    }

    public Iterable<EndpointPair<Integer>> pathTo(int v) {
        if (!hasPathTo(v)) return null;
        Stack<EndpointPair<Integer>> path = new Stack<EndpointPair<Integer>>();
        int x = v;
        for (EndpointPair<Integer> edge = edgeTo.get(v); edge != null; edge = edgeTo.get(x)) {
            path.push(edge);
            x = other(edge, x);
        }
        return path;
    }

    public int weight (EndpointPair<Integer> edge) {
        ScotlandYard.Transport transport = setup.graph.edgeValueOrDefault(edge, ImmutableSet.of()).asList().get(0);
        return 12 - player.tickets().get(transport.requiredTicket());
    }

    public ImmutableSet<EndpointPair<Integer>> adjacent(List<Piece> players) {
        Set<EndpointPair<Integer>> adj = new HashSet<>();
        Set<Integer> toRemove = new HashSet<>();
        for(int destination : setup.graph.adjacentNodes(player.location())) {
            for (int i = 0; i < players.size(); i++) {
                String colour = players.get(i).webColour();
                if (players.get(i).isDetective()) {
                    Detective detective;
                    switch (colour) {
                        case "#f00":
                            detective = Detective.RED;
                        case "#0f0":
                            detective = Detective.GREEN;
                        case "#00f":
                            detective = Detective.BLUE;
                        case "#fff":
                            detective = Detective.WHITE;
                        case "#ff0":
                            detective = Detective.YELLOW;
                            break;
                        default:
                            throw new IllegalStateException("Unexpected value: " + colour);
                    }
                    if (state.getDetectiveLocation(detective).orElse(null) == destination) toRemove.add(destination);
                }
            }

            if (!toRemove.contains(destination)) adj.add(EndpointPair.unordered(player.location(), destination));
        }
        return ImmutableSet.copyOf(adj);
    }

    public int other(EndpointPair<Integer> edge, int v) {
        if (edge.nodeU() == v) return edge.nodeV();
        else return edge.nodeU();
    }

    public int pathScore(Iterable<EndpointPair<Integer>> edges) {
        int s = 0;
        for (EndpointPair<Integer> edge : edges) {
            s += weight(edge);
        }
        return s ^ 2;
    }
}
