package org.openstreetmap.josm.plugins.myawesomeplugin;

import javax.swing.JMenu;

import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MainMenu;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;
import org.openstreetmap.josm.plugins.myawesomeplugin.actions.MagicSelectAction;
import org.openstreetmap.josm.plugins.myawesomeplugin.actions.RunMagic;

public class MyAwesomePlugin extends Plugin {
    /**
     * Will be invoked by JOSM to bootstrap the plugin
     *
     * @param info  information about the plugin and its local installation    
     */
     public MyAwesomePlugin(PluginInformation info) {
        super(info);

        //JMenu editMenu = MainApplication.getMenu().editMenu;
        JMenu toolsMenu = MainApplication.getMenu().moreToolsMenu;

        MainMenu.add(toolsMenu, new RunMagic());
        MainMenu.add(toolsMenu, new MagicSelectAction());
     }

     /* ... */
 }