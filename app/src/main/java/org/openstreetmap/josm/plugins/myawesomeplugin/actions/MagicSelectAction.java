package org.openstreetmap.josm.plugins.myawesomeplugin.actions;

import static org.openstreetmap.josm.gui.help.HelpUtil.ht;
import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.swing.JOptionPane;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.command.AddCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.search.SearchCompiler;
import org.openstreetmap.josm.data.osm.search.SearchCompiler.Match;
import org.openstreetmap.josm.data.osm.search.SearchParseError;
import org.openstreetmap.josm.data.osm.search.SearchSetting;
import org.openstreetmap.josm.data.osm.visitor.paint.relations.Multipolygon;
import org.openstreetmap.josm.data.osm.visitor.paint.relations.Multipolygon.JoinedWay;
import org.openstreetmap.josm.data.projection.Projection;
import org.openstreetmap.josm.data.projection.Projections;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.Notification;
import org.openstreetmap.josm.gui.dialogs.relation.DownloadRelationTask;
import org.openstreetmap.josm.plugins.myawesomeplugin.utils.NodeWayUtils;
import org.openstreetmap.josm.tools.CopyList;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Shortcut;
import org.openstreetmap.josm.data.osm.Node;
import org.xml.sax.SAXException;

import com.google.common.collect.Iterables;

import java.awt.geom.Point2D;

/**
 * Extends current selection by selecting nodes on all touched ways
 */
public class MagicSelectAction extends JosmAction {

    private DataSet ds;

    public MagicSelectAction() {
        super(tr("Magic Intersecting ways"), "intway", tr("Select intersecting ways"),
                Shortcut.registerShortcut("tools:intway", tr("Selection: {0}", tr("Intersecting ways")),
                        KeyEvent.VK_I, Shortcut.DIRECT),
                true);
        putValue("help", ht("/Action/SelectIntersectingWays"));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        ds = getLayerManager().getActiveDataSet();
        Collection<Way> selectedWays = ds.getSelectedWays();
        Collection<Way> allWays = ds.getWays();

        if (!selectedWays.isEmpty()) {
            Set<Way> newIntersectedWays = new HashSet<>();
            NodeWayUtils.addWaysIntersectingWays(allWays, selectedWays, newIntersectedWays);

            // check for ways with relations
            Set<Relation> parentRelations = OsmPrimitive.getParentRelations(newIntersectedWays);

            for (Relation relation : parentRelations) {
                // check relation type
                if ("multipolygon".equals(relation.get("type"))) {
                    // copy way here for now
                    // copy(getLayerManager().getActiveDataLayer(), Iterables.get(selectedWays, 0));

                    Way sourceWay = null;

                    do {
                        sourceWay = selectedWays.iterator().next();
                        Logging.info("Working with way id: " + sourceWay.getId());

                        // Offset the line
                        Way offsetWay = offsetWay(sourceWay, ds);

                        boolean start = false;
                        boolean end = false;

                        do {
                            checkWayCutsMultipolygon(offsetWay, relation, start, end);

                        }

                        while (start != true && end != true);

                        // Way offsetWay2 = offsetWay(sourceWay, ds);

                        Node firstNode = offsetWay1.firstNode();
                        Node lastNode = offsetWay1.lastNode();

                        MainApplication.worker.submit(
                                new DownloadRelationTask(Collections.singletonList(relation),
                                        MainApplication.getLayerManager().getEditLayer()));
                        // ds.addSelected(relation);
                        break;
                    }

                    while (sourceWay != null);

                }

            }

        } else {
            new Notification(
                    tr("Please select some ways to find connected and intersecting ways!"))
                    .setIcon(JOptionPane.WARNING_MESSAGE).show();
        }
    }

    private void checkWayCutsMultipolygon(Way offsetWay, Relation relation, boolean start, boolean end) {

        // Check if created ways start and end points a within relation multipolygon
        Collection<OsmPrimitive> primitivesInsideMultipolygon = NodeWayUtils
                .selectAllInside(Collections.singletonList(relation), this.ds, false);

                Node firstNode = null;
                Node lastNode = null;

                if (!start){
                     firstNode = offsetWay.firstNode();
                }

                if (!end){
                     lastNode = offsetWay.lastNode();
                }


        for (OsmPrimitive osmPrimitive : primitivesInsideMultipolygon) {
            if (osmPrimitive instanceof Node) {


                Node node = (Node) osmPrimitive;
                if (firstNode != null && node.getCoor().equals(firstNode.getCoor())) {
                    //ds.addSelected(osmPrimitive);
                    System.out.println("Found Node: " + node.getId());

                    //findNextConnectedWay(sourceWay, 1);

                } else if (lastNode != null && node.getCoor().equals(lastNode.getCoor())) {
                    //findNextConnectedWay(sourceWay, 2);
                }

            }
        }

    }

