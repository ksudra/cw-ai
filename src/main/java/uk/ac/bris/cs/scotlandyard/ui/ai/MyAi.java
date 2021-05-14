package uk.ac.bris.cs.scotlandyard.ui.ai;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.atlassian.fugue.Pair;
import uk.ac.bris.cs.scotlandyard.model.*;

import static com.google.common.math.Stats.meanOf;
import static uk.ac.bris.cs.scotlandyard.model.ScotlandYard.Ticket.*;

public class MyAi implements Ai {
    GameSetup setup;
    Player mrX;

    @Nonnull @Override public String name() { return "mrX AI"; }

    @Nonnull @Override public Move pickMove(
            @Nonnull Board board,
            Pair<Long, TimeUnit> timeoutPair) {
        this.setup = board.getSetup();
        var moves = board.getAvailableMoves().asList();
        Pair<Move, Double> finalMove = new Pair<>(moves.get(new Random().nextInt(moves.size())), 0.0);
        int red = 0;
        int green = 0;
        int blue = 0;
        int white = 0;
        int yellow = 0;
        int MrX;

        for(Piece player : board.getPlayers()){
            if(player.isMrX()){
                ImmutableMap<ScotlandYard.Ticket, Integer> mrXTicket = makeTickets(
                        Objects.requireNonNull(board.getPlayerTickets(player).orElse(null)).getCount(TAXI),
                        Objects.requireNonNull(board.getPlayerTickets(player).orElse(null)).getCount(BUS),
                        Objects.requireNonNull(board.getPlayerTickets(player).orElse(null)).getCount(UNDERGROUND),
                        Objects.requireNonNull(board.getPlayerTickets(player).orElse(null)).getCount(DOUBLE),
                        Objects.requireNonNull(board.getPlayerTickets(player).orElse(null)).getCount(SECRET));
                mrX = new Player(player, mrXTicket, finalMove.left().source());
            }
        }

        List<List<Node>> adj = new ArrayList<>();
        int w = 0;
        int maxNode = setup.graph.nodes().stream().max(Integer::compareTo).orElse(0);
        int minNode = setup.graph.nodes().stream().min(Integer::compareTo).orElse(0);
        for (int i = 0; i < maxNode + 1; i++) {
            List<Node> item = new ArrayList<>();
            adj.add(item);
            if(setup.graph.nodes().contains(i)) {
                for (int j : setup.graph.adjacentNodes(i)) {
                    for (ScotlandYard.Transport t : Objects.requireNonNull(setup.graph.edgeValue(i, j).orElse(null))) {
                        switch (t) {
                            case TAXI: w++;
                            case BUS: w += 2;
                            case UNDERGROUND: w += 4;
                            case FERRY: w += 8;
                        }
                    }
                    w = w / Objects.requireNonNull(setup.graph.edgeValue(i, j).orElse(null)).size();
                    item.add(new Node(j, w));
                }
            } else {
                item.add(new Node(i, Integer.MAX_VALUE));
            }
        }

        ImmutableSet<Player> detectives = makeDetectives(board);

        List<Pair<Move, Double>> moveList = moveScores(List.copyOf(board.getAvailableMoves()),
                ImmutableList.copyOf(detectives), mrX, adj, minNode, board);
        Move returnedMove = moveList.get(0).left();
        Queue<Move> moveQueue = new LinkedList<>();
        if (moveList.size() >= 3) {
            for (int i = 0; i < 3; i++) {
                moveQueue.add(moveList.get(i).left());
            }
        } else {
            Collections.reverse(moveList);
            for (Pair<Move, Double> move : moveList) {
                moveQueue.add(move.left());
            }
        }

        for (Player detective:detectives) {
            switch (detective.piece().webColour()) {
                case "#f00":
                    red = detective.location();
                case "#0f0":
                    green = detective.location();
                case "#00f":
                    blue = detective.location();
                case "#fff":
                    white = detective.location();
                case "#ff0":
                    yellow = detective.location();
            }
        }

        MrX = returnedMove.visit(new Move.Visitor<>(){
            @Override
            public Integer visit(Move.SingleMove move) {
                return move.destination;
            }

            @Override
            public Integer visit(Move.DoubleMove move) {
                return move.destination2;
            }
        });

        boolean reset = false;
        int n = 0;
        while(!moveQueue.isEmpty() && n < 4) {

            returnedMove = moveQueue.remove();

                if (MrX == red) {
                    reset = true;
                } else if (MrX == green) {
                    reset = true;
                } else if (MrX == blue) {
                    reset = true;
                } else if (MrX == white) {
                    reset = true;
                } else if (MrX == yellow) {
                    reset = true;
                }

                if (reset) {
                    for (Player detective : detectives) {
                        switch (detective.piece().webColour()) {
                            case "#f00":
                                red = detective.location();
                            case "#0f0":
                                green = detective.location();
                            case "#00f":
                                blue = detective.location();
                            case "#fff":
                                white = detective.location();
                            case "#ff0":
                                yellow = detective.location();
                        }
                    }
                    MrX = mrX.location();
                    reset = false;
                }

                for (Player detective:detectives) {
                    List<Pair<Node, Integer>> newMoves = new ArrayList<>();
                    switch (detective.piece().webColour()) {
                        case "#f00":
                            for(Node node:adj.get(red - minNode)) {
                                int score = node.weight;
                                newMoves.add(new Pair<>(node, score));
                            }
                            newMoves.sort(Comparator.comparing(move -> move.right()));
                            red = newMoves.get(0).left().node;
                        case "#0f0":
                            for(Node node:adj.get(green - minNode)) {
                                int score = node.weight;
                                newMoves.add(new Pair<>(node, score));
                            }
                            newMoves.sort(Comparator.comparing(move -> move.right()));
                            green = newMoves.get(0).left().node;
                        case "#00f":
                            for(Node node:adj.get(blue - minNode)) {
                                int score = node.weight;
                                newMoves.add(new Pair<>(node, score));
                            }
                            newMoves.sort(Comparator.comparing(move -> move.right()));
                            blue = newMoves.get(0).left().node;
                        case "#fff":
                            for(Node node:adj.get(white - minNode)) {
                                int score = node.weight;
                                newMoves.add(new Pair<>(node, score));
                            }
                            newMoves.sort(Comparator.comparing(move -> move.right()));
                            white = newMoves.get(0).left().node;
                        case "#ff0":
                            for(Node node:adj.get(yellow - minNode)) {
                                int score = node.weight;
                                newMoves.add(new Pair<>(node, score));
                            }
                            newMoves.sort(Comparator.comparing(move -> move.right()));
                            yellow = newMoves.get(0).left().node;
                    }
                }

                List<Pair<Node, Integer>> newMoves = new ArrayList<>();
                for (Node node:adj.get(MrX - minNode)) {
                    int score = node.weight;
                    newMoves.add(new Pair<>(node, score));
                }
                newMoves.sort(Comparator.comparing(move -> move.right()));
                MrX = newMoves.get(0).left().node;
                n++;
            }
        return returnedMove;
    }



