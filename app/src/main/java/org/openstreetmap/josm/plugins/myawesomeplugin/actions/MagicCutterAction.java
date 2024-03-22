package org.openstreetmap.josm.plugins.myawesomeplugin.actions;

import static org.openstreetmap.josm.gui.help.HelpUtil.ht;
import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.Area;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import org.openstreetmap.josm.actions.ExpertToggleAction;
import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.actions.UnGlueAction;
import org.openstreetmap.josm.actions.downloadtasks.DownloadOsmTask;
import org.openstreetmap.josm.actions.downloadtasks.DownloadTask;
import org.openstreetmap.josm.actions.downloadtasks.DownloadTaskList;
import org.openstreetmap.josm.actions.downloadtasks.PostDownloadHandler;
import org.openstreetmap.josm.actions.relation.DownloadRelationAction;
import org.openstreetmap.josm.command.AddCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.DeleteCommand;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.command.SplitWayCommand;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.DefaultNameFormatter;
import org.openstreetmap.josm.data.osm.MultipolygonBuilder;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.PrimitiveId;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.MultipolygonBuilder.JoinedPolygon;
import org.openstreetmap.josm.data.osm.search.SearchCompiler;
import org.openstreetmap.josm.data.osm.search.SearchCompiler.Match;
import org.openstreetmap.josm.data.osm.search.SearchParseError;
import org.openstreetmap.josm.data.osm.search.SearchSetting;
import org.openstreetmap.josm.data.osm.visitor.paint.relations.Multipolygon;
import org.openstreetmap.josm.data.osm.visitor.paint.relations.Multipolygon.JoinedWay;
import org.openstreetmap.josm.data.projection.Projection;
import org.openstreetmap.josm.data.projection.Projections;
import org.openstreetmap.josm.gui.ExtendedDialog;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.Notification;
import org.openstreetmap.josm.gui.dialogs.relation.DownloadRelationMemberTask;
import org.openstreetmap.josm.gui.dialogs.relation.DownloadRelationTask;
import org.openstreetmap.josm.gui.io.DownloadPrimitivesTask;
import org.openstreetmap.josm.gui.io.DownloadPrimitivesWithReferrersTask;
import org.openstreetmap.josm.gui.progress.swing.PleaseWaitProgressMonitor;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.io.OsmTransferException;
import org.openstreetmap.josm.plugins.PluginHandler;
import org.openstreetmap.josm.plugins.myawesomeplugin.utils.NodeWayUtils;
import org.openstreetmap.josm.plugins.utilsplugin2.actions.SplitObjectAction;
import org.openstreetmap.josm.tools.CopyList;
import org.openstreetmap.josm.tools.Geometry;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Shortcut;
import org.xml.sax.SAXException;

import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.tools.Pair;
import org.openstreetmap.josm.data.preferences.IntegerProperty;

import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Callable;

/**
 * Extends current selection by selecting nodes on all touched ways
 */
public class MagicCutterAction extends JosmAction {

    private DataSet ds;
    Collection<Way> allWays;
    Way selectedWay;
    String offsetMode;
    Set<Relation> newRelations;

    Map<String, String> tags = new HashMap<>();
    public static final IntegerProperty OSM_DOWNLOAD_TIMEOUT = new IntegerProperty("remotecontrol.osm.download.timeout",
            5 * 60);

    public MagicCutterAction() {
        super(tr("Magic Multipolygon Cutter"), "multipolygon", tr("Select intersecting ways"),
                Shortcut.registerShortcut("tools:intway", tr("Selection: {0}", tr("Intersecting ways")),
                        KeyEvent.VK_I, Shortcut.DIRECT),
                true);
        putValue("help", ht("/Action/SelectIntersectingWays"));
    }

    @Override
    public synchronized void actionPerformed(ActionEvent e) {
        ds = getLayerManager().getActiveDataSet();
        Collection<Way> selectedWays = ds.getSelectedWays();
        newRelations = new HashSet<>();

        if (!selectedWays.isEmpty() && selectedWays.size() == 1) {

            // Selected
            Iterator<Way> iterator = selectedWays.iterator();
            this.selectedWay = iterator.next();

            this.allWays = ds.getWays();

            this.offsetMode = "positive";

            Relation foundRelation = getCrossingRelation(this.selectedWay);

            checkIfRelationIsComplete(foundRelation);

            // Logging.info("===========Positive Started ");

            // cutWithOffset(selectedWays, "positive");

            // Logging.info("===========Negative Started ");

            // cutWithOffset(selectedWays, "negative");

        } else {
            new Notification(
                    tr("Please select some ways to find connected and intersecting ways!"))
                    .setIcon(JOptionPane.WARNING_MESSAGE).show();
        }
    }

