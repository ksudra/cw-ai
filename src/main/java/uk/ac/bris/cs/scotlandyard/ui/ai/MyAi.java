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
    MyGameStateFactory gameStateFactory = new MyGameStateFactory();

    @Nonnull @Override public String name() { return "mrX AI"; }

    @Nonnull @Override public Move pickMove(
            @Nonnull Board board,
            Pair<Long, TimeUnit> timeoutPair) {
        this.setup = board.getSetup();
        var moves = board.getAvailableMoves().asList();
        Pair<Move, Double> finalMove = new Pair<>(moves.get(new Random().nextInt(moves.size())), 0.0);

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

        Board.GameState gameSimulation = gameStateFactory.build(setup, mrX, ImmutableList.copyOf(detectives));
        Board.GameState initial = gameSimulation;

        List<Pair<Move, Double>> moveList = moveScores(gameSimulation.getAvailableMoves(), ImmutableList.copyOf(detectives), mrX, adj, false, minNode, board);
        Move returnedMove = moveList.get(0).left();
        Stack<Move> moveStack = new Stack<>();
        if (moveList.size() >= 3) {
            for (int i = 2; i > -1; i--) {
                moveStack.push(moveList.get(i).left());
            }
        } else {
            Collections.reverse(moveList);
            for (Pair<Move, Double> move : moveList) {
                moveStack.push(move.left());
            }
        }
        boolean complete = false;
        int n = 0;
        while(!moveStack.isEmpty() && !complete) {

            Move currentMove = moveStack.pop();
            gameSimulation.advance(currentMove);

            while(gameSimulation.getWinner() != mrX.piece() && n < 5) {
                if (!gameSimulation.getWinner().isEmpty() && gameSimulation.getWinner() != mrX.piece()) {
                    gameSimulation = initial;
                    break;
                } else if (gameSimulation.getWinner() == mrX.piece()) {
                    returnedMove = currentMove;
                    complete = true;
                    break;
                }

                for (Player detective:detectives) {
                    gameSimulation.advance(moveScores(gameSimulation.getAvailableMoves(), List.copyOf(detectives),
                            detective, adj, true, minNode, board).get(0).left());
                }

                gameSimulation.advance(moveScores(gameSimulation.getAvailableMoves(), ImmutableList.copyOf(detectives),
                        mrX, adj, true, minNode, board).get(0).left());
                n++;
            }

        }
        if(n >= 5) {
            returnedMove = moveList.get(0).left();
        }

        return returnedMove;
    }

    List<Pair<Move, Double>> moveScores(ImmutableSet<Move> moves, List<Player> detectives, Player player,
                                        List<List<Node>> adj, boolean greedy, int minNode, Board board) {
        List<Pair<Move, Double>> moveList = new ArrayList<>();
        Board.GameState initial = gameStateFactory.build(setup, mrX, ImmutableList.copyOf(detectives));
        for(Move move: moves){
            double score = 0.0;
//            if (move.commencedBy().isMrX()) {
//                for (ScotlandYard.Ticket ticket : move.tickets()) {
//                    switch (ticket) {
//                        case TAXI: score += 1;
//                        case BUS: score += 4;
//                        case UNDERGROUND: score += 8;
//                        case SECRET: score += 7;
//                        case DOUBLE: score += 10;
//                    }
//                }
//            }

            ImmutableList<ScotlandYard.Ticket> tickets = move.visit(new Move.Visitor<>(){
                @Override
                public ImmutableList<ScotlandYard.Ticket> visit(Move.SingleMove move){ return ImmutableList.of(move.ticket);}

                @Override
                public ImmutableList<ScotlandYard.Ticket> visit(Move.DoubleMove move){ return ImmutableList.of(move.ticket1, move.ticket2, DOUBLE);}
            });

            Board.GameState gameState = initial.advance(move);

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


            if(player.piece().isMrX() && !greedy) {
                ArrayList<Double> distances = new ArrayList<>();
                for (Player detective : detectives) {
                    Dijkstra dij = new Dijkstra(setup.graph.nodes().size(), adj, minNode);
                    dij.dijkstra(detective.location());
                    distances.add(Math.log(dij.dist[dest]) / Math.log(2));
                }
               score = meanOf(distances);

                if(tickets.contains(DOUBLE) || tickets.contains(SECRET)){
                    score = 0.5 * score;
                }

                if (tickets.size() == 1) {
                  score += board.getPlayerTickets(mrX.piece()).get().getCount(tickets.get(0)) * 0.00001;
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
            } else if(greedy) {
                for (Node node:adj.get(player.location()))
                    if (node.node == dest) {
                        score = node.weight;
                    }
            }

            moveList.add(new Pair<>(move, score));
        }

        moveList.sort(Comparator.comparing(move -> move.right()));
        if (player.piece().isMrX() && !greedy) {
            Collections.reverse(moveList);
        }
        System.out.println(moveList);
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

    static class Dijkstra {
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
}
