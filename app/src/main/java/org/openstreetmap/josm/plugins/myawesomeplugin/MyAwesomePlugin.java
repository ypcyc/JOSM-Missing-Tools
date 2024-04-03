package org.openstreetmap.josm.plugins.myawesomeplugin;

import javax.swing.JMenu;

import org.openstreetmap.josm.gui.IconToggleButton;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MainMenu;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;
import org.openstreetmap.josm.plugins.myawesomeplugin.actions.MagicCutAction;
import org.openstreetmap.josm.plugins.myawesomeplugin.actions.MagicCutterAction;
import org.openstreetmap.josm.plugins.myawesomeplugin.actions.MagicUnlink;

public class MyAwesomePlugin extends Plugin {
    /**
     * Will be invoked by JOSM to bootstrap the plugin
     *
     * @param info information about the plugin and its local installation
     */
    public MyAwesomePlugin(PluginInformation info) {
        super(info);

        // JMenu editMenu = MainApplication.getMenu().editMenu;
        JMenu toolsMenu = MainApplication.getMenu().moreToolsMenu;

        MainMenu.add(toolsMenu, new MagicUnlink());
        MainMenu.add(toolsMenu, new MagicCutterAction());

    }

    /**
     * Called when the JOSM map frame is created or destroyed.
     */
    @Override
    public void mapFrameInitialized(MapFrame oldFrame, MapFrame newFrame) {
        if (oldFrame == null && newFrame != null) { // map frame added
            MapFrame map = MainApplication.getMap();
            map.addMapMode(new IconToggleButton(new MagicCutAction(map)));
        }
    }
}