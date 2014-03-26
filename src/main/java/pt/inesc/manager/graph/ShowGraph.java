package pt.inesc.manager.graph;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Paint;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JFrame;
import javax.swing.JScrollPane;

import org.apache.commons.collections15.Transformer;

import edu.uci.ics.jung.algorithms.layout.FRLayout;
import edu.uci.ics.jung.algorithms.layout.Layout;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.SparseMultigraph;
import edu.uci.ics.jung.graph.util.EdgeType;
import edu.uci.ics.jung.visualization.BasicVisualizationServer;
import edu.uci.ics.jung.visualization.VisualizationViewer;
import edu.uci.ics.jung.visualization.renderers.Renderer.VertexLabel.Position;

public class ShowGraph extends
        Thread {
    private static final int WIDTH = 600;
    private static final int HEIGHT = 600;
    private static final int MARGIN = 50;
    private static final long REFRESH_PERIOD = 5000;
    private final AtomicBoolean refresh = new AtomicBoolean(false);
    Graph<String, String> g;
    BasicVisualizationServer<String, String> vv;
    Layout<String, String> layout;
    JFrame frame;

    public ShowGraph() {
        g = new SparseMultigraph<String, String>();
    }

    public ShowGraph(HashMap<Long, Dependency> hashGraph) {
        this();
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
        refresh.set(true);
    }

    public void addEdgeAndVertex(String nodeA, String nodeB) {
        g.addVertex(nodeA);
        g.addVertex(nodeB);
        g.addEdge(nodeA + "->" + nodeB, nodeA, nodeB, EdgeType.DIRECTED);
    }

    /**
     * Key executes after dependencies. Each dependencie -> key
     * 
     * @param key
     * @param dependencies
     */
    public void addEdgeAndVertex(Long key, List<Long> dependencies) {
        g.addVertex(key.toString());
        for (Long depKey : dependencies) {
            g.addVertex(depKey.toString());
            addEdge(depKey.toString(), key.toString());
        }


    }

    @Override
    public void run() {
        if (vv == null) {
            display();
        }
        while (true) {
            if (refresh.getAndSet(false)) {
                refresh();
                try {
                    sleep(REFRESH_PERIOD);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
    }

    private void refresh() {
        // TODO
        layout = new FRLayout<String, String>(g);
        layout.setSize(new Dimension(WIDTH, HEIGHT));
        vv.setGraphLayout(layout);
        vv.repaint(0);
        frame.revalidate();
    }

    public void display() {
        layout = new FRLayout<String, String>(g);
        layout.setSize(new Dimension(WIDTH, HEIGHT));
        vv = new VisualizationViewer<String, String>(layout);
        vv.setSize(new Dimension(WIDTH + MARGIN, HEIGHT + MARGIN));

        // Color and labels
        Transformer<String, Paint> vertexPaint = new Transformer<String, Paint>() {
            public Paint transform(String i) {
                return Color.decode("#4586F5");
            }
        };
        Transformer<String, Shape> vertexSize = new Transformer<String, Shape>() {
            public Shape transform(String i) {
                Ellipse2D circle = new Ellipse2D.Double(-15, -15, 70, 30);
                return circle;
            }
        };
        vv.getRenderContext().setVertexFillPaintTransformer(vertexPaint);
        vv.getRenderContext().setVertexLabelTransformer(new TruncatedStringLabeller());
        vv.getRenderContext().setVertexShapeTransformer(vertexSize);
        vv.getRenderer().getVertexLabelRenderer().setPosition(Position.CNTR);


        frame = new JFrame("Dependency Graph");
        JScrollPane jsp = new JScrollPane(vv);
        frame.getContentPane().add(jsp);
        frame.pack();
        frame.setVisible(true);
    }


}