    List<Pair<Move, Double>> moveScores(List<Move> moves, List<Player> detectives, Player player,
                                        List<List<Node>> adj, int minNode, Board board) {
        List<Pair<Move, Double>> moveList = new ArrayList<>();
        Random random = new Random();
        List<Move> movesCopy = new ArrayList<>(moves);
        List<Move> newMoves = new ArrayList<>();

        if(moves.size() > 50) {
            for (int i = 0; i < 50; i++) {
                int index = random.nextInt(movesCopy.size());
                newMoves.add(movesCopy.get(index));
                movesCopy.remove(index);
            }
        }

        for(Move move: newMoves) {
            double score = 0.0;

            ImmutableList<ScotlandYard.Ticket> tickets = move.visit(new Move.Visitor<>(){
                @Override
                public ImmutableList<ScotlandYard.Ticket> visit(Move.SingleMove move){ return ImmutableList.of(move.ticket);}

                @Override
                public ImmutableList<ScotlandYard.Ticket> visit(Move.DoubleMove move){ return ImmutableList.of(move.ticket1,
                        move.ticket2, DOUBLE);}
            });

            int dest = move.visit(new Move.Visitor<>(){
                @Override
                public Integer visit(Move.SingleMove move) {
                    return move.destination;
                }

                @Override
                public Integer visit(Move.DoubleMove move) {
                    return move.destination2;
                }
            });

            if(player.piece().isMrX()) {
                ArrayList<Double> distances = new ArrayList<>();
                for (Player detective : detectives) {
                    Dijkstra dij = new Dijkstra(setup.graph.nodes().size(), adj, minNode);
                    dij.dijkstra(detective.location());
                    distances.add(Math.log(dij.dist[dest]));
                }
                score = meanOf(distances);

                if(tickets.contains(DOUBLE) || tickets.contains(SECRET)){
                    score = 0.5 * score;
                }

                if(setup.rounds.get(board.getMrXTravelLog().size())){
                    if(tickets.contains(DOUBLE)){
                        if(tickets.get(1) == SECRET)
                            score = 4 * score;
                    }
                }
                if(board.getMrXTravelLog().size() > 0 && setup.rounds.get(board.getMrXTravelLog().size() - 1)){
                    if(tickets.contains(SECRET)){
                        score = 4 * score;
                    }
                }

                score = score * setup.graph.adjacentNodes(dest).size();
            }

            moveList.add(new Pair<>(move, score));
        }

        moveList.sort(Comparator.comparing(move -> move.right()));
        Collections.reverse(moveList);
        return moveList;
    }