    private void checkIfRelationIsComplete(Relation relation) {

        if (relation.getIncompleteMembers().size() > 0) {

            DownloadPrimitivesTask downloadTask = new DownloadPrimitivesTask(
                    MainApplication.getLayerManager().getEditLayer(),
                    Collections.singletonList(relation), true);

            Future<?> future = MainApplication.worker.submit(downloadTask);

            Runnable runAfterTask = new Runnable() {
                public void run() {
                    // this is not strictly necessary because of the type of executor service
                    // Main.worker is initialized with, but it doesn't harm either
                    //
                    try {
                        future.get();
                        Logging.info("Downloaded");

                        cutRelation(relation);

                        // condition.set(true);
                    } catch (InterruptedException | ExecutionException e) {

                        e.printStackTrace();
                    }
                }
            };
            MainApplication.worker.submit(runAfterTask);

        } else {
            //
            cutRelation(relation);
        }
    }

    public Relation getCrossingRelation(Way selectedWay) {

        Set<Way> newIntersectedWays = new HashSet<>();
        // Check ways that intersects with selected Way
        NodeWayUtils.addWaysIntersectingWays(this.allWays, Collections.singletonList(selectedWay), newIntersectedWays);

        // Check if intersected Way is part of Relation
        Set<Relation> parentRelations = OsmPrimitive.getParentRelations(newIntersectedWays);

        for (Relation relation : parentRelations) {

            if ("multipolygon".equals(relation.get("type"))) {
                return relation;
            }

        }

        return null;

    }

    public void cutRelation(Relation relation) {

        Set<Relation> relations = new HashSet<>();

        // complete Relation
        processRelation(relation, this.selectedWay, this.offsetMode);

        relations.add(relation);
        this.offsetMode = "negative";

        relation = getCrossingRelation(this.selectedWay);
        processRelation(relation, this.selectedWay, this.offsetMode);

        relations.add(relation);

        // final relation to be deleted
        relation = getCrossingRelation(this.selectedWay);

        Collection<OsmPrimitive> relationToDelete = new ArrayList<>();
        relationToDelete.add(relation);

        for (Way way : relation.getMemberPrimitives(Way.class)) {
            Set<Relation> parentRelations = OsmPrimitive.getParentRelations(Collections.singleton(way));
            if (parentRelations.size() == 1) {
                relationToDelete.add(way);
            }
        }

        if (!relationToDelete.isEmpty()) {

            Command cmd = DeleteCommand.delete(relationToDelete, true, false);
            UndoRedoHandler.getInstance().add(cmd);

        }

        this.newRelations.addAll(relations);
        getLayerManager().getEditDataSet().setSelected(this.newRelations);

    }

    public void processRelation(Relation relation, Way selectedWay, String offsetMode) {

        tryToCutRelationWithWayV2(relation, selectedWay, offsetMode);
        // tryToCutRelationWithWay(relation, selectedWay, "first", offsetMode);
        // tryToCutRelationWithWay(relation, selectedWay, "last", offsetMode);

    }

