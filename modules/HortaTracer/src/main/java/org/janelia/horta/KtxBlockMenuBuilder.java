package org.janelia.horta;

import java.awt.event.ActionEvent;
import java.io.IOException;
import javax.swing.AbstractAction;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import org.janelia.horta.actions.LoadHortaTileAtFocusAction;
import org.janelia.horta.actors.OmeZarrVolumeActor;
import org.janelia.horta.actors.TetVolumeActor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Create popup menus related to Ktx volume tile blocks.
 * @author brunsc
 */
class KtxBlockMenuBuilder {
    private boolean preferKtx = false;

    private boolean preferOmeZarr = true;

    private boolean preferStaticTiles = true;

    private JCheckBoxMenuItem enableVolumeCacheMenu;

    private JCheckBoxMenuItem enableOmeZarrCacheMenu;

    private JCheckBoxMenuItem enableStaticTileCacheMenu;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    boolean isPreferKtx() {
        return preferKtx;
    }

    void setPreferKtx(boolean doPreferKtx) {
        preferKtx = doPreferKtx;
    }

    boolean isPreferOmeZarr() {
        return preferOmeZarr;
    }

    void setPreferOmeZarr(boolean doPreferOmeZarr) {
        preferOmeZarr = doPreferOmeZarr;
    }

    void populateMenus(final HortaMenuContext context) {
        JMenu tilesMenu = new JMenu("Tiles");
        context.topMenu.add(tilesMenu);

         tilesMenu.add(new AbstractAction("Load KTX Tile At Cursor") {
            @Override
            public void actionPerformed(ActionEvent e) {
                logger.info("Load Horta Cursor Tile Action invoked");
                NeuronTracerTopComponent nttc = NeuronTracerTopComponent.getInstance();
                if (nttc == null)
                    return;
                try {
                    nttc.setPreferKtx(true);
                    nttc.loadPersistentTileAtLocation(context.mouseXyz);
                } catch (IOException ex) {
                    logger.info("Tile load failed");
                }
            }
        });

       tilesMenu.add(new AbstractAction("Load KTX Tile At Focus") {
            @Override
            public void actionPerformed(ActionEvent e) {
                NeuronTracerTopComponent nttc = NeuronTracerTopComponent.getInstance();
                if (nttc == null)
                    return;
                new LoadHortaTileAtFocusAction(nttc).actionPerformed(e);
            }
        });

        tilesMenu.add(new JPopupMenu.Separator());

        /* */
        enableVolumeCacheMenu = new JCheckBoxMenuItem("Prefer rendered Ktx tiles", preferKtx);
        tilesMenu.add(enableVolumeCacheMenu);
        enableVolumeCacheMenu.addActionListener(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                JCheckBoxMenuItem item = (JCheckBoxMenuItem)e.getSource();
                preferKtx = item.isSelected();
                if (preferKtx) {
                    preferOmeZarr = false;
                    enableOmeZarrCacheMenu.setSelected(false);
                    preferStaticTiles = false;
                    enableStaticTileCacheMenu.setSelected(false);
                }
                NeuronTracerTopComponent nttc = NeuronTracerTopComponent.getInstance();
                item.setSelected(preferKtx);
                nttc.reloadSampleLocation();
            }
        });

        enableOmeZarrCacheMenu = new JCheckBoxMenuItem("Prefer rendered Ome Zarr tiles", preferOmeZarr);
        tilesMenu.add(enableOmeZarrCacheMenu);
        enableOmeZarrCacheMenu.addActionListener(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                JCheckBoxMenuItem item = (JCheckBoxMenuItem)e.getSource();
                preferOmeZarr = item.isSelected();
                if (preferOmeZarr) {
                    preferKtx = false;
                    enableVolumeCacheMenu.setSelected(false);
                    preferStaticTiles = false;
                    enableStaticTileCacheMenu.setSelected(false);
                }
                NeuronTracerTopComponent nttc = NeuronTracerTopComponent.getInstance();
                item.setSelected(preferOmeZarr);
                nttc.reloadSampleLocation();
            }
        });

        enableStaticTileCacheMenu = new JCheckBoxMenuItem("Prefer raw tiles", preferStaticTiles);
        tilesMenu.add(enableStaticTileCacheMenu);
        enableStaticTileCacheMenu.addActionListener(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                JCheckBoxMenuItem item = (JCheckBoxMenuItem)e.getSource();
                preferStaticTiles = item.isSelected();
                if (preferStaticTiles) {
                    preferKtx = false;
                    enableVolumeCacheMenu.setSelected(false);
                    preferOmeZarr = false;
                    enableOmeZarrCacheMenu.setSelected(false);
                }
                NeuronTracerTopComponent nttc = NeuronTracerTopComponent.getInstance();
                item.setSelected(preferStaticTiles);
                nttc.reloadSampleLocation();
            }
        });
        /* */

        tilesMenu.add(new JMenuItem(
                new AbstractAction("Clear all Volume Blocks")
        {
            @Override
            public void actionPerformed(ActionEvent e) {
                TetVolumeActor.getInstance().clearAllBlocks();
                OmeZarrVolumeActor.getInstance().clearAllBlocks();
                NeuronTracerTopComponent nttc = NeuronTracerTopComponent.getInstance();
                TetVolumeActor.getInstance().clearAllBlocks();
                nttc.getNeuronMPRenderer().clearVolumeActors();
                nttc.clearAllTiles();
            }
        }));

    }

}
