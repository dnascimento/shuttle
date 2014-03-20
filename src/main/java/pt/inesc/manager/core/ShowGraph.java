package pt.inesc.manager.core;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Paint;
import java.util.HashMap;
import java.util.Map.Entry;

import javax.swing.JFrame;

import org.apache.commons.collections15.Transformer;

import edu.uci.ics.jung.algorithms.layout.CircleLayout;
import edu.uci.ics.jung.algorithms.layout.Layout;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.SparseMultigraph;
import edu.uci.ics.jung.graph.util.EdgeType;
import edu.uci.ics.jung.visualization.BasicVisualizationServer;
import edu.uci.ics.jung.visualization.decorators.ToStringLabeller;
import edu.uci.ics.jung.visualization.renderers.Renderer.VertexLabel.Position;

public class ShowGraph {
    private static final int WIDTH = 600;
    private static final int HEIGHT = 600;
    private static final int MARGIN = 50;
    Graph<String, String> g;

    public ShowGraph(Graph<String, String> graph) {
        g = graph;
    }


    public ShowGraph(HashMap<Long, Dependency> hashGraph) {
        g = new SparseMultigraph<String, String>();
        for (Entry<Long, Dependency> entry : hashGraph.entrySet()) {

            g.addVertex(entry.getValue().toString());
            for (long depKey : entry.getValue().getAfter()) {
                Dependency depEntry = hashGraph.get(depKey);
                g.addVertex(depEntry.toString());
                addEdge(entry.getValue().toString(), depEntry.toString());
            }
        }
    }

    /**
     * Add edge from A to B
     * 
     * @param nodeA
     * @param nodeB
     */
    private void addEdge(String nodeA, String nodeB) {
        g.addEdge(nodeA + "->" + nodeB, nodeA, nodeB, EdgeType.DIRECTED);
    }

    public void display() {
        Layout<String, String> layout = new CircleLayout<String, String>(g);
        layout.setSize(new Dimension(WIDTH, HEIGHT));
        BasicVisualizationServer<String, String> vv = new BasicVisualizationServer<String, String>(
                layout);
        vv.setPreferredSize(new Dimension(WIDTH + MARGIN, HEIGHT + MARGIN));

        // Color and labels
        Transformer<String, Paint> vertexPaint = new Transformer<String, Paint>() {
            public Paint transform(String i) {
                return Color.GREEN;
            }
        };
        // // Set up a new stroke Transformer for the edges
        // float dash[] = { 10.0f };
        // final Stroke edgeStroke = new BasicStroke(1.0f, BasicStroke.CAP_BUTT,
        // BasicStroke.JOIN_MITER, 10.0f, dash, 0.0f);
        //
        // Transformer<String, Stroke> edgeStrokeTransformer = new Transformer<String,
        // Stroke>() {
        // public Stroke transform(String s) {
        // return edgeStroke;
        // }
        // };
        vv.getRenderContext().setVertexFillPaintTransformer(vertexPaint);
        // vv.getRenderContext().setEdgeStrokeTransformer(edgeStrokeTransformer);
        vv.getRenderContext().setVertexLabelTransformer(new ToStringLabeller<String>());
        vv.getRenderContext().setEdgeLabelTransformer(new ToStringLabeller<String>());
        vv.getRenderer().getVertexLabelRenderer().setPosition(Position.CNTR);


        JFrame frame = new JFrame("Dependency Graph");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().add(vv);
        frame.pack();
        frame.setVisible(true);

    }
}