    void tryToCutRelationWithWayV2(Relation relation, Way selectedWay, String offsetMode) {

        // 1 check start/end point ouside outer polygon
        boolean firstOutside = checkNodeWithinMultipolygon(relation, selectedWay.firstNode());
        boolean latstOutside = checkNodeWithinMultipolygon(relation, selectedWay.lastNode());

        Node startingNode;

        if (firstOutside && latstOutside) {
            // error
        } else if (firstOutside) {
            startingNode = selectedWay.firstNode();
        } else if (latstOutside) {
            startingNode = selectedWay.lastNode();
        }

        // 2 check if selected way crosses inner polygon
        Set<Way> newIntersectedWays = new HashSet<>();
        // Check ways that intersects with selected Way
        // boolean crossingInner = false;
        Way crossingInnerWay = null;

        NodeWayUtils.addWaysIntersectingWays(this.allWays, Collections.singletonList(selectedWay), newIntersectedWays);

        // TODO check if inner is closest to outer
        relation.getMemberPrimitives(Way.class);
        List<OsmPrimitive> innerMembers = new ArrayList<>(relation.findRelationMembers("inner"));
        for (OsmPrimitive primitive : innerMembers) {
            if (primitive instanceof Way) {
                Way wayElement = (Way) primitive;
                // crossingInner = ;
                if (newIntersectedWays.contains(wayElement)) {
                    crossingInnerWay = wayElement;
                    break;
                }
            }
        }

        // 3 if not, check if line crosses polygon fully, proceed with standard to find
        // next way for offset if not fully crossed
        // if crosses, needs offset line?, get nodes of way, check until first will be
        // inside inner
        if (crossingInnerWay != null) {

            Collection<Way> ways = new ArrayList<>(relation.getMemberPrimitives(Way.class));
            MultipolygonBuilder multipolygon = new MultipolygonBuilder();
            String error = multipolygon.makeFromWays(ways);
            // if (error != null)
            // return null;
            List<JoinedPolygon> innerRings = multipolygon.innerWays;

            Area innerArea = null;

            for (JoinedPolygon innerRing : innerRings) {
                List<Way> innerWays = innerRing.ways;
                if (innerWays.contains(crossingInnerWay)) {
                    //
                    innerArea = innerRing.area;
                }
            }

            List<Node> collectedWays = new ArrayList<>();

            Way firstWay = checkWayAndTryToMinimize(selectedWay, startingNode, innerArea, 3, mode);

            // MultipolygonBuilder.JoinedPolygon
        }

        // copy new way using start and found node
        // intersecting ways -> nodes, split -> removeoutside
        // keep new nodes -> split inner , and outer
        // compare by length
        // delete holes
        // add new lines to relation with outer roles
        // replace innrer roles to outer

    }

    // private boolean isOuterMemberOfMultipolygon(OsmPrimitive ref) {
    // return ref instanceof Relation
    // && ref.isSelected()
    // && ((Relation) ref).isMultipolygon()
    // && ((Relation) ref).getMembersFor(Collections.singleton(this)).stream()
    // .anyMatch(rm -> "outer".equals(rm.getRole()));
    // }

    // public void cutWithOffset(Collection<Way> selectedWays, String offsetMode) {

    // Collection<Way> allWays = ds.getWays();
    // Set<Way> newIntersectedWays = new HashSet<>();
    // // Check ways that intersects with selected Way
    // NodeWayUtils.addWaysIntersectingWays(allWays, selectedWays,
    // newIntersectedWays);

    // // Check if intersected Way is part of Relation
    // Set<Relation> parentRelations =
    // OsmPrimitive.getParentRelations(newIntersectedWays);

    // for (Relation relation : parentRelations) {
    // // Check Relation type is Multipolygon
    // if ("multipolygon".equals(relation.get("type"))) {

    // // Check if all memebers are downloaded
    // if (relation.getIncompleteMembers().size() > 0) {

    // DownloadPrimitivesTask downTask = new DownloadPrimitivesTask(
    // MainApplication.getLayerManager().getEditLayer(),
    // Collections.singletonList(relation), true);

    // // Runnable runAfterTask = new Runnable() {
    // // public void run() {
    // // // this is not strictly necessary because of the type of executor service
    // // // Main.worker is initialized with, but it doesn't harm either
    // // //
    // // try {
    // // future.get();
    // // Logging.info("Download co try");

    // // condition.set(true);
    // // } catch (InterruptedException | ExecutionException e) {

    // // e.printStackTrace();
    // // }
    // // }
    // // };
    // // MainApplication.worker.submit(runAfterTask);

    // Thread appThread = new Thread() {
    // public void run() {
    // try {
    // SwingUtilities.invokeLater(downTask);
    // } catch (Exception e) {
    // e.printStackTrace();
    // }
    // System.out.println("Finished on " + Thread.currentThread());
    // }
    // };
    // appThread.start();

    // // AtomicBoolean condition = new AtomicBoolean(false);

    // // // Set<OsmPrimitive> ret = new HashSet<>(relation.getIncompleteMembers());

    // // // DownloadOsmTask task = new DownloadOsmTask();

    // // // DownloadRelationTask
    // // // DownloadRelationMemberTask

