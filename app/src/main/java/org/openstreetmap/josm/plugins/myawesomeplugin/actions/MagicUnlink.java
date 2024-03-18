package org.openstreetmap.josm.plugins.myawesomeplugin.actions;

import static org.openstreetmap.josm.gui.help.HelpUtil.ht;
import static org.openstreetmap.josm.tools.I18n.tr;
import static org.openstreetmap.josm.tools.I18n.trn;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.JOptionPane;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.actions.UnGlueAction;
import org.openstreetmap.josm.command.AddCommand;
import org.openstreetmap.josm.command.ChangeNodesCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.DefaultNameFormatter;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.Notification;
import org.openstreetmap.josm.plugins.utilsplugin2.actions.SplitObjectAction;
import org.openstreetmap.josm.tools.Geometry;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Shortcut;

public class MagicUnlink extends JosmAction {

    private DataSet ds;

    private transient Node selectedNode;
    private transient Set<Node> selectedNodes;

    /**
     * Constructs a new {@code RunMagic}.
     */
    public MagicUnlink() {
        super(tr("Magic Poligon Unlink"), "addintersect", tr("Add missing nodes at intersections of selected ways."),
                Shortcut.registerShortcut("tools:addintersect", tr("More tools: {0}", tr("Add nodes at intersections")),
                        KeyEvent.VK_M, Shortcut.SHIFT),
                true);
        putValue("help", ht("/Action/AddIntersections"));
    }

    @Override
    public void actionPerformed(ActionEvent arg0) {

        ds = getLayerManager().getActiveDataSet();
        Collection<Way> selectedWays = ds.getSelectedWays();

        if (!isEnabled()) {
            return;
        }

        if (!selectedWays.isEmpty() && selectedWays.size() == 1) {
            // Selected
            Iterator<Way> iterator = selectedWays.iterator();
            Way selectedWay = iterator.next();

            if (!selectedWay.isClosed()) {
                return;
            }

            List<Node> selectedWayNodes = selectedWay.getNodes();
            List<Node> processedNodes = new ArrayList<>();
            List<Node> allNewNodes = new ArrayList<>();
            // boolean found = false;

            for (Node selectedWayNode : selectedWayNodes) {

                // if (selectedWayNode.getId() == 1263693859) {
                // Logging.info("Node found");
                // }

                // Check if node was already processed
                if (!processedNodes.contains(selectedWayNode)) {

                    List<Way> parentWays = selectedWayNode.getParentWays();

                    if (parentWays.size() > 1) {

                        List<Node> commonNodes = new ArrayList<>();

                        for (Way parentWay : parentWays) {
                            //
                            if (parentWay.getUniqueId() != selectedWay.getUniqueId() && !parentWay.isClosed()) {

                                // if (way.getId() == 110735623) {
                                // Logging.info("Way found"); // 963988860
                                // found = true;

                                List<Node> parentWayNodes = parentWay.getNodes();

                                for (Node selectedWayNode1 : selectedWayNodes) {
                                    //
                                    if (parentWayNodes.contains(selectedWayNode1)
                                            && !processedNodes.contains(selectedWayNode1)) {
                                        commonNodes.add(selectedWayNode1);
                                    }
                                }

                                break;

                                // }

                            }

                        }

                        if (commonNodes.size() > 0) {

                            processedNodes.addAll(commonNodes);

                            // found = false;

                            List<Command> cmds = new ArrayList<>();

                            Map<Node, Node> replaced = new HashMap<>();
                            commonNodes.forEach(n -> replaced.put(n, cloneNode(n, cmds)));
                            List<Node> modNodes = new ArrayList<>(selectedWay.getNodes());
                            modNodes.replaceAll(n -> replaced.getOrDefault(n, n));

                            selectedNodes = new HashSet<>(commonNodes);

                            // only one changeCommand for a way, else garbage will happen
                            addCheckedChangeNodesCmd(cmds, selectedWay, modNodes);
                            UndoRedoHandler.getInstance().add(new SequenceCommand(
                                    trn("Dupe {0} node into {1} nodes", "Dupe {0} nodes into {1} nodes",
                                            commonNodes.size(), commonNodes.size(), 2 * commonNodes.size()),
                                    cmds));

                            Collection<Node> newNodes = replaced.values();

                            EastNorth coord = Geometry.getCentroid(selectedWayNodes);

                            for (Node newNode : newNodes) {
                                moveNodeInDirection(newNode, coord, 0.01);
                                allNewNodes.add(newNode);
                            }

                            // Set<Node> Nodes = Geometry.addIntersections(ways, false, cmds);
                            // getLayerManager().getEditDataSet().setSelected(replaced.values());

                        }

                    }

                }
            }

            //
            if (allNewNodes.size() > 0) {
                getLayerManager().getEditDataSet().setSelected(allNewNodes);

            }

        }

    }

