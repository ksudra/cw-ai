package uk.ac.bris.cs.scotlandyard.ui.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;


import com.google.common.collect.ImmutableList;
import io.atlassian.fugue.Pair;
import uk.ac.bris.cs.scotlandyard.model.*;

public class MyAi implements Ai {
	Board board;
	GameSetup setup;
	Player mrX;
	List<Player> detectives;
	MyGameStateFactory gameStateFactory;

	MyAi(Board board, GameSetup setup, Player mrX, List<Player> detectives, MyGameStateFactory gameStateFactory) {
		this.board = board;
		this.setup = setup;
		this.mrX = mrX;
		this.detectives = detectives;
		this.gameStateFactory = gameStateFactory;
	}

	@Nonnull @Override public String name() { return "Name me!"; }

	@Nonnull @Override public Move pickMove(
			@Nonnull Board board,
			Pair<Long, TimeUnit> timeoutPair) {
		// returns a random move, replace with your own implementation
		List<Move> moves = new ArrayList<>();
		List<Integer> scores = new ArrayList<>();
		int score = 0;
		for (Move move: board.getAvailableMoves()) {
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

			for (Player detective : detectives) {
				DijkstraSP dijkstra = new DijkstraSP(setup, detective.location(), detective);
				score += dijkstra.pathScore(dijkstra.pathTo((int) dest));
			}

			for(Move newMove : gameStateFactory.build(setup, mrX, ImmutableList.copyOf(detectives)).advance(move).getAvailableMoves()) {
				if(newMove.commencedBy().isMrX()) {
					score++;
				}
			}
			moves.add(move);
			scores.add(score);
		}
		Move finalMove = moves.get(0);
		int index = 0;

		for(int i = 0; i < moves.size(); i++) {
			if(scores.get(i) > scores.get(index)) finalMove = moves.get(index);
		}

		return finalMove;
	}
}