    // // DownloadPrimitivesTask downTask = new DownloadPrimitivesTask(
    // // MainApplication.getLayerManager().getEditLayer(),
    // // Collections.singletonList(relation), true);

    // // // Future<?> future = MainApplication.worker
    // // // .submit(new
    // // //
    // DownloadPrimitivesTask(MainApplication.getLayerManager().getEditLayer(),
    // // // Collections.singletonList(relation), true));

    // // // DownloadRelationTask downTask = new DownloadRelationTask(
    // // // Collections.singletonList(relation),
    // // // MainApplication.getLayerManager().getEditLayer());

    // // // downTask.canRunInBackground()
    // // // Object lock = new Object();

    // // Future<?> future = MainApplication.worker.submit(downTask);

    // // Runnable runAfterTask = new Runnable() {
    // // public void run() {
    // // // this is not strictly necessary because of the type of executor service
    // // // Main.worker is initialized with, but it doesn't harm either
    // // //
    // // try {
    // // future.get();
    // // Logging.info("Download co try");

    // // condition.set(true);
    // // } catch (InterruptedException | ExecutionException e) {

    // // e.printStackTrace();
    // // }
    // // }
    // // };
    // // MainApplication.worker.submit(runAfterTask);

    // // // Thread thr = new Thread(runAfterTask);
    // // // thr.start();

    // // // Thread thr = new Thread(() -> {
    // // // // Do some background task here

    // // // // Update the UI on the EDT using SwingUtilities.invokeLater()

    // // // SwingUtilities.invokeLater(runAfterTask);

    // // // });
    // // // thr.start();

    // // while (!condition.get()) {
    // // System.out.println("Waiting for condition to be true...");
    // // try {
    // // Thread.sleep(500);
    // // } catch (InterruptedException e) {
    // // // TODO Auto-generated catch block
    // // e.printStackTrace();
    // // }

    // // // Logging.info("cool!");
    // // }

    // Logging.info("Runaway!");

    // } else {
    // processRelationOld(relation, selectedWays, offsetMode);
    // }

    // }

    // }

    // }

    // public void processRelationOld(Relation relation, Collection<Way>
    // selectedWays, String offsetMode) {

    // processWay(relation, selectedWays, "first", offsetMode);
    // processWay(relation, selectedWays, "last", offsetMode);

    // }

    public void tryToCutRelationWithWay(Relation relation, Way selectedWay, String direction, String mode) {
        Way sourceWay = null;

        do {
            sourceWay = selectedWay;
            Logging.info("Working with way id: " + sourceWay.getUniqueId());

            Way finalWayForFirst = checkWayAndTryToFollow(sourceWay, direction, relation, 3, mode);

            Set<Way> newIntersectedWays2 = new HashSet<>();
            Logging.info("Try to create intersection");
            // allWays relation.getMemberPrimitives(Way.class)

            // Find ways of relation intersecting with new Way
            NodeWayUtils.addWaysIntersectingWays(relation.getMemberPrimitives(Way.class),
                    Collections.singletonList(finalWayForFirst), newIntersectedWays2);

            for (Way way : relation.getMemberPrimitives(Way.class)) {
                Logging.info("Relation way id: " + way.getUniqueId());
            }

            List<Way> wayList = new ArrayList<>();
            wayList.addAll(newIntersectedWays2);
            wayList.add(finalWayForFirst);

            for (Way way : wayList) {
                Logging.info("Intersected way id: " + way.getUniqueId());
                ds.addSelected(way);
            }

            Logging.info("Selected ways " + wayList.size());

            // Create Nodes at intersections
            Set<Node> intersectionNodes = createIntersection(wayList);
            List<Node> intersectionNodes2 = new ArrayList<>();
            intersectionNodes2.addAll(intersectionNodes);

            // Split way
            // doSplitWayShowSegmentSelection(finalWayForFirst, intersectionNodes2);
            List<List<Node>> wayChunks = SplitWayCommand.buildSplitChunks(finalWayForFirst,
                    intersectionNodes2);
            Logging.info("Waychunks count: " + wayChunks.size());

            // Collection<? extends OsmPrimitive> resultWays = Collections.emptyList();
            SplitWayCommand result = SplitWayCommand.splitWay(finalWayForFirst,
                    wayChunks, Collections.emptyList());
            List<Command> cmds = new LinkedList<>();
            cmds.add(result);

            // Logging.info("Results " + resultWays.size());
            // Now we have splitted ways
            // Try to Split multipolygon
            if (!cmds.isEmpty()) {
                UndoRedoHandler.getInstance().add(new SequenceCommand(tr("Split way"),
                        cmds));

                getLayerManager().getEditDataSet().setSelected(result.getNewSelection());

            }

            Collection<OsmPrimitive> results = getLayerManager().getEditDataSet().getSelected();

            Collection<OsmPrimitive> waysToDelete = new ArrayList<>();

            for (OsmPrimitive element : results) {
                if (element instanceof Way) {

                    Way wayElement = (Way) element;
                    Logging.info("Relation to split: " + relation.getId());
                    Logging.info("Way for split: " + wayElement.getUniqueId());

                    try {
                        Logging.info("Relation incomplete members: " + relation.getIncompleteMembers().size());
                        Pair<List<Relation>, List<Command>> Pairs = SplitObjectAction.splitMultipolygonAtWay(relation,
                                wayElement, true);
                        Logging.info("Split succesfull");
                        List<Relation> newRelations = Pairs.a;

                        for (Relation splitRelation : newRelations) {
                            if (splitRelation.isNew()) {
                                this.newRelations.add(splitRelation);
                            }
                        }

                    } catch (IllegalArgumentException err) {
                        Logging.info("Caught IllegalArgumentException: " + err.getMessage());
                        waysToDelete.add(wayElement);
                    }

                }

            }

            // delete unused ways
            if (!waysToDelete.isEmpty()) {

                Command cmd = DeleteCommand.delete(waysToDelete, true, false);
                UndoRedoHandler.getInstance().add(cmd);

            }

            break;
        }

        while (sourceWay != null);
    }