    public static void moveNodeInDirection(Node node, EastNorth targetEN, double distance) {
        // Get the current East-North coordinates of the node
        EastNorth currentEN = node.getEastNorth();

        // Calculate the direction vector from the current node to the target East-North
        // coordinate
        double dx = targetEN.getX() - currentEN.getX();
        double dy = targetEN.getY() - currentEN.getY();

        // Scale the direction vector to the desired distance
        double newX = currentEN.getX() + dx * distance;
        double newY = currentEN.getY() + dy * distance;

        // Convert the new East-North coordinates back to latitude and longitude
        double lat = newY;
        double lon = newX;

        // Set the new coordinates to the existing node
        node.setEastNorth(new EastNorth(lon, lat));

    }

    private static Node cloneNode(Node originalNode, List<Command> cmds) {
        Node newNode = new Node(originalNode, true /* clear OSM ID */);
        cmds.add(new AddCommand(originalNode.getDataSet(), newNode));
        return newNode;
    }

    private boolean addCheckedChangeNodesCmd(List<Command> cmds, Way w, List<Node> nodes) {
        boolean relationCheck = !calcAffectedRelations(Collections.singleton(w)).isEmpty();
        cmds.add(new ChangeNodesCommand(w, nodes));
        if (relationCheck) {
            notifyWayPartOfRelation(Collections.singleton(w));
        }
        return relationCheck;
    }

    protected void notifyWayPartOfRelation(final Collection<Way> ways) {
        Set<Relation> affectedRelations = calcAffectedRelations(ways);
        if (affectedRelations.isEmpty()) {
            return;
        }
        final int size = affectedRelations.size();
        final String msg1 = trn("Unglueing possibly affected {0} relation: {1}",
                "Unglueing possibly affected {0} relations: {1}",
                size, size, DefaultNameFormatter.getInstance().formatAsHtmlUnorderedList(affectedRelations, 20));
        final String msg2 = trn("Ensure that the relation has not been broken!",
                "Ensure that the relations have not been broken!",
                size);
        new Notification(msg1 + msg2).setIcon(JOptionPane.WARNING_MESSAGE).show();
    }

    protected Set<Relation> calcAffectedRelations(final Collection<Way> ways) {
        final Set<Node> affectedNodes = (selectedNodes != null) ? selectedNodes : Collections.singleton(selectedNode);
        return OsmPrimitive.getParentRelations(ways)
                .stream().filter(r -> isRelationAffected(r, affectedNodes, ways))
                .collect(Collectors.toSet());
    }

    private static boolean isRelationAffected(Relation r, Set<Node> affectedNodes, Collection<Way> ways) {
        if (!r.isUsable())
            return false;
        // see #18670: suppress notification when well known restriction types are not
        // affected
        if (!r.hasTag("type", "restriction", "connectivity", "destination_sign") || r.hasIncompleteMembers())
            return true;
        int count = 0;
        for (RelationMember rm : r.getMembers()) {
            if (rm.isNode() && affectedNodes.contains(rm.getNode()))
                count++;
            if (rm.isWay() && ways.contains(rm.getWay())) {
                count++;
                if ("via".equals(rm.getRole())) {
                    count++;
                }
            }
        }
        return count >= 2;
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
