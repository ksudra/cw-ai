package uk.ac.bris.cs.scotlandyard.ui.ai;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.atlassian.fugue.Pair;
import uk.ac.bris.cs.scotlandyard.model.*;

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
        Pair<Move, Integer> finalMove = new Pair<>(moves.get(new Random().nextInt(moves.size())), 0);

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
        for (int i : setup.graph.nodes()) {
            List<Node> item = new ArrayList<>();
            adj.add(item);
            for (int j : setup.graph.adjacentNodes(i)) {
                for (ScotlandYard.Transport t : Objects.requireNonNull(setup.graph.edgeValue(i, j).orElse(null))) {
                    switch (t) {
                        case TAXI: w++;
                        case BUS: w += 2;
                        case UNDERGROUND: w += 4;
                        case FERRY: w = Integer.MAX_VALUE;
                    }
                }
                w = w / Objects.requireNonNull(setup.graph.edgeValue(i, j).orElse(null)).size();
                item.add(new Node(j, w));
            }
        }


        ImmutableSet<Player> detectives = makeDetectives(board);

        Board.GameState gameSimulation = gameStateFactory.build(setup, mrX, ImmutableList.copyOf(detectives));
        Board.GameState initial = gameSimulation;

        List<Pair<Move, Integer>> moveList = moveScores(gameSimulation.getAvailableMoves(), ImmutableList.copyOf(detectives), mrX, adj);
        Move returnedMove = moveList.get(0).left();
        Stack<Move> moveStack = new Stack<>();
        for (int i = 2; i > -1; i--) {
            moveStack.push(moveList.get(i).left());
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

                for (Player detective : detectives) {
                    gameSimulation.advance(moveScores(gameSimulation.getAvailableMoves(), List.copyOf(detectives), detective, adj).get(0).left());
                }

                gameSimulation.advance(moveScores(gameSimulation.getAvailableMoves(), ImmutableList.copyOf(detectives), mrX, adj).get(0).left());
                n++;
            }

        }
        if(n >= 5) {
            returnedMove = moveList.get(0).left();
        }

        return returnedMove;
    }

    List<Pair<Move, Integer>> moveScores(ImmutableSet<Move> moves, List<Player> detectives, Player player, List<List<Node>> adj) {
        List<Pair<Move, Integer>> moveList = new ArrayList<>();
        Board.GameState initial = gameStateFactory.build(setup, mrX, ImmutableList.copyOf(detectives));
        for(Move move: moves){
            int score = 0;
            if (move.commencedBy().isMrX()) {
                for (ScotlandYard.Ticket ticket : move.tickets()) {
                    switch (ticket) {
                        case TAXI: score += 1;
                        case BUS: score += 4;
                        case UNDERGROUND: score += 8;
                        case SECRET: score += 7;
                        case DOUBLE: score += 10;
                    }
                }
            }
            
            Board.GameState gameState = initial.advance(move);
            Dijkstra dij = new Dijkstra(setup.graph.nodes().size(), adj);

            if(player.piece().isMrX()) {
                for (Player detective : detectives) {
                    dij.dijkstra(detective.location());
                    score += dij.dist[mrX.location() - 1];
                }
                for(Move newMove : gameState.getAvailableMoves()) {
                    if(newMove.commencedBy().isMrX()) {
                        score++;
                    }
                }
            } else if(player.piece().isDetective()) {
                dij.dijkstra(player.location());
                score = dij.dist[mrX.location() - 1];
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