    // public static void doSplitWayShowSegmentSelection(Way splitWay, List<Node>
    // splitNodes, List<OsmPrimitive> selection) {
    // final List<List<Node>> wayChunks = SplitWayCommand.buildSplitChunks(splitWay,
    // splitNodes);
    // if (wayChunks != null) {
    // final List<Way> newWays = SplitWayCommand.createNewWaysFromChunks(splitWay,
    // wayChunks);
    // final Way wayToKeep =
    // SplitWayCommand.Strategy.keepLongestChunk().determineWayToKeep(newWays);

    // if (wayToKeep != null) {
    // doSplitWay(splitWay, wayToKeep, newWays, selection);
    // }
    // }
    // }

    // static void doSplitWay(Way way, Way wayToKeep, List<Way> newWays,
    // List<OsmPrimitive> newSelection) {
    // final MapFrame map = MainApplication.getMap();
    // final boolean isMapModeDraw = map != null && map.mapMode == map.mapModeDraw;

    // Optional<SplitWayCommand> splitWayCommand = SplitWayCommand.doSplitWay(
    // way,
    // wayToKeep,
    // newWays,
    // !isMapModeDraw ? newSelection : null,
    // SplitWayCommand.WhenRelationOrderUncertain.ASK_USER_FOR_CONSENT_TO_DOWNLOAD
    // );

    // splitWayCommand.ifPresent(result -> {
    // UndoRedoHandler.getInstance().add(result);
    // List<? extends PrimitiveId> newSel = result.getNewSelection();
    // if (!Utils.isEmpty(newSel)) {
    // way.getDataSet().setSelected(newSel);
    // }
    // });
    // if (!splitWayCommand.isPresent()) {
    // newWays.forEach(w -> w.setNodes(null)); // see 19885
    // }
    // }

    private Set<Node> createIntersection(List<Way> ways) {

        LinkedList<Command> cmds = new LinkedList<>();
        Set<Node> Nodes = Geometry.addIntersections(ways, false, cmds);
        Logging.info("Created nodes count " + Nodes.size());
        if (!cmds.isEmpty()) {
            UndoRedoHandler.getInstance().add(new SequenceCommand(tr("Add nodes at intersections"), cmds));
            Set<Node> nodes = new HashSet<>(10);
            // find and select newly added nodes
            for (Command cmd : cmds)
                if (cmd instanceof AddCommand) {
                    Collection<? extends OsmPrimitive> pp = cmd.getParticipatingPrimitives();
                    for (OsmPrimitive p : pp) { // find all affected nodes
                        if (p instanceof Node && p.isNew())
                            nodes.add((Node) p);
                    }
                    if (!nodes.isEmpty()) {

                        getLayerManager().getEditDataSet().setSelected(nodes);
                    }
                }
        }
        return Nodes;
    }