    private static Way findNextConnectedWay(Way currentWay, int mode) {

        Node connectionNode = null;
        if (mode == 1) {
            connectionNode = currentWay.firstNode();
        } else {
            connectionNode = currentWay.lastNode();
        }
        Way nextConnectedWay = findConnectedWayWithSameTags(currentWay, connectionNode);

        return nextConnectedWay;
    }

    private static Way findConnectedWayWithSameTags(Way currentWay, Node node) {
        // Iterate over the ways connected to the specified node
        for (Way connectedWay : node.getParentWays()) {
            // Check if the connected way has the same tags as the current way
            if (connectedWay != currentWay && connectedWay.getKeys().equals(currentWay.getKeys())) {
                return connectedWay; // Found a connected way with the same tags
            }
        }
        return null; // No connected way with the same tags found
    }

    private static Way offsetWay(Collection<Way> sourceWays, DataSet ds) {

        // Offset distance in meters
        double offsetDistance2 = 0.0001;

        Collection<JoinedWay> list = combineWays(sourceWays);

        if (list.size() == 1) {
            // ways form a single line string
            List<Node> sourceNodes = Collections.unmodifiableList(new ArrayList<>(list.iterator().next().getNodes()));

            // Way sourceWay = combineWays.iterator().next();

            // List<Node> sourceNodes = sourceWay.getNodes();
            List<Node> offsetNodes = new ArrayList<>();
            List<Command> commands = new LinkedList<>();

            // Calculate the heading from start to end points
            double heading = calculateHeading(sourceNodes.get(0).getCoor(),
                    sourceNodes.get(sourceNodes.size() - 1).getCoor());

            // Offset the line by 90 degrees from the calculated heading
            double offsetHeading = (heading + 90) % 360;

            for (Node originalNode : sourceNodes) {
                // Offset the coordinates based on the new heading
                double newLat = originalNode.getCoor().lat() + Math.cos(Math.toRadians(offsetHeading)) * offsetDistance;
                double newLon = originalNode.getCoor().lon() + Math.sin(Math.toRadians(offsetHeading)) * offsetDistance;

                // Create a new node with the offset coordinates
                // offsetNodes.add(new Node(new LatLon(newLat, newLon)));
                Node offsetNode = new Node(new LatLon(newLat, newLon));
                offsetNodes.add(offsetNode);

                commands.add(new AddCommand(ds, offsetNode));

                Logging.info("x 1:" + originalNode.getEastNorth().getX());
                Logging.info("x 1:" + offsetNode.getEastNorth().getX());

            }

            // Create a new way with the offset nodes
            Way offsetLine = new Way();
            offsetLine.setNodes(offsetNodes);

            commands.add(new AddCommand(ds, offsetLine));
            UndoRedoHandler.getInstance().add(new SequenceCommand(tr("Add way"), commands));
            ds.addSelected(offsetLine);

            return offsetLine;
            // Multipolygon#joinWays
        }

        return null;
    }

    private static Collection<JoinedWay> combineWays(Collection<Way> sourceWays) {
        // final Node[] endnodes = {w.firstNode(), w.lastNode()};

        List<Way> combineWays = new ArrayList<>();
        for (Way sourceWay : sourceWays) {
            List<Node> sourceNodes = sourceWay.getNodes();

            Way offsetLine = new Way();
            offsetLine.setNodes(sourceNodes);

            combineWays.add(offsetLine);

        }

        Collection<JoinedWay> list = Multipolygon.joinWays(combineWays);
        return list;

    }

    private static double calculateHeading(LatLon start, LatLon end) {
        // Calculate the heading in degrees
        double deltaY = end.lat() - start.lat();
        double deltaX = end.lon() - start.lon();

        double heading = Math.toDegrees(Math.atan2(deltaX, deltaY));
        return (heading + 360) % 360; // Ensure the heading is in the range [0, 360)
    }

    @Override
    protected void updateEnabledState() {
        updateEnabledStateOnCurrentSelection();
    }

    @Override
    protected void updateEnabledState(Collection<? extends OsmPrimitive> selection) {
        setEnabled(selection != null && !selection.isEmpty());
    }
}

// final SearchSetting ss = new SearchSetting();
// ss.text = "highway:";
// ss.caseSensitive = true;

// SearchCompiler.Match match;
// Collection<Way> matchedWays = new HashSet<>();
// try {
// match = SearchCompiler.compile(ss);

// for (Way way : allWays) {
// if (match.match(way)) {
// matchedWays.add(way);
// }
// }

// } catch (SearchParseError e1) {

// e1.printStackTrace();
// }