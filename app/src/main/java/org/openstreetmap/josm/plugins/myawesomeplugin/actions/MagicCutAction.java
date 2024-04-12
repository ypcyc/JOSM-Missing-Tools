package org.openstreetmap.josm.plugins.myawesomeplugin.actions;

import static org.openstreetmap.josm.tools.I18n.marktr;
import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Arrays;

import javax.swing.JOptionPane;

import org.openstreetmap.josm.actions.mapmode.MapMode;
import org.openstreetmap.josm.actions.mapmode.ParallelWays;
import org.openstreetmap.josm.command.AddCommand;
import org.openstreetmap.josm.command.ChangeCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.DeleteCommand;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.command.SplitWayCommand;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.SystemOfMeasurement;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.coor.ILatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.DefaultNameFormatter;
import org.openstreetmap.josm.data.osm.MultipolygonBuilder;
import org.openstreetmap.josm.data.osm.MultipolygonBuilder.JoinedPolygon;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.WaySegment;
import org.openstreetmap.josm.data.preferences.AbstractToStringProperty;
import org.openstreetmap.josm.data.preferences.BooleanProperty;
import org.openstreetmap.josm.data.preferences.CachingProperty;
import org.openstreetmap.josm.data.preferences.DoubleProperty;
import org.openstreetmap.josm.data.preferences.IntegerProperty;
import org.openstreetmap.josm.data.preferences.NamedColorProperty;
import org.openstreetmap.josm.data.preferences.StrokeProperty;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.Notification;
import org.openstreetmap.josm.gui.draw.MapViewPath;
import org.openstreetmap.josm.gui.layer.AbstractMapViewPaintable;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.util.ModifierExListener;
import org.openstreetmap.josm.plugins.myawesomeplugin.utils.NodeWayUtils;
import org.openstreetmap.josm.plugins.myawesomeplugin.utils.OsmEdge;
import org.openstreetmap.josm.plugins.myawesomeplugin.utils.RoutingGraph;

import org.openstreetmap.josm.plugins.utilsplugin2.actions.SplitObjectAction;
import org.openstreetmap.josm.tools.CheckParameterUtil;
import org.openstreetmap.josm.tools.Geometry;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Pair;
import org.openstreetmap.josm.tools.Shortcut;

/**
 * MapMode for making parallel ways.
 * <p>
 * All calculations are done in projected coordinates.
 * <p>
 * TODO:
 * <p>
 * == Functionality ==
 * <ol>
 * <li>Use selected nodes as split points for the selected ways.
 * <p>
 * The ways containing the selected nodes will be split and only the "inner"
 * parts will be copied</li>
 * <li>Enter exact offset</li>
 * <li>Improve snapping</li>
 * <li>Visual cues could be better</li>
 * <li>(long term) Parallelize and adjust offsets of existing ways</li>
 * </ol>
 * == Code quality ==
 * <ol type="a">
 * <li>The mode, flags, and modifiers might be updated more than necessary.
 * <p>
 * Not a performance problem, but better if they where more centralized</li>
 * <li>Extract generic MapMode services into a super class and/or utility
 * class</li>
 * <li>Maybe better to simply draw our own source way highlighting?</li>
 * </ol>
 * Current code doesn't not take into account that ways might been highlighted
 * by other than us. Don't think that situation should ever happen though.
 *
 * @author Ole Jørgen Brønner (olejorgenb)
 */
public class MagicCutAction extends MapMode implements ModifierExListener {

    private static final CachingProperty<BasicStroke> HELPER_LINE_STROKE = new StrokeProperty(
            prefKey("stroke.hepler-line"), "1").cached();
    private static final CachingProperty<BasicStroke> REF_LINE_STROKE = new StrokeProperty(prefKey("stroke.ref-line"),
            "2 2 3").cached();

    // @formatter:off
    // CHECKSTYLE.OFF: SingleSpaceSeparator
    private static final CachingProperty<Double> SNAP_THRESHOLD         = new DoubleProperty(prefKey("snap-threshold-percent"), 0.70).cached();
    private static final CachingProperty<Boolean> SNAP_DEFAULT          = new BooleanProperty(prefKey("snap-default"),      true).cached();
    private static final CachingProperty<Boolean> COPY_TAGS_DEFAULT     = new BooleanProperty(prefKey("copy-tags-default"), false).cached();
    private static final CachingProperty<Integer> INITIAL_MOVE_DELAY    = new IntegerProperty(prefKey("initial-move-delay"), 200).cached();
    private static final CachingProperty<Double> SNAP_DISTANCE_METRIC   = new DoubleProperty(prefKey("snap-distance-metric"), 0.5).cached();
    private static final CachingProperty<Double> SNAP_DISTANCE_IMPERIAL = new DoubleProperty(prefKey("snap-distance-imperial"), 1).cached();
    private static final CachingProperty<Double> SNAP_DISTANCE_CHINESE  = new DoubleProperty(prefKey("snap-distance-chinese"), 1).cached();
    private static final CachingProperty<Double> SNAP_DISTANCE_NAUTICAL = new DoubleProperty(prefKey("snap-distance-nautical"), 0.1).cached();
    private static final CachingProperty<Color> MAIN_COLOR = new NamedColorProperty(marktr("make parallel helper line"), Color.RED).cached();

    private static final CachingProperty<Map<Modifier, Boolean>> SNAP_MODIFIER_COMBO
            = new KeyboardModifiersProperty(prefKey("snap-modifier-combo"),             "?sC").cached();
    private static final CachingProperty<Map<Modifier, Boolean>> COPY_TAGS_MODIFIER_COMBO
            = new KeyboardModifiersProperty(prefKey("copy-tags-modifier-combo"),        "As?").cached();
    private static final CachingProperty<Map<Modifier, Boolean>> ADD_TO_SELECTION_MODIFIER_COMBO
            = new KeyboardModifiersProperty(prefKey("add-to-selection-modifier-combo"), "aSc").cached();
    private static final CachingProperty<Map<Modifier, Boolean>> TOGGLE_SELECTED_MODIFIER_COMBO
            = new KeyboardModifiersProperty(prefKey("toggle-selection-modifier-combo"), "asC").cached();
    private static final CachingProperty<Map<Modifier, Boolean>> SET_SELECTED_MODIFIER_COMBO
            = new KeyboardModifiersProperty(prefKey("set-selection-modifier-combo"),    "asc").cached();
    // CHECKSTYLE.ON: SingleSpaceSeparator
    // @formatter:on

    enum Mode {
        DRAGGING, NORMAL
    }

    //// Preferences and flags
    // See updateModeLocalPreferences for defaults
    private Mode mode;
    private boolean copyTags;

    private boolean snap;

    private final MapView mv;

