package uk.ac.bris.cs.scotlandyard.ui.ai;

import com.google.common.graph.EndpointPair;
import uk.ac.bris.cs.scotlandyard.model.GameSetup;
import uk.ac.bris.cs.scotlandyard.model.Player;

public class dijkstraTests extends DijkstraSP{
    public dijkstraTests(GameSetup setup, int s, Player player) {
        super(setup, s, player);

        // compute shortest paths
        DijkstraSP sp = new DijkstraSP(setup, s, player);


        // print shortest path
        for (int t = 0; t < setup.graph.nodes().size(); t++) {
            if (sp.hasPathTo(t)) {
                System.out.println(s + ", " + ", " + t + ", " + sp.distTo(t));
                for (EndpointPair e : sp.pathTo(t)) {
                    System.out.println(e + "   ");
                }
                System.out.println();
            }
            else {
                System.out.println(s + ", " + t);
            }
        }
    }
}