    public ImmutableSet<Player> makeDetectives(Board board){
        ArrayList<Player> detectives = new ArrayList<>();
        for(Piece player : board.getPlayers()){
            if(player.isDetective() && board.getDetectiveLocation((Piece.Detective)player).isPresent()){
                ImmutableMap<ScotlandYard.Ticket, Integer> tickets = makeTickets(Objects.requireNonNull(board.getPlayerTickets(player).orElse(null)).getCount(TAXI),
                        Objects.requireNonNull(board.getPlayerTickets(player).orElse(null)).getCount(BUS),
                        Objects.requireNonNull(board.getPlayerTickets(player).orElse(null)).getCount(UNDERGROUND),
                        Objects.requireNonNull(board.getPlayerTickets(player).orElse(null)).getCount(DOUBLE),
                        Objects.requireNonNull(board.getPlayerTickets(player).orElse(null)).getCount(SECRET));

                detectives.add(new Player(player, tickets, board.getDetectiveLocation((Piece.Detective) player).get()));
            }
        }
        return ImmutableSet.copyOf(detectives);
    }

    static ImmutableMap<ScotlandYard.Ticket, Integer> makeTickets(
            int taxi, int bus, int underground, int x2, int secret) {
        return ImmutableMap.of(
                TAXI, taxi,
                BUS, bus,
                UNDERGROUND, underground,
                ScotlandYard.Ticket.DOUBLE, x2,
                ScotlandYard.Ticket.SECRET, secret);
    }
}

    class Dijkstra {
        public int[] dist;
        private final Set<Integer> settled;
        private final PriorityQueue<Node> priorityQueue;
        private final int nodes;
        List<List<Node>> adjacencyList;

        public Dijkstra(int nodes, List<List<Node>> adjacencyList, int minNode)
        {
            this.adjacencyList = adjacencyList;
            this.nodes = nodes;
            dist = new int[nodes + minNode];
            settled = new HashSet<>();
            priorityQueue = new PriorityQueue<>(nodes, new Node());
        }

        public void dijkstra(int source) {

            for (int i = 0; i < nodes + 1; i++)
                dist[i] = Integer.MAX_VALUE;

            priorityQueue.add(new Node(source, 0));

            dist[source] = 0;
            while (settled.size() != nodes) {
                int u = priorityQueue.remove().node;
                settled.add(u);
                relax(u);
            }
        }

        void relax(int u)
        {
            int edgeDistance;
            int newDistance;

            for (int i = 0; i < adjacencyList.get(u).size(); i++) {
                Node v = adjacencyList.get(u).get(i);

                if (!settled.contains(v.node)) {
                    edgeDistance = v.weight;
                    newDistance = dist[u] + edgeDistance;

                    if (newDistance < dist[v.node])
                        dist[v.node] = newDistance;

                    priorityQueue.add(new Node(v.node, dist[v.node]));
                }
            }
        }
    }



