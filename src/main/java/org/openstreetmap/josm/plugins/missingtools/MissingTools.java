package org.openstreetmap.josm.plugins.missingtools;

import javax.swing.JMenu;

import org.openstreetmap.josm.gui.IconToggleButton;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MainMenu;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;
import org.openstreetmap.josm.plugins.missingtools.actions.CutPolygonAction;
import org.openstreetmap.josm.plugins.missingtools.actions.UnlinkPolygonAction;

public class MissingTools extends Plugin {
    /**
     * Will be invoked by JOSM to bootstrap the plugin
     *
     * @param info information about the plugin and its local installation
     */
    public MissingTools(PluginInformation info) {
        super(info);

        // JMenu editMenu = MainApplication.getMenu().editMenu;
        JMenu toolsMenu = MainApplication.getMenu().moreToolsMenu;

        MainMenu.add(toolsMenu, new UnlinkPolygonAction());

    }

    /**
     * Called when the JOSM map frame is created or destroyed.
     */
    @Override
    public void mapFrameInitialized(MapFrame oldFrame, MapFrame newFrame) {
        if (oldFrame == null && newFrame != null) { // map frame added
            MapFrame map = MainApplication.getMap();
            map.addMapMode(new IconToggleButton(new CutPolygonAction(map)));

            // map.addMapMode(new IconToggleButton(new UnlinkPolygonAction(map)));
        }
    }
}