    // Mouse tracking state
    private Point mousePressedPos;
    private boolean mouseIsDown;
    private long mousePressedTime;
    private boolean mouseHasBeenDragged;

    private transient WaySegment referenceSegment;
    private transient ParallelWays pWays;
    private transient ParallelWays pWaysMirrored;
    private transient Set<Way> sourceWays;
    private EastNorth helperLineStart;
    private EastNorth helperLineEnd;

    private final ParallelWayLayer temporaryLayer = new ParallelWayLayer();

    // Custom
    private DataSet ds;
    // private List<Way> ringOneCrossingWays;
    private List<Way> ringTwoCrossingWays;
    private List<Node> intersectionNodes;
    private List<Way> createdWays;
    Set<Relation> newRelations;
    long cutPathId;

    private static final List<String> IRRELEVANT_KEYS = Arrays.asList("source", "created_by", "note");

    /**
     * Constructs a new {@code MagicCutAction}.
     * 
     * @param mapFrame Map frame
     */
    public MagicCutAction(MapFrame mapFrame) {
        super(tr("Polygon Cut"),
                "polycut",
                tr("Cut Polygon by parallel copies of ways"),
                Shortcut.registerShortcut("mapmode:parallel", tr("Mode: {0}",
                        tr("Polygon Cut")), KeyEvent.VK_Y, Shortcut.DIRECT),
                ImageProvider.getCursor("normal", "parallel"));
        // setHelpId(ht("/Action/Parallel"));
        mv = mapFrame.mapView;
    }

    @Override
    public void enterMode() {
        // super.enterMode() updates the status line and cursor so we need our state to
        // be set correctly
        setMode(Mode.NORMAL);
        pWays = null;
        pWaysMirrored = null;
        super.enterMode();

        // #19887: overwrite default: we want to show the distance to the original way
        MainApplication.getMap().statusLine.setAutoLength(false);

        mv.addMouseListener(this);
        mv.addMouseMotionListener(this);
        mv.addTemporaryLayer(temporaryLayer);

        // Needed to update the mouse cursor if modifiers are changed when the mouse is
        // motionless
        MainApplication.getMap().keyDetector.addModifierExListener(this);
        sourceWays = new LinkedHashSet<>(getLayerManager().getEditDataSet().getSelectedWays());
        for (Way w : sourceWays) {
            w.setHighlighted(true);
        }
    }

    @Override
    public void exitMode() {
        super.exitMode();
        mv.removeMouseListener(this);
        mv.removeMouseMotionListener(this);
        mv.removeTemporaryLayer(temporaryLayer);
        MapFrame map = MainApplication.getMap();
        map.statusLine.setDist(-1);
        map.keyDetector.removeModifierExListener(this);
        removeWayHighlighting(sourceWays);
        pWays = null;
        pWaysMirrored = null;
        sourceWays = null;
        referenceSegment = null;
    }

    @Override
    public String getModeHelpText() {
        // TODO: add more detailed feedback based on modifier state.
        // TODO: dynamic messages based on preferences. (Could be problematic
        // translation wise)
        switch (mode) {
            case NORMAL:
                // CHECKSTYLE.OFF: LineLength
                return tr(
                        "Select ways as in Select mode. Drag selected ways or a single way to create a parallel copy (Alt toggles tag preservation)");
            // CHECKSTYLE.ON: LineLength
            case DRAGGING:
                return tr("Hold Ctrl to toggle snapping");
        }
        return ""; // impossible ..
    }

    @Override
    public boolean layerIsSupported(Layer l) {
        return isEditableDataLayer(l);
    }

    @Override
    public void modifiersExChanged(int modifiers) {
        if (MainApplication.getMap() == null || mv == null || !mv.isActiveLayerDrawable())
            return;

        // Should only get InputEvents due to the mask in enterMode
        if (updateModifiersState(modifiers)) {
            updateStatusLine();
            updateCursor();
        }
    }

    private boolean updateModifiersState(int modifiers) {
        boolean oldAlt = alt, oldShift = shift, oldCtrl = ctrl;
        updateKeyModifiersEx(modifiers);
        return oldAlt != alt || oldShift != shift || oldCtrl != ctrl;
    }