    private Way checkWayAndTryToMinimize(Way initialWay, Node startingNode, Area innerArea) {

        Way partWay = new Way();

        List<Node> initialWayNodes = initialWay.getNodes();
        if (startingNode.getUniqueId() == initialWay.firstNode().getUniqueId()) {
            // Forward loop
            for (int i = 0; i < initialWayNodes.size(); i++) {
                Node node = initialWayNodes.get(i);
                partWay.addNode(node);
                EastNorth en = node.getEastNorth();
                if (innerArea.contains(en.east(), en.north())) {
                    break;
                }
            }

        } else if (startingNode.getUniqueId() == initialWay.lastNode().getUniqueId()) {
            // Reverse loop
            for (int i = initialWayNodes.size() - 1; i >= 0; i--) {
                Node node = initialWayNodes.get(i);
                partWay.addNode(node);
                EastNorth en = node.getEastNorth();
                if (innerArea.contains(en.east(), en.north())) {
                    break;
                }
            }

        }

        return partWay;
    }

    // private Way processNode(Node startingNode){
    // //
    // }

    private Way checkWayAndTryToFollow(Way mergedWay, String direction, Relation relation, Integer attempt,
            String mode) {
        if (attempt == null) {
            attempt = 5;
        }
        // int attempts = 5;
        Way offsetWay = null;

        while (attempt > 0) {

            Logging.info("Direction (" + direction + "), Attemt started: " + attempt);

            offsetWay = getOffsetWay(mergedWay, mode);
            Logging.info("offsetWay created");
            Command addOffsetWayCommand = createAddWayCommand(offsetWay);
            UndoRedoHandler.getInstance().add(addOffsetWayCommand);
            Logging.info("offsetWay added to dataset: " + offsetWay.getId());

            Node endingOffsetNode = getEndingNode(offsetWay, direction);
            Logging.info("offsetWay END node (" + direction + ") found");

            boolean found = checkNodeWithinMultipolygon(relation, endingOffsetNode);

            if (found) {

                attempt--;
                if (attempt == 0) {
                    Logging.info("Last Attemt failed: " + attempt);
                    return null;
                } else {
                    // Remove previous Way
                    Logging.info("Remove previous offsetWay from dataset");

                    Integer count = addOffsetWayCommand.getChildren().size();
                    Logging.info("Commands count: " + count);
                    UndoRedoHandler.getInstance().undo(count);

                    // Node firstNodeMergedWay = mergedWay.firstNode();
                    Node endingNodeMergedWay = getEndingNode(mergedWay, direction);
                    Way nextConnectedWay = findConnectedWayWithSameTags(mergedWay, endingNodeMergedWay);
                    if (nextConnectedWay == null) {
                        break;
                    }
                    Logging.info("Direction (" + direction + "). Found connected way: " + nextConnectedWay.getId());
                    Way newMergedWay = mergeWays(mergedWay, nextConnectedWay);
                    Way foundWay = checkWayAndTryToFollow(newMergedWay, direction, relation, attempt, mode);
                    if (foundWay != null) {
                        Logging.info("Returning merged way, on attempt " + attempt);
                        // break;
                        return foundWay;
                    }
                }

            } else {
                Logging.info("Node not found. offsetWay remains in dataset.");
                break;
            }

        }

        Logging.info("return offsetWay added to dataset: " + offsetWay.getId());
        return offsetWay;

    }

    private Node getEndingNode(Way way, String direction) {
        if (direction.equals("first")) {
            return way.firstNode();
        } else {
            return way.lastNode();
        }
    }

    private Command createAddWayCommand(Way newWay) {

        List<Command> commands = new LinkedList<>();
        List<Node> sourceNodes = newWay.getNodes();

        for (Node originalNode : sourceNodes) {
            commands.add(new AddCommand(this.ds, originalNode));
        }

        commands.add(new AddCommand(ds, newWay));

        return new SequenceCommand(tr("Add way"), commands);

    }

