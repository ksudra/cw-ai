package uk.ac.bris.cs.scotlandyard.ui.ai;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;
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
		ImmutableSet<Player> detectives = makeDetectives(board);
		Pair<Move, Integer> finalMove = new Pair(moves.get(new Random().nextInt(moves.size())), 0);
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
			Object dest = move.visit(new Move.Visitor<Object>() {
				@Override
				public Object visit(Move.SingleMove move) {
					return ((Move.SingleMove) move).destination;
				}

				@Override
				public Object visit(Move.DoubleMove move) {
					return ((Move.DoubleMove) move).destination2;
				}
			});

			/*for (Player detective : detectives) {
				DijkstraSP dijkstra = new DijkstraSP(setup, detective.location(), detective);
				score += dijkstra.pathScore(dijkstra.pathTo((int) dest));
			}*/

			//TODO simulate mrX
			/*for(Move newMove : gameStateFactory.build(setup, mrX, ImmutableList.copyOf(detectives)).advance(move).getAvailableMoves()) {
				if(newMove.commencedBy().isMrX()) {
					score++;
				}
			}*/
			if(score >= finalMove.right()){
				finalMove = new Pair(move, score);
			};
		}

		return finalMove.left();
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