    private void updateCursor() {
        Cursor newCursor = null;
        switch (mode) {
            case NORMAL:
                if (matchesCurrentModifiers(SET_SELECTED_MODIFIER_COMBO)) {
                    newCursor = ImageProvider.getCursor("normal", "parallel");
                } else if (matchesCurrentModifiers(ADD_TO_SELECTION_MODIFIER_COMBO)) {
                    newCursor = ImageProvider.getCursor("normal", "parallel_add");
                } else if (matchesCurrentModifiers(TOGGLE_SELECTED_MODIFIER_COMBO)) {
                    newCursor = ImageProvider.getCursor("normal", "parallel_remove");
                }
                break;
            case DRAGGING:
                newCursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);
                break;
            default:
                throw new AssertionError();
        }
        if (newCursor != null) {
            mv.setNewCursor(newCursor, this);
        }
    }

    private void setMode(Mode mode) {
        this.mode = mode;
        updateCursor();
        updateStatusLine();
    }

    private boolean sanityCheck() {
        // @formatter:off
        boolean areWeSane =
            mv.isActiveLayerVisible() &&
            mv.isActiveLayerDrawable() &&
            ((Boolean) this.getValue("active"));
        // @formatter:on
        assert areWeSane; // mad == bad
        return areWeSane;
    }

    @Override
    public void mousePressed(MouseEvent e) {
        requestFocusInMapView();
        updateModifiersState(e.getModifiersEx());
        // Other buttons are off limit, but we still get events.
        if (e.getButton() != MouseEvent.BUTTON1)
            return;

        if (!sanityCheck())
            return;

        updateFlagsOnlyChangeableOnPress();
        updateFlagsChangeableAlways();

        creteCutPath();

        // Since the created way is left selected, we need to unselect again here
        if (pWays != null && pWays.getWays() != null) {
            getLayerManager().getEditDataSet().clearSelection(pWays.getWays());
            pWays = null;
        }

        if (pWaysMirrored != null && pWaysMirrored.getWays() != null) {
            getLayerManager().getEditDataSet().clearSelection(pWaysMirrored.getWays());
            pWaysMirrored = null;
        }

        mouseIsDown = true;
        mousePressedPos = e.getPoint();
        mousePressedTime = System.currentTimeMillis();

    }

    // Cut logic
    void creteCutPath() {

        if (ctrl) {

            ds = getLayerManager().getActiveDataSet();
            Collection<Node> selectedNodes = ds.getSelectedNodes();

            if (selectedNodes.size() != 2) {
                String msg = "Please select 2 nodes to process cut";
                new Notification(msg).setIcon(JOptionPane.INFORMATION_MESSAGE).show();
                return;
            }

            Node firstNode = selectedNodes.iterator().next();

            List<Node> selectedNodesList = new ArrayList<>(selectedNodes);
            RoutingGraph oRoutingGraph = new RoutingGraph();
            List<OsmEdge> listedge = oRoutingGraph.applyAlgorithm(ds, selectedNodesList);

            List<Node> routeNodes = new ArrayList<>();
            routeNodes.add(firstNode);

            for (OsmEdge edge : listedge) {
                Node toNode = edge.getTo();
                if (!routeNodes.contains(toNode)) {
                    routeNodes.add(toNode);
                }
            }

            Way cutPathWay = new Way();
            cutPathWay.setNodes(routeNodes);

            List<Command> commands = new ArrayList<>();
            commands.add(new AddCommand(ds, cutPathWay));

            List<OsmPrimitive> newSelection = new ArrayList<>();
            newSelection.add(cutPathWay);

            UndoRedoHandler.getInstance().add(new SequenceCommand(
                    tr("Create Cut Path"), commands));
            ds.setSelected(newSelection);

            cutPathId = cutPathWay.getUniqueId();

        }
    }

    private void performCut() {
        if (ctrl && pWays != null) {

            List<Way> offsetWays = new ArrayList<>();
            offsetWays.addAll(pWays.getWays());
            offsetWays.addAll(pWaysMirrored.getWays());

            // 1. find relation/polygon

            Relation relation = getCrossingRelation(offsetWays);
            if (relation == null) {
                Logging.info("Error: No crossing completed Relation can be found");

                pWays = null;
                pWaysMirrored = null;

                Way refWay = referenceSegment.getWay();

                offsetWays.add(refWay);

                Command cmd = DeleteCommand.delete(offsetWays, true, false);
                UndoRedoHandler.getInstance().add(cmd);

                mv.removeTemporaryLayer(temporaryLayer);
                JOptionPane.showMessageDialog(MainApplication.getMainFrame(),
                        "You are probably trying to cut incomplete polygon. Please check if all related relation memembers are downloaded");

            } else {

                Logging.info("Relation found: " + relation.getUniqueId());

                // 2. get nodes inside

                // getClosestNodesOutsideMultipolygon(relation);

                // simple no inner, end nodes outside
                // complicated with inner, only one outside

                // ringOneCrossingWays = new ArrayList<>();
                ringTwoCrossingWays = new ArrayList<>();
                intersectionNodes = new ArrayList<>();
                createdWays = new ArrayList<>();

                Logging.info("Cut with first Offset line");
                tryToCutRelationWithWayV2(relation, offsetWays.get(0));

                if (ringTwoCrossingWays.size() == 0) {
                    List<Way> tmpOffsetWays = new ArrayList<>();
                    tmpOffsetWays.add(offsetWays.get(1));
                    relation = getCrossingRelation(tmpOffsetWays);
                    Logging.info("Processing new relation: " + relation.getUniqueId());
                    tryToCutRelationWithWayV2(relation, offsetWays.get(1));
                } else {
                    Logging.info("Continue processing relation: " + relation.getUniqueId());
                    tryToCutRelationWithWayV2(relation, offsetWays.get(1));
                }

                if (ringTwoCrossingWays.size() > 0) {
                    Logging.info("Rebuilding Relation, getting Ring ways (holes) for deletion");

                    // Rebuild Relation
                    List<Way> waysForDeletion = splitRingsAndGetWaysForDeletionV2(relation);
                    reconstructRelation(relation, waysForDeletion);

                    // Delete initial route path
                    // referenceSegment
                    Command cmd = DeleteCommand.delete(Collections.singletonList(referenceSegment.getWay()), true,
                            false);
                    UndoRedoHandler.getInstance().add(cmd);

                } else {
                    // sourceWays
                    // delete relation (after Simple cut)

                    if (sourceWays == null) {
                        Logging.info("Error: No source ways found");
                        return;
                    }

                    relation = getCrossingRelation(new ArrayList<>(sourceWays));
                    if (relation == null) {
                        Logging.info("Error: No relation found");
                        return;
                    }
                    Logging.info("Delete unneeded relation: " + relation.getUniqueId());

                    Collection<OsmPrimitive> relationWithMembersToDelete = new ArrayList<>();
                    relationWithMembersToDelete.add(relation);

                    for (Way way : relation.getMemberPrimitives(Way.class)) {
                        Set<Relation> parentRelations = OsmPrimitive.getParentRelations(Collections.singleton(way));
                        if (parentRelations.size() == 1) {
                            relationWithMembersToDelete.add(way);
                        }
                    }

                    // Delete referenceSegment
                    relationWithMembersToDelete.add(referenceSegment.getWay());

                    Command cmd = DeleteCommand.delete(relationWithMembersToDelete, true, false);
                    UndoRedoHandler.getInstance().add(cmd);

                }
            }

            // Switch mode
            MapFrame map = MainApplication.getMap();
            map.selectMapMode(map.mapModeSelect);

        }

    }

    void reconstructRelation(Relation relation, List<Way> waysForDeletion) {

        Collection<OsmPrimitive> waysToDelete = new ArrayList<>();
        waysToDelete.addAll(waysForDeletion);

        // delete ways
        if (!waysToDelete.isEmpty()) {
            Command cmd = DeleteCommand.delete(waysToDelete, true, false);
            UndoRedoHandler.getInstance().add(cmd);
        }

        Collection<Way> relationMemberWays = new ArrayList<>(relation.getMemberPrimitives(Way.class));

        relationMemberWays.addAll(createdWays);

        createRelation(relation, relationMemberWays);

    }

    void createRelation(Relation r, Collection<Way> relationMemberWays) {
        MultipolygonBuilder mpc = new MultipolygonBuilder();
        String error = mpc.makeFromWays(relationMemberWays);

        if (error != null) {
            JOptionPane.showMessageDialog(MainApplication.getMainFrame(), error);
            return;
        }

        boolean relationReused = false;
        List<Command> commands = new ArrayList<>();
        List<OsmPrimitive> newSelection = new ArrayList<>();

        for (JoinedPolygon p : mpc.outerWays) {

            ArrayList<JoinedPolygon> myInnerWays = new ArrayList<>();
            for (JoinedPolygon i : mpc.innerWays) {
                // if the first point of any inner ring is contained in this
                // outer ring, then this inner ring belongs to us. This
                // assumption only works if multipolygons have valid geometries
                EastNorth en = i.ways.get(0).firstNode().getEastNorth();
                if (p.area.contains(en.east(), en.north())) {
                    myInnerWays.add(i);
                }
            }

            if (!myInnerWays.isEmpty()) {
                // this ring has inner rings, so we leave a multipolygon in
                // place and don't reconstruct the rings.
                Relation n;
                if (relationReused) {
                    n = new Relation();
                    n.setKeys(r.getKeys());
                } else {
                    n = new Relation(r);
                    n.setMembers(null);
                }
                for (Way w : p.ways) {
                    n.addMember(new RelationMember("outer", w));
                }
                for (JoinedPolygon i : myInnerWays) {
                    for (Way w : i.ways) {
                        n.addMember(new RelationMember("inner", w));
                    }
                }
                if (relationReused) {
                    commands.add(new AddCommand(ds, n));
                } else {
                    relationReused = true;
                    commands.add(new ChangeCommand(r, n));
                }
                newSelection.add(n);
                continue;
            }

            // move all tags from relation and common tags from ways
            // start with all tags from first way but only if area tags are present
            Map<String, String> tags = p.ways.get(0).getKeys();
            if (!p.ways.get(0).hasAreaTags()) {
                tags.clear();
            }
            List<OsmPrimitive> relations = p.ways.get(0).getReferrers();
            Set<String> noTags = new HashSet<>(r.keySet());
            for (int i = 1; i < p.ways.size(); i++) {
                Way w = p.ways.get(i);
                for (String key : w.keySet()) {
                    String value = w.get(key);
                    if (!noTags.contains(key) && tags.containsKey(key) && !tags.get(key).equals(value)) {
                        tags.remove(key);
                        noTags.add(key);
                    }
                }
                List<OsmPrimitive> referrers = w.getReferrers();
                relations.removeIf(osmPrimitive -> !referrers.contains(osmPrimitive));
            }
            tags.putAll(r.getKeys());
            tags.remove("type");

            // then delete ways that are not relevant (do not take part in other relations
            // or have strange tags)
            Way candidateWay = null;
            for (Way w : p.ways) {
                if (w.getReferrers().size() == 1) {
                    // check tags that remain
                    Set<String> keys = new HashSet<>(w.keySet());
                    keys.removeAll(tags.keySet());
                    IRRELEVANT_KEYS.forEach(keys::remove);
                    if (keys.isEmpty()) {
                        if (candidateWay == null) {
                            candidateWay = w;
                        } else {
                            if (candidateWay.isNew() && !w.isNew()) {
                                // prefer ways that are already in the database
                                Way tmp = w;
                                w = candidateWay;
                                candidateWay = tmp;
                            }
                            commands.add(new DeleteCommand(w));
                        }
                    }
                }
            }

            // take the first way, put all nodes into it, making it a closed polygon
            Way result = candidateWay == null ? new Way() : new Way(candidateWay);
            result.setNodes(p.nodes);
            result.addNode(result.firstNode());
            result.setKeys(tags);
            newSelection.add(candidateWay == null ? result : candidateWay);
            commands.add(candidateWay == null ? new AddCommand(ds, result) : new ChangeCommand(candidateWay, result));
        }

        UndoRedoHandler.getInstance().add(new SequenceCommand(tr("Reconstruct polygons from relation {0}",
                r.getDisplayName(DefaultNameFormatter.getInstance())), commands));
        ds.setSelected(newSelection);

    }

    List<JoinedPolygon> getParentRingsForWays(Relation relation, List<Way> ways) {

        List<JoinedPolygon> intersectingRings = new ArrayList<>();

        List<JoinedPolygon> checkRings = buildMultipolygonAndGetRings(relation);

        for (JoinedPolygon checkRing : checkRings) {

            for (Way way : ways) {
                if (checkRing.ways.contains(way)) {
                    if (!intersectingRings.contains(checkRing)) {
                        intersectingRings.add(checkRing);
                    }
                }
            }

        }

        return intersectingRings;
    }

    Way getMemberOfRing(JoinedPolygon ring, Way crossingWay) {
        for (Way way : ring.ways) {
            if (way == crossingWay) {
                return way;
            }
        }

        return null;
        // return ring.ways.get(0);
    }

    void performSimpleCut(Relation relation, Way offsetWay) {

        // Find Ways of Relation intersecting with Offset Line
        Set<Way> intersectedWaysWithOffsetLine = new HashSet<>();
        NodeWayUtils.addWaysIntersectingWays(relation.getMemberPrimitives(Way.class),
                Collections.singletonList(offsetWay), intersectedWaysWithOffsetLine);

        List<Way> intersectingWayList = new ArrayList<>();
        intersectingWayList.addAll(intersectedWaysWithOffsetLine);
        intersectingWayList.add(offsetWay);

        for (Way way : intersectingWayList) {
            Logging.info("Intersected way id: " + way.getUniqueId());
            ds.addSelected(way);
        }

        Logging.info("Selected ways for intersections" + intersectingWayList.size());

        // Create Nodes at Ways Intersections
        Set<Node> intersectionNodes = createIntersection(intersectingWayList);

        List<List<Node>> wayChunks = SplitWayCommand.buildSplitChunks(offsetWay,
                new ArrayList<>(intersectionNodes));
        Logging.info("Waychunks count: " + wayChunks.size());

        // Collection<? extends OsmPrimitive> resultWays = Collections.emptyList();
        SplitWayCommand result = SplitWayCommand.splitWay(offsetWay,
                wayChunks, Collections.emptyList());

        List<Command> splitCommands = new LinkedList<>();
        splitCommands.add(result);

        // Now we have splitted ways
        // Try to Split multipolygon
        if (!splitCommands.isEmpty()) {
            UndoRedoHandler.getInstance().add(new SequenceCommand(tr("Split multipolygon"),
                    splitCommands));
            getLayerManager().getEditDataSet().setSelected(result.getNewSelection());
        }

        Collection<OsmPrimitive> waysToDelete = new ArrayList<>();

        Collection<OsmPrimitive> resultsAfterSplit = getLayerManager().getEditDataSet().getSelected();

        for (OsmPrimitive primitive : resultsAfterSplit) {
            if (primitive instanceof Way) {

                Way wayForSplit = (Way) primitive;
                Logging.info("Relation to split: " + relation.getUniqueId());
                Logging.info("Way for split: " + wayForSplit.getUniqueId());

                try {
                    Logging.info("Relation incomplete members: " + relation.getIncompleteMembers().size());
                    Pair<List<Relation>, List<Command>> Pairs = SplitObjectAction.splitMultipolygonAtWay(relation,
                            wayForSplit, true);
                    Logging.info("Split successful");

                } catch (IllegalArgumentException err) {
                    Logging.info("Caught IllegalArgumentException: " + err.getMessage());
                    waysToDelete.add(wayForSplit);
                }

            }

        }

        // delete unused ways
        if (!waysToDelete.isEmpty()) {
            Command cmd = DeleteCommand.delete(waysToDelete, true, false);
            UndoRedoHandler.getInstance().add(cmd);
        }
    }

    void tryToCutRelationWithWayV2(Relation relation, Way offsetWay) {

        Logging.info("Cutting relation: " + relation.getUniqueId() + " with offsetWay: " + offsetWay.getUniqueId());

        // TODO offset way may cross different ways

        // 1 check if selected way crosses inner polygon
        Set<Way> intersectedWaysWithOffsetLine = new HashSet<>();
        // Check ways that intersects with selected Way
        // boolean crossingInner = false;
        Way ringOneCrossingWay = null;
        Way ringTwoCrossingWay = null;

        NodeWayUtils.addWaysIntersectingWays(getLayerManager().getActiveDataSet().getWays(),
                Collections.singletonList(offsetWay), intersectedWaysWithOffsetLine);

        // Iterator<Way> iterator = intersectedWaysWithOffsetLine.iterator();
        // Way firstWay = iterator.next();

        // if (intersectedWaysWithOffsetLine.size() == 1 && !firstWay.isClosed()) {
        // JOptionPane.showMessageDialog(MainApplication.getMainFrame(), "Offset Line
        // does not cross Polygon");
        // return;
        // }

        // TODO check if inner is closest to outer

        List<Way> outerCrossingWays = getRelationIntersectedMemberWayWithRole(relation, intersectedWaysWithOffsetLine,
                "outer");
        List<Way> innerCrossingWays = getRelationIntersectedMemberWayWithRole(relation, intersectedWaysWithOffsetLine,
                "inner");

        List<JoinedPolygon> outerRings = getParentRingsForWays(relation, outerCrossingWays);
        List<JoinedPolygon> innerRings = getParentRingsForWays(relation, innerCrossingWays);

        if (outerRings.size() == 1 && innerRings.size() == 0) {
            // Simple Split
            Logging.info("Performing simple cut");
            performSimpleCut(relation, offsetWay);

        } else if (outerRings.size() == 1 && innerRings.size() == 1) {
            // Join Outer with Inner
            Logging.info("Join Outer Ring with Inner Ring");
            ringOneCrossingWay = getMemberOfRing(outerRings.get(0), outerCrossingWays.get(0));
            ringTwoCrossingWay = getMemberOfRing(innerRings.get(0), innerCrossingWays.get(0));

        } else if (outerRings.size() == 0 && innerRings.size() == 2) {
            // Join 2 Inner
            Logging.info("Join 2 Inner Rings");
            ringOneCrossingWay = getMemberOfRing(innerRings.get(0), innerCrossingWays.get(0));
            ringTwoCrossingWay = getMemberOfRing(innerRings.get(1), innerCrossingWays.get(1));
        } else {
            // error
            Logging.info("Error finding proper Rings");
        }

        /// 2 check start/end point ouside outer polygon
        // NOTE Nodes in Inner rings also considered outside
        // boolean firstOutside = checkNodeOutsideArea(offsetWay.firstNode(),
        /// outerRings.get(0).area);

        // ringOneCrossingWays.add(ringOneCrossingWay);
        if (ringTwoCrossingWay != null) {
            ringTwoCrossingWays.add(ringTwoCrossingWay);
        }

        // 3 if not, check if line crosses polygon fully, proceed with standard to find
        // next way for offset if not fully crossed
        // if crosses, needs offset line?, get nodes of way, check until first will be
        // inside inner
        if (ringTwoCrossingWay != null) {

            // Create temporary multipolygon with actual Data
            Collection<Way> allRelationWays = new ArrayList<>(relation.getMemberPrimitives(Way.class));
            MultipolygonBuilder multipolygon = new MultipolygonBuilder();
            String error = multipolygon.makeFromWays(allRelationWays);

            if (error != null) {
                JOptionPane.showMessageDialog(MainApplication.getMainFrame(), error);
                return;
            }

            // Create intersection Nodes for later split
            List<Way> waysForIntersections = new ArrayList<>();
            waysForIntersections.add(ringOneCrossingWay);
            waysForIntersections.add(ringTwoCrossingWay);
            waysForIntersections.add(offsetWay);

            Set<Node> intersectionNodesSet = createIntersection(waysForIntersections);
            intersectionNodes.addAll(intersectionNodesSet);

            // Split way using Intersection nodes
            Logging.info("Split way using Intersection nodes");

            List<List<Node>> wayChunks = SplitWayCommand.buildSplitChunks(offsetWay,
                    intersectionNodes);
            Logging.info("Waychunks count: " + wayChunks.size());

            SplitWayCommand splitWayCommand = SplitWayCommand.splitWay(offsetWay,
                    wayChunks, Collections.emptyList());
            List<Command> splitWayCommands = new LinkedList<>();
            splitWayCommands.add(splitWayCommand);

            if (!splitWayCommands.isEmpty()) {
                UndoRedoHandler.getInstance().add(new SequenceCommand(tr("Split way"),
                        splitWayCommands));

                getLayerManager().getEditDataSet().setSelected(splitWayCommand.getNewSelection());

            }

            // delete unneeded ways
            Collection<OsmPrimitive> results = getLayerManager().getEditDataSet().getSelected();
            Collection<OsmPrimitive> waysToDelete = new ArrayList<>();

            for (OsmPrimitive element : results) {
                if (element instanceof Way) {
                    Way wayElement = (Way) element;
                    if (intersectionNodes.contains(wayElement.firstNode())
                            && intersectionNodes.contains(wayElement.lastNode())) {
                        createdWays.add(wayElement);
                    } else {
                        waysToDelete.add(wayElement);
                    }
                }
            }

            if (!waysToDelete.isEmpty()) {
                Command cmd = DeleteCommand.delete(waysToDelete, true, false);
                UndoRedoHandler.getInstance().add(cmd);
            }
            Logging.info("Deleted unused Waychunks after split");

        }

    }

    List<JoinedPolygon> buildMultipolygonAndGetRings(Relation relation) {

        List<JoinedPolygon> checkRings = new ArrayList<>();
        Collection<Way> relationMemberWays = new ArrayList<>(relation.getMemberPrimitives(Way.class));
        MultipolygonBuilder multipolygon = new MultipolygonBuilder();
        String error = multipolygon.makeFromWays(relationMemberWays);

        if (error != null) {
            JOptionPane.showMessageDialog(MainApplication.getMainFrame(), error);
            return null;
        }

        checkRings.addAll(multipolygon.outerWays);
        checkRings.addAll(multipolygon.innerWays);

        return checkRings;
    }

    List<Way> splitRingsAndGetWaysForDeletionV2(Relation relation) {

        List<Way> ringDeletionWays = new ArrayList<>();

        // reconstruct multipolygon to fetch added Split nodes positions
        List<JoinedPolygon> checkRings = buildMultipolygonAndGetRings(relation);

        List<Node> ring1IntersectionNodes = null;
        List<Node> ring2IntersectionNodes = null;

        // Find Rings containing intersection Nodes and split them
        Logging.info("Find Rings containing intersection Nodes and split them");
        for (JoinedPolygon ring : checkRings) {

            List<Node> ringIntersectionNodes = new ArrayList<>();
            List<Way> splitWays = new ArrayList<>();

            for (Way ringWay : ring.ways) {
                for (Node ringNode : ringWay.getNodes()) {
                    if (intersectionNodes.contains(ringNode)) {
                        ringIntersectionNodes.add(ringNode);
                        if (!splitWays.contains(ringWay)) {
                            splitWays.add(ringWay);
                        }
                    }
                }
            }

            // split
            Logging.info("Split Ring to get 'hole'");
            if (ringIntersectionNodes.size() == 2 && splitWays.size() == 1) {

                // simple split
                List<List<Node>> wayChunks = SplitWayCommand.buildSplitChunks(splitWays.get(0),
                        ringIntersectionNodes);

                SplitWayCommand result = SplitWayCommand.splitWay(splitWays.get(0),
                        wayChunks, Collections.emptyList());
                List<Command> splitRingCommands = new LinkedList<>();
                splitRingCommands.add(result);

                if (!splitRingCommands.isEmpty()) {
                    UndoRedoHandler.getInstance().add(new SequenceCommand(tr("Split Ring way"),
                            splitRingCommands));
                    getLayerManager().getEditDataSet().setSelected(result.getNewSelection());
                }

                if (ring1IntersectionNodes == null) {
                    ring1IntersectionNodes = ringIntersectionNodes;
                } else {
                    ring2IntersectionNodes = ringIntersectionNodes;
                }

            }

        }

        Logging.info("Rebuild Polygon to fetch actual Ways after splitting");
        // reconstruct multipolygon to find Ways for deletion after Split
        checkRings = buildMultipolygonAndGetRings(relation);

        Logging.info("Getting hole for Ring 1");
        ringDeletionWays.addAll(getRingWaysForDeletion(checkRings, ring1IntersectionNodes));
        // TODO FIX ringIntersectionNodes2 null
        Logging.info("Getting hole for Ring 2");
        ringDeletionWays.addAll(getRingWaysForDeletion(checkRings, ring2IntersectionNodes));

        return ringDeletionWays;
    }

    List<Way> getRingWaysForDeletion(List<JoinedPolygon> checkRings, List<Node> ringIntersectionNodes) {
        List<Way> ringDeletionWays = new ArrayList<>();
        JoinedPolygon foundRing = null;

        for (JoinedPolygon checkRing : checkRings) {

            if (checkRing.nodes.contains(ringIntersectionNodes.get(0))) {
                foundRing = checkRing;
                break;
            }
        }

        if (foundRing == null)
            return null;

        // NOTE There was issue calculating length, seems to be fixed
        Pair<double[], Way[][]> checkedDistances = calculateDistancesBetweenWaysInClosedWay(foundRing.ways,
                ringIntersectionNodes.get(0), ringIntersectionNodes.get(1));

        if (checkedDistances.a[0] < checkedDistances.a[1]) {
            ringDeletionWays = Arrays.asList(checkedDistances.b[0]);
        } else {
            ringDeletionWays = Arrays.asList(checkedDistances.b[1]);
        }

        return ringDeletionWays;

    }

    public Pair<double[], Way[][]> calculateDistancesBetweenWaysInClosedWay(List<Way> ways, Node node1, Node node2) {
        Way[][] waysArray = new Way[2][];
        double[] distances = new double[2];

        int index1 = -1;
        int index2 = -1;
        for (int i = 0; i < ways.size(); i++) {
            Way way = ways.get(i);
            if (way.firstNode() == node1) {
                index1 = i;
            }
            if (way.firstNode() == node2) {
                index2 = i;
            }
        }

        // Iterate over the ways in the Ring in both directions, calculating
        // distances until reaching the target nodes
        for (int direction = 0; direction < 2; direction++) {
            int currentIndex = direction == 0 ? index1 : index2;
            int targetIndex = direction == 0 ? index2 : index1;
            double totalDistance = 0.0;
            List<Way> waysTraversed = new ArrayList<>();

            // Traverse the closed way in the specified direction
            while (currentIndex != targetIndex) {
                Way currentWay = ways.get(currentIndex);
                waysTraversed.add(currentWay); // Collect the current way

                // Fix???
                if (currentWay.lastNode() == node1 || currentWay.lastNode() == node2) {
                    currentIndex = targetIndex;
                } else {
                    currentIndex = (currentIndex + 1) % ways.size();
                }

            }

            // Add the last way visited to waysTraversed
            // waysTraversed.add(ways.get(targetIndex));

            // Calculate the total distance traversed (e.g., sum of lengths of all ways)
            for (Way way : waysTraversed) {
                totalDistance += way.getLength();
            }

            // Store the total distance for the current direction
            distances[direction] = totalDistance;
            waysArray[direction] = waysTraversed.toArray(new Way[0]); // Convert the list of ways traversed to an array
        }

        return new Pair<>(distances, waysArray);
    }

    private Set<Node> createIntersection(List<Way> ways) {

        LinkedList<Command> createIntersectionNodesCommands = new LinkedList<>();
        Set<Node> Nodes = Geometry.addIntersections(ways, false, createIntersectionNodesCommands);
        Logging.info("Created nodes count " + Nodes.size());

        if (!createIntersectionNodesCommands.isEmpty()) {
            UndoRedoHandler.getInstance()
                    .add(new SequenceCommand(tr("Add nodes at intersections"), createIntersectionNodesCommands));
            Set<Node> nodes = new HashSet<>(10);
            // find and select newly added nodes
            for (Command command : createIntersectionNodesCommands)
                if (command instanceof AddCommand) {
                    Collection<? extends OsmPrimitive> pp = command.getParticipatingPrimitives();
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

    // private Command createAddWayCommand(Way newWay) {

    // List<Command> commands = new LinkedList<>();
    // List<Node> sourceNodes = newWay.getNodes();

    // for (Node originalNode : sourceNodes) {
    // commands.add(new AddCommand(this.ds, originalNode));
    // }

    // commands.add(new AddCommand(ds, newWay));

    // return new SequenceCommand(tr("Add way"), commands);

    // }

    private List<Way> getRelationIntersectedMemberWayWithRole(Relation relation, Set<Way> intersectedWays,
            String role) {

        List<Way> foundMembers = new ArrayList<>();

        List<OsmPrimitive> allMembersWithRole = new ArrayList<>(relation.findRelationMembers(role));

        for (OsmPrimitive primitive : allMembersWithRole) {
            if (primitive instanceof Way) {
                Way wayElement = (Way) primitive;

                if (intersectedWays.contains(wayElement)) {
                    foundMembers.add(wayElement);

                }
            }
        }

        return foundMembers;

    }

    public Relation getCrossingRelation(List<Way> offsetWays) {

        Set<Way> newIntersectedWays = new HashSet<>();
        // Check ways that intersects with selected Way
        NodeWayUtils.addWaysIntersectingWays(getLayerManager().getActiveDataSet().getWays(), offsetWays,
                newIntersectedWays);

        // Check if intersected Way is part of Relation
        Set<Relation> parentRelations = OsmPrimitive.getParentRelations(newIntersectedWays);

        for (Relation relation : parentRelations) {
            // check if complete
            if (relation.getIncompleteMembers().size() == 0 && "multipolygon".equals(relation.get("type"))) {
                return relation;
            }
        }
        return null;
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        updateModifiersState(e.getModifiersEx());
        // Other buttons are off limit, but we still get events.
        if (e.getButton() != MouseEvent.BUTTON1)
            return;

        if (!mouseHasBeenDragged) {
            // use point from press or click event? (or are these always the same)
            Way nearestWay = mv.getNearestWay(e.getPoint(), OsmPrimitive::isSelectable);
            if (nearestWay == null) {
                if (matchesCurrentModifiers(SET_SELECTED_MODIFIER_COMBO)) {
                    clearSourceWays();
                }
                resetMouseTrackingState();
                return;
            }
            boolean isSelected = nearestWay.isSelected();
            if (matchesCurrentModifiers(ADD_TO_SELECTION_MODIFIER_COMBO)) {
                if (!isSelected) {
                    addSourceWay(nearestWay);
                }
            } else if (matchesCurrentModifiers(TOGGLE_SELECTED_MODIFIER_COMBO)) {
                if (isSelected) {
                    removeSourceWay(nearestWay);
                } else {
                    addSourceWay(nearestWay);
                }
            } else if (matchesCurrentModifiers(SET_SELECTED_MODIFIER_COMBO)) {
                clearSourceWays();
                addSourceWay(nearestWay);
            } // else -> invalid modifier combination
        } else if (mode == Mode.DRAGGING) {
            // clearSourceWays();
            MainApplication.getMap().statusLine.setDist(pWays.getWays());
        }

        Logging.info("Cut started");
        performCut();

        setMode(Mode.NORMAL);
        resetMouseTrackingState();
        temporaryLayer.invalidate();
    }

    private static void removeWayHighlighting(Collection<Way> ways) {
        if (ways == null)
            return;
        for (Way w : ways) {
            w.setHighlighted(false);
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        // WTF... the event passed here doesn't have button info?
        // Since we get this event from other buttons too, we must check that
        // _BUTTON1_ is down.
        if (!mouseIsDown)
            return;

        boolean modifiersChanged = updateModifiersState(e.getModifiersEx());
        updateFlagsChangeableAlways();

        if (modifiersChanged) {
            // Since this could be remotely slow, do it conditionally
            updateStatusLine();
            updateCursor();
        }

        if ((System.currentTimeMillis() - mousePressedTime) < INITIAL_MOVE_DELAY.get())
            return;
        // Assuming this event only is emitted when the mouse has moved
        // Setting this after the check above means we tolerate clicks with some
        // movement
        mouseHasBeenDragged = true;

        if (mode == Mode.NORMAL) {
            // Should we ensure that the copyTags modifiers are still valid?

            // Important to use mouse position from the press, since the drag
            // event can come quite late
            if (!isModifiersValidForDragMode())
                return;
            if (!initParallelWays(mousePressedPos, copyTags))
                return;
            setMode(Mode.DRAGGING);
        }

        // Calculate distance to the reference line
        Point p = e.getPoint();
        EastNorth enp = mv.getEastNorth((int) p.getX(), (int) p.getY());
        EastNorth nearestPointOnRefLine = Geometry.closestPointToLine(referenceSegment.getFirstNode().getEastNorth(),
                referenceSegment.getSecondNode().getEastNorth(), enp);

        // Note: d is the distance in _projected units_
        double d = enp.distance(nearestPointOnRefLine);
        double realD = mv.getProjection().eastNorth2latlon(enp).greatCircleDistance(
                (ILatLon) mv.getProjection().eastNorth2latlon(nearestPointOnRefLine));
        double snappedRealD = realD;

        boolean toTheRight = Geometry.angleIsClockwise(
                referenceSegment.getFirstNode(), referenceSegment.getSecondNode(), new Node(enp));

        if (snap) {
            // TODO: Very simple snapping
            // - Snap steps relative to the distance?
            double snapDistance;
            SystemOfMeasurement som = SystemOfMeasurement.getSystemOfMeasurement();
            if (som.equals(SystemOfMeasurement.CHINESE)) {
                snapDistance = SNAP_DISTANCE_CHINESE.get() * SystemOfMeasurement.CHINESE.aValue;
            } else if (som.equals(SystemOfMeasurement.IMPERIAL)) {
                snapDistance = SNAP_DISTANCE_IMPERIAL.get() * SystemOfMeasurement.IMPERIAL.aValue;
            } else if (som.equals(SystemOfMeasurement.NAUTICAL_MILE)) {
                snapDistance = SNAP_DISTANCE_NAUTICAL.get() * SystemOfMeasurement.NAUTICAL_MILE.aValue;
            } else {
                snapDistance = SNAP_DISTANCE_METRIC.get(); // Metric system by default
            }
            double closestWholeUnit;
            double modulo = realD % snapDistance;
            if (modulo < snapDistance / 2.0) {
                closestWholeUnit = realD - modulo;
            } else {
                closestWholeUnit = realD + (snapDistance - modulo);
            }
            if (Math.abs(closestWholeUnit - realD) < (SNAP_THRESHOLD.get() * snapDistance)) {
                snappedRealD = closestWholeUnit;
            } else {
                snappedRealD = closestWholeUnit + Math.signum(realD - closestWholeUnit) * snapDistance;
            }
        }
        d = snappedRealD * (d / realD); // convert back to projected distance. (probably ok on small scales)
        helperLineStart = nearestPointOnRefLine;
        helperLineEnd = enp;
        if (toTheRight) {
            d = -d;
        }
        // TODO draw second line
        pWays.changeOffset(d);

        pWaysMirrored.changeOffset(-d);

        MapFrame map = MainApplication.getMap();
        map.statusLine.setDist(Math.abs(snappedRealD));
        map.statusLine.repaint();
        temporaryLayer.invalidate();
    }

    private boolean matchesCurrentModifiers(CachingProperty<Map<Modifier, Boolean>> spec) {
        return matchesCurrentModifiers(spec.get());
    }

    private boolean matchesCurrentModifiers(Map<Modifier, Boolean> spec) {
        EnumSet<Modifier> modifiers = EnumSet.noneOf(Modifier.class);
        if (ctrl) {
            modifiers.add(Modifier.CTRL);
        }
        if (alt) {
            modifiers.add(Modifier.ALT);
        }
        if (shift) {
            modifiers.add(Modifier.SHIFT);
        }
        return spec.entrySet().stream().allMatch(entry -> modifiers.contains(entry.getKey()) == entry.getValue());
    }

    private boolean isModifiersValidForDragMode() {
        return (!alt && !shift && !ctrl) || matchesCurrentModifiers(SNAP_MODIFIER_COMBO)
                || matchesCurrentModifiers(COPY_TAGS_MODIFIER_COMBO);
    }

    private void updateFlagsOnlyChangeableOnPress() {
        copyTags = COPY_TAGS_DEFAULT.get() != matchesCurrentModifiers(COPY_TAGS_MODIFIER_COMBO);
    }

    private void updateFlagsChangeableAlways() {
        snap = SNAP_DEFAULT.get() != matchesCurrentModifiers(SNAP_MODIFIER_COMBO);
    }

    // We keep the source ways and the selection in sync so the user can see the
    // source way's tags
    private void addSourceWay(Way w) {
        assert sourceWays != null;
        getLayerManager().getEditDataSet().addSelected(w);
        w.setHighlighted(true);
        sourceWays.add(w);
    }

    private void removeSourceWay(Way w) {
        assert sourceWays != null;
        getLayerManager().getEditDataSet().clearSelection(w);
        w.setHighlighted(false);
        sourceWays.remove(w);
    }

    private void clearSourceWays() {
        assert sourceWays != null;
        getLayerManager().getEditDataSet().clearSelection(sourceWays);
        for (Way w : sourceWays) {
            w.setHighlighted(false);
        }
        sourceWays.clear();
    }

    private void resetMouseTrackingState() {
        mouseIsDown = false;
        mousePressedPos = null;
        mouseHasBeenDragged = false;
    }

    // TODO: rename
    private boolean initParallelWays(Point p, boolean copyTags) {

        if (cutPathId == 0) {
            return false;
        }
        Predicate<OsmPrimitive> idPredicate = primitive -> primitive.getUniqueId() == cutPathId;

        referenceSegment = mv.getNearestWaySegment(p, idPredicate, true);
        if (referenceSegment == null)
            return false;

        sourceWays.removeIf(w -> w.isIncomplete() || w.isEmpty());

        if (!sourceWays.contains(referenceSegment.getWay())) {
            clearSourceWays();
            addSourceWay(referenceSegment.getWay());
        }

        try {
            int referenceWayIndex = -1;
            int i = 0;
            for (Way w : sourceWays) {
                if (w == referenceSegment.getWay()) {
                    referenceWayIndex = i;
                    break;
                }
                i++;
            }
            pWays = new ParallelWays(sourceWays, copyTags, referenceWayIndex);
            pWays.commit();
            pWaysMirrored = new ParallelWays(sourceWays, copyTags, referenceWayIndex);
            pWaysMirrored.commit();
            getLayerManager().getEditDataSet().setSelected(pWays.getWays());
            return true;
        } catch (IllegalArgumentException e) {
            Logging.debug(e);
            new Notification(tr("MagicCutAction\n" +
                    "The ways selected must form a simple branchless path"))
                    .setIcon(JOptionPane.INFORMATION_MESSAGE)
                    .show();
            // The error dialog prevents us from getting the mouseReleased event
            resetMouseTrackingState();
            pWays = null;
            pWaysMirrored = null;
            return false;
        }
    }

    private static String prefKey(String subKey) {
        return "edit.make-parallel-way-action." + subKey;
    }

    /**
     * A property that holds the keyboard modifiers.
     * 
     * @author Michael Zangl
     * @since 10869
     */
    private static class KeyboardModifiersProperty extends AbstractToStringProperty<Map<Modifier, Boolean>> {

        KeyboardModifiersProperty(String key, String defaultValue) {
            this(key, createFromString(defaultValue));
        }

        KeyboardModifiersProperty(String key, Map<Modifier, Boolean> defaultValue) {
            super(key, defaultValue);
        }

        @Override
        protected String toString(Map<Modifier, Boolean> t) {
            StringBuilder sb = new StringBuilder();
            for (Modifier mod : Modifier.values()) {
                Boolean val = t.get(mod);
                if (val == null) {
                    sb.append('?');
                } else if (val) {
                    sb.append(Character.toUpperCase(mod.shortChar));
                } else {
                    sb.append(mod.shortChar);
                }
            }
            return sb.toString();
        }

        @Override
        protected Map<Modifier, Boolean> fromString(String string) {
            return createFromString(string);
        }

        private static Map<Modifier, Boolean> createFromString(String string) {
            Map<Modifier, Boolean> ret = new EnumMap<>(Modifier.class);
            for (int i = 0; i < string.length(); i++) {
                char c = string.charAt(i);
                if (c == '?') {
                    continue;
                }
                Optional<Modifier> mod = Modifier.findWithShortCode(c);
                if (mod.isPresent()) {
                    ret.put(mod.get(), Character.isUpperCase(c));
                } else {
                    Logging.debug("Ignoring unknown modifier {0}", c);
                }
            }
            return Collections.unmodifiableMap(ret);
        }
    }

    enum Modifier {
        CTRL('c'),
        ALT('a'),
        SHIFT('s');

        private final char shortChar;

        Modifier(char shortChar) {
            this.shortChar = Character.toLowerCase(shortChar);
        }

        /**
         * Find the modifier with the given short code
         * 
         * @param charCode The short code
         * @return The modifier
         */
        public static Optional<Modifier> findWithShortCode(int charCode) {
            return Stream.of(values()).filter(m -> m.shortChar == Character.toLowerCase(charCode)).findAny();
        }
    }

    private class ParallelWayLayer extends AbstractMapViewPaintable {
        @Override
        public void paint(Graphics2D g, MapView mv, Bounds bbox) {
            if (mode == Mode.DRAGGING) {
                CheckParameterUtil.ensureParameterNotNull(mv, "mv");

                Color mainColor = MAIN_COLOR.get();
                g.setStroke(REF_LINE_STROKE.get());
                g.setColor(mainColor);
                MapViewPath line = new MapViewPath(mv);
                line.moveTo(referenceSegment.getFirstNode());
                line.lineTo(referenceSegment.getSecondNode());
                g.draw(line.computeClippedLine(g.getStroke()));

                g.setStroke(HELPER_LINE_STROKE.get());
                g.setColor(mainColor);
                line = new MapViewPath(mv);
                line.moveTo(helperLineStart);
                line.lineTo(helperLineEnd);
                g.draw(line.computeClippedLine(g.getStroke()));
            }
        }
    }
}
