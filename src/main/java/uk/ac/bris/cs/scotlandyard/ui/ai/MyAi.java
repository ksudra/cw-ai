package uk.ac.bris.cs.scotlandyard.ui.ai;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import com.google.common.math.Stats;
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
		Pair<Move, Integer> finalMove = new Pair(moves.get(new Random().nextInt(moves.size())), 0);
		
		for(Piece player : board.getPlayers()){
			if(player.isMrX()){
				ImmutableMap<ScotlandYard.Ticket, Integer> mrXticket = makeTickets(
						board.getPlayerTickets(player).get().getCount(TAXI),
						board.getPlayerTickets(player).get().getCount(BUS),
						board.getPlayerTickets(player).get().getCount(UNDERGROUND),
						board.getPlayerTickets(player).get().getCount(DOUBLE),
						board.getPlayerTickets(player).get().getCount(SECRET));
				mrX = new Player(player, mrXticket, finalMove.left().source());
			}
		}

		List<List<Node>> adj = new ArrayList<>();
		int w = 0;
		// Initialize list for every node
		for (int i : setup.graph.nodes()) {
			List<Node> item = new ArrayList<>();
			adj.add(item);
			for (int j : setup.graph.adjacentNodes(i)) {
				for (ScotlandYard.Transport t : setup.graph.edgeValue(i, j).orElse(null)) {
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
		}

		ImmutableSet<Player> detectives = makeDetectives(board);

		Board.GameState gameSimulation = gameStateFactory.build(setup, mrX, ImmutableList.copyOf(detectives));
		Board.GameState initial = gameSimulation;

		List<Pair<Move, Double>> moveList = moveScores(gameSimulation.getAvailableMoves(), ImmutableList.copyOf(detectives), mrX, adj);
		Move returnedMove = finalMove.left();
		Stack<Move> moveStack = new Stack<>();
		for (int i = 2; i > -1; i--) {
			moveStack.push(moveList.get(i).left());
		}
		boolean complete = false;



		int n = 0;
		while(!moveStack.isEmpty() && !complete) {

			Move currentMove = moveStack.pop();
			if(gameSimulation.getAvailableMoves().contains(currentMove)){
				gameSimulation = gameSimulation.advance(currentMove);
			}

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
					if (gameSimulation.getAvailableMoves().asList().stream().anyMatch(move -> move.commencedBy() == detective.piece())) {

						gameSimulation = gameSimulation.advance(moveScores(gameSimulation.getAvailableMoves(), List.copyOf(detectives), detective, adj).get(0).left());
					}
				}
				n++;
			}

		}
		if(n >= 5) {
			returnedMove = moveList.get(0).left();
		}

		return returnedMove;
	}

	List<Pair<Move, Double>> moveScores(ImmutableSet<Move> moves, List<Player> detectives, Player player, List<List<Node>> adj) {
		List<Pair<Move, Double>> moveList = new ArrayList<>();
		Board.GameState initial = gameStateFactory.build(setup, mrX, ImmutableList.copyOf(detectives));
		for(Move move: moves){
			Double score = 0.0;

			Object dest = move.visit(new Move.Visitor<Object>() {
				@Override
				public Object visit(Move.SingleMove move) {
					return move.destination;
				}

				@Override
				public Object visit(Move.DoubleMove move) {
					return move.destination2;
				}
			});

			Board.GameState gameState = initial;
			if(initial.getAvailableMoves().asList().contains(move)){
				gameState = initial.advance(move);
			}

			Dijkstra dij = new Dijkstra(setup.graph.nodes().size(), adj);
			if(player.piece().isMrX()) {
				ArrayList<Integer> distances = new ArrayList<>();
				for (Player detective : detectives) {
					dij.dijkstra(detective.location());
					distances.add((dij.dist[(Integer)dest - 1]));
				}
				System.out.println(distances);
				List<Double> squares = distances.stream().map((p -> (Double) Math.pow(p, 2))).collect(Collectors.toList());
				Double squaresSum = squares.stream().mapToDouble(Double::doubleValue).sum();
				Double var = (squaresSum - distances.size() * meanOf(distances)) / distances.size();
				score = meanOf(distances) / var;
			}
			score = score * (gameState.getAvailableMoves().size());
			/*if (move.commencedBy().isMrX()) {
				for (ScotlandYard.Ticket ticket : move.tickets()) {
					switch (ticket) {
						case UNDERGROUND: score = 1.2 * score;
						case SECRET: score = 1.2 * score;
						case DOUBLE: score = 1.2 * score;
					}
				}
			}*/
			if(player.piece().isDetective()) {
				dij.dijkstra(player.location());
				score -= dij.dist[mrX.location()] ^ 2;
			}
			moveList.add(new Pair(move, score));
		}

		Collections.sort(moveList, Comparator.comparing(move -> (Double) move.right()));
		Collections.reverse(moveList);
		if(player.isMrX()) {
			//System.out.println(moveList);
		}
		return moveList;
	}

	final class MyBoard implements Board {
		private GameState gameState;
		private MyBoard(GameState gameState) {
			this.gameState = gameState;
		}

		@Nonnull
		@Override
		public GameSetup getSetup() {
			return gameState.getSetup();
		}

		@Nonnull
		@Override
		public ImmutableSet<Piece> getPlayers() {
			return gameState.getPlayers();
		}

		@Nonnull
		@Override
		public Optional<Integer> getDetectiveLocation(Piece.Detective detective) {
			return gameState.getDetectiveLocation(detective);
		}

		@Nonnull
		@Override
		public Optional<TicketBoard> getPlayerTickets(Piece piece) {
			return gameState.getPlayerTickets(piece);
		}

		@Nonnull
		@Override
		public ImmutableList<LogEntry> getMrXTravelLog() {
			return gameState.getMrXTravelLog();
		}

		@Nonnull
		@Override
		public ImmutableSet<Piece> getWinner() {
			return gameState.getWinner();
		}

		@Nonnull
		@Override
		public ImmutableSet<Move> getAvailableMoves() {
			return gameState.getAvailableMoves();
		}
	}


	public ImmutableSet<Player> makeDetectives(Board board){
		ArrayList<Player> detectives = new ArrayList<>();
		for(Piece player : board.getPlayers()){
			if(player.isDetective() && board.getDetectiveLocation((Piece.Detective)player).isPresent()){
				ImmutableMap<ScotlandYard.Ticket, Integer> tickets = makeTickets(board.getPlayerTickets(player).get().getCount(TAXI),
						board.getPlayerTickets(player).get().getCount(BUS),
						board.getPlayerTickets(player).get().getCount(UNDERGROUND),
						board.getPlayerTickets(player).get().getCount(DOUBLE),
						board.getPlayerTickets(player).get().getCount(SECRET));

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