    private static Way mergeWays(Way way1, Way way2) {
        // Combine nodes from both ways into a new list
        // List<Node> combinedNodes = new ArrayList<>(way1.getNodes());
        // combinedNodes.addAll(way2.getNodes());

        // Create a new way with the combined nodes
        Way mergedWay = new Way();

        // Use a set to keep track of unique nodes
        Set<Node> uniqueNodes = new HashSet<>();

        for (Node node : way2.getNodes()) {
            if (uniqueNodes.add(node)) {
                mergedWay.addNode(node);
            }
        }

        for (Node node : way1.getNodes()) {
            if (uniqueNodes.add(node)) {
                mergedWay.addNode(node);
            }
        }

        // Copy tags and other attributes as needed
        // mergedWay.setKeys(way1.getKeys());

        return mergedWay;
    }

    private boolean checkNodeWithinMultipolygon(Relation relation, Node fNode) {
        // Check if created ways start and end points a within relation multipolygon
        Collection<OsmPrimitive> primitivesInsideMultipolygon = NodeWayUtils
                .selectAllInside(Collections.singletonList(relation), this.ds, false);

        for (OsmPrimitive osmPrimitive : primitivesInsideMultipolygon) {
            if (osmPrimitive instanceof Node) {

                Node node = (Node) osmPrimitive;
                if (node.getCoor().equals(fNode.getCoor())) {
                    // ds.addSelected(osmPrimitive);
                    System.out.println("Found Node: " + node.getId());

                    return true;
                }

            }
        }
        return false;

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

    public Way getOffsetWay(Way sourceWay, String mode) {

        // Offset distance in meters
        double offsetDistance = 0.0001;

        if (mode.equals("negative")) {
            offsetDistance = offsetDistance * -1;
        }

        List<Node> sourceNodes = sourceWay.getNodes();
        List<Node> offsetNodes = new ArrayList<>();

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

            Node offsetNode = new Node(new LatLon(newLat, newLon));
            offsetNodes.add(offsetNode);

        }

        // Create a new way with the offset nodes
        Way offsetWay = new Way();

        // Split way should not contain tags
        // tags.put("road", "" + attempt);
        // tags.put("note", "offset");
        offsetWay.setKeys(tags);
        offsetWay.setNodes(offsetNodes);

        return offsetWay;

    }

    // private static Way offsetWay(Collection<Way> sourceWays, DataSet ds) {

    // // Offset distance in meters
    // double offsetDistance2 = 0.0001;

    // Collection<JoinedWay> list = combineWays(sourceWays);

    // if (list.size() == 1) {
    // // ways form a single line string
    // List<Node> sourceNodes = Collections.unmodifiableList(new
    // ArrayList<>(list.iterator().next().getNodes()));

    // // Way sourceWay = combineWays.iterator().next();

    // // List<Node> sourceNodes = sourceWay.getNodes();
    // List<Node> offsetNodes = new ArrayList<>();
    // List<Command> commands = new LinkedList<>();

    // // Calculate the heading from start to end points
    // double heading = calculateHeading(sourceNodes.get(0).getCoor(),
    // sourceNodes.get(sourceNodes.size() - 1).getCoor());

    // // Offset the line by 90 degrees from the calculated heading
    // double offsetHeading = (heading + 90) % 360;

    // for (Node originalNode : sourceNodes) {
    // // Offset the coordinates based on the new heading
    // double newLat = originalNode.getCoor().lat() +
    // Math.cos(Math.toRadians(offsetHeading)) * offsetDistance;
    // double newLon = originalNode.getCoor().lon() +
    // Math.sin(Math.toRadians(offsetHeading)) * offsetDistance;

    // // Create a new node with the offset coordinates
    // // offsetNodes.add(new Node(new LatLon(newLat, newLon)));
    // Node offsetNode = new Node(new LatLon(newLat, newLon));
    // offsetNodes.add(offsetNode);

    // commands.add(new AddCommand(ds, offsetNode));

    // Logging.info("x 1:" + originalNode.getEastNorth().getX());
    // Logging.info("x 1:" + offsetNode.getEastNorth().getX());

    // }

    // // Create a new way with the offset nodes
    // Way offsetLine = new Way();
    // offsetLine.setNodes(offsetNodes);

    // commands.add(new AddCommand(ds, offsetLine));
    // UndoRedoHandler.getInstance().add(new SequenceCommand(tr("Add way"),
    // commands));
    // ds.addSelected(offsetLine);

    // return offsetLine;
    // // Multipolygon#joinWays
    // }

    // return null;
    // }

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