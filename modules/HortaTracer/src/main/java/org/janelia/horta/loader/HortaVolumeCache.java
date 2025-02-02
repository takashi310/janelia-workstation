package org.janelia.horta.loader;

import org.janelia.workstation.controller.model.color.ImageColorModel;
import org.janelia.geometry3d.PerspectiveCamera;
import org.janelia.geometry3d.Vector3;
import org.janelia.horta.volume.VolumeMipMaterial;
import org.janelia.gltools.texture.Texture3d;
import org.janelia.horta.BrainTileInfo;
import org.janelia.horta.NeuronTraceLoader;
import org.janelia.horta.options.TileLoadingPanel;
import org.janelia.horta.volume.BrickActor;
import org.janelia.horta.volume.BrickInfo;
import org.janelia.horta.volume.BrickInfoSet;
import org.janelia.horta.volume.StaticVolumeBrickSource;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.openide.util.Exceptions;
import org.openide.util.NbPreferences;
import org.openide.util.RequestProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;

/**
 * Keeps in memory a configurable number of nearby volume tiles.
 * Manages transfer of volume tileimagery:
 *   A) from disk/network, 
 *   B) to RAM, 
 *   C) and thence to GPU video memory
 * 
 * TODO: Trim tiles to non-overlapping subvolumes before processing.
 * 
 * @author brunsc
 */
public class HortaVolumeCache {
    private static final Logger LOG = LoggerFactory.getLogger(HortaVolumeCache.class);

    private int ramTileCount = 4; // Three is better than two for tile availability
    private int gpuTileCount = 1;
    private final PerspectiveCamera camera;
    private StaticVolumeBrickSource source = null;

    // Lightweight metadata
    private final Collection<BrickInfo> nearVolumeMetadata = new ConcurrentSkipListSet<>((t1, t2) -> {
        // dummmy comparator that simply looks at the X coordinate of the centroid
        Vector3 c1 = t1.getBoundingBox().getCentroid();
        Vector3 c2 = t2.getBoundingBox().getCentroid();
        if (c1.getX() < c2.getX()) {
            return -1;
        } else if (c1.getX() > c2.getX()) {
            return 1;
        } else {
            return 0;
        }
    });

    // Large in-memory cache
    private final Map<BrickInfo, Texture3d> nearVolumeInRam = new ConcurrentHashMap<>();

    private final Map<BrickInfo, RequestProcessor.Task> queuedTiles = new ConcurrentHashMap<>();
    private final Map<BrickInfo, RequestProcessor.Task> loadingTiles = new ConcurrentHashMap<>();

    // Fewer on GPU cache
    private final Map<BrickInfo, BrickActor> actualDisplayTiles = new ConcurrentHashMap<>();
    private final Collection<BrickInfo> desiredDisplayTiles = new ConcurrentSkipListSet<>((t1, t2) -> {
        // dummmy comparator that simply looks at the X coordinate of the centroid
        Vector3 c1 = t1.getBoundingBox().getCentroid();
        Vector3 c2 = t2.getBoundingBox().getCentroid();
        if (c1.getX() < c2.getX()) {
            return -1;
        } else if (c1.getX() > c2.getX()) {
            return 1;
        } else {
            return 0;
        }
    });
    
    // To enable/disable loading
    private boolean doUpdateCache = true;
    
    // Cache camera data for early termination
    private float cachedFocusX = Float.NaN;
    private float cachedFocusY = Float.NaN;
    private float cachedFocusZ = Float.NaN;
    private float cachedZoom = Float.NaN;

    private RequestProcessor loadProcessor;
    private final ImageColorModel imageColorModel;
    private final VolumeMipMaterial.VolumeState volumeState;
    private final Collection<TileDisplayObserver> observers = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private int currentColorChannel;

    public HortaVolumeCache(final PerspectiveCamera camera, 
                            final ImageColorModel imageColorModel,
                            final VolumeMipMaterial.VolumeState volumeState,
                            int currentColorChannel)
    {
        this.imageColorModel = imageColorModel;
        this.volumeState = volumeState;
        this.currentColorChannel = currentColorChannel;

        this.camera = camera;
        camera.addObserver(new CameraObserver());

        Preferences pref = NbPreferences.forModule(TileLoadingPanel.class);

        String concurrentLoadsStr = pref.get(TileLoadingPanel.PREFERENCE_CONCURRENT_LOADS, TileLoadingPanel.PREFERENCE_CONCURRENT_LOADS_DEFAULT);
        setConcurrentLoads(concurrentLoadsStr);

        String ramTileCountStr = pref.get(TileLoadingPanel.PREFERENCE_RAM_TILE_COUNT, TileLoadingPanel.PREFERENCE_RAM_TILE_COUNT_DEFAULT);
        setRamTileCount(ramTileCountStr);

        pref.addPreferenceChangeListener(new PreferenceChangeListener() {
            public void preferenceChange(PreferenceChangeEvent evt) {
                if (evt.getKey().equals(TileLoadingPanel.PREFERENCE_CONCURRENT_LOADS)) {
                    setConcurrentLoads(evt.getNewValue());
                }
                else if (evt.getKey().equals(TileLoadingPanel.PREFERENCE_RAM_TILE_COUNT)) {
                    setRamTileCount(evt.getNewValue());
                }
            }
        });
    }

    private void setConcurrentLoads(String preferenceValue) {
        int loadThreads = Integer.parseInt(preferenceValue);
        LOG.info("Configuring loadThreads={}", loadThreads);
        if (loadProcessor!=null) {
            loadProcessor.shutdown();
        }
        loadProcessor = new RequestProcessor("VolumeTileLoad", loadThreads, true);
    }

    private void setRamTileCount(String preferenceValue) {
        this.ramTileCount = Integer.parseInt(preferenceValue);
        LOG.info("Configuring ramTileCount={}", ramTileCount);
    }

    public void registerLoneDisplayedTile(BrickActor actor) 
    {
        if (actualDisplayTiles.containsKey(actor.getBrainTile()))
            return;
        Texture3d texture = ((VolumeMipMaterial)actor.getMaterial()).getTexture();
        synchronized(actualDisplayTiles) {
            nearVolumeMetadata.clear();
            nearVolumeMetadata.add(actor.getBrainTile());
            nearVolumeInRam.clear();
            nearVolumeInRam.put(actor.getBrainTile(), texture);
            actualDisplayTiles.clear();
            actualDisplayTiles.put(actor.getBrainTile(), actor);
        }
    }

    public void clearAllTiles() {
        nearVolumeMetadata.clear();
        nearVolumeInRam.clear();
        actualDisplayTiles.clear();
    }
    
    public boolean isUpdateCache() {
        return doUpdateCache;
    }

    public void setUpdateCache(boolean doUpdateCache) {
        if (this.doUpdateCache == doUpdateCache)
            return; // no change
        this.doUpdateCache = doUpdateCache;
        if (this.doUpdateCache) {
            updateLocation(); // Begin any pending loads
        }
    }
    
    public void toggleUpdateCache() {
        setUpdateCache(! isUpdateCache());
    }
    
    public int getRamTileCount() {
        return ramTileCount;
    }

    public void setRamTileCount(int ramTileCount) {
        this.ramTileCount = ramTileCount;
    }

    public StaticVolumeBrickSource getSource() {
        return source;
    }

    public void setSource(StaticVolumeBrickSource source) {
        this.source = source;
    }

    public int getGpuTileCount() {
        return gpuTileCount;
    }

    public void setGpuTileCount(int videoTileCount) {
        this.gpuTileCount = videoTileCount;
    }
    
    private void updateLocation() {
        updateLocation(camera);
    }
    
    private void updateLocation(PerspectiveCamera cam) {
        float[] focusXyz = cam.getVantage().getFocus();
        float zoom = cam.getVantage().getSceneUnitsPerViewportHeight();
        updateLocation(focusXyz, zoom);
    }
    
    private void updateLocation(float[] xyz, float zoom)
    {
        if (! doUpdateCache)
            return;

        // Cache previous location for early termination
        if (xyz[0] == cachedFocusX
                && xyz[1] == cachedFocusY
                && xyz[2] == cachedFocusZ
                && zoom == cachedZoom) 
        {
            return; // no important change to camera
        }
        cachedFocusX = xyz[0];
        cachedFocusY = xyz[1];
        cachedFocusZ = xyz[2];
        cachedZoom = zoom;

        if (source == null) {
            return;
        }
        
        // Find the metadata for the closest volume tiles
        BrickInfoSet allBricks = NeuronTraceLoader.getBricksForCameraResolution(source, camera);
        Collection<BrickInfo> closestBricks = allBricks.getClosestBricks(xyz, ramTileCount);
        Collection<BrickInfo> veryClosestBricks = allBricks.getClosestBricks(xyz, gpuTileCount);

        synchronized(desiredDisplayTiles) {
            desiredDisplayTiles.clear();
            desiredDisplayTiles.addAll(veryClosestBricks);
        }

        // Compare to cached list of tile metadata
        // Create list of new and obsolete tiles
        Collection<BrickInfo> obsoleteBricks = new ArrayList<>();
        Collection<BrickInfo> newBricks = new ArrayList<>();
        for (BrickInfo brickInfo : nearVolumeMetadata) {
            if (closestBricks.contains(brickInfo)) 
                continue;
            obsoleteBricks.add(brickInfo);
        }
        for (BrickInfo brickInfo : closestBricks) {
            if (nearVolumeMetadata.contains(brickInfo))
                continue;
            newBricks.add(brickInfo);
        }
        
        // Update local cache
        nearVolumeMetadata.removeAll(obsoleteBricks);
        nearVolumeMetadata.addAll(newBricks);
        // but don't update new tiles in ram until they are loaded

        // Upload closest tiles to GPU, if already loaded in RAM
        for (BrickInfo brick : desiredDisplayTiles) { // These are the tiles we want do display right now.
            BrainTileInfo tile = (BrainTileInfo)brick;
            if (actualDisplayTiles.containsKey(brick)) {// Is it already displayed?
                LOG.debug("Already displaying: "+tile.getTileRelativePath());
                continue; // already loaded
            }
            if (nearVolumeInRam.containsKey(brick)) { // Is the texture ready?
                LOG.debug("Already in RAM: "+tile.getTileRelativePath());
                uploadToGpu((BrainTileInfo)brick); // then display it!
            }
        }
        
        // Begin loading the new tiles asynchronously to RAM
        // ...starting with the ones we want to display right now
        for (BrickInfo brick : desiredDisplayTiles) {
            BrainTileInfo tile = (BrainTileInfo)brick;
            if (actualDisplayTiles.containsKey(brick)) {// Is it already displayed?
                continue; // already displayed
            }
            if (nearVolumeInRam.containsKey(brick)) { // Is the texture already loaded in RAM?
                continue; // already loaded
            }
            LOG.debug("Queueing brick with norm priority: "+tile.getTileRelativePath());
            queueLoad(tile, Thread.NORM_PRIORITY);
        }
        for (BrickInfo brick : newBricks) {
            BrainTileInfo tile = (BrainTileInfo)brick;
            LOG.debug("Queueing brick with min priority: "+tile.getTileRelativePath());
            queueLoad(tile, Thread.MIN_PRIORITY); // Don't worry; duplicates will be skipped
        }

        // Begin deleting the old tiles        
        for (BrickInfo brick : obsoleteBricks) {
            BrainTileInfo tile = (BrainTileInfo)brick;
            LOG.info("Removing from RAM: "+tile.getTileRelativePath());
            nearVolumeInRam.remove(brick);
        }
    }
    
    private void queueLoad(final BrainTileInfo tile, int priority) 
    {
        if (! doUpdateCache)
            return;

        if (loadingTiles.containsKey(tile))
            return; // already loading

        Runnable loadTask = new Runnable() {
            @Override
            public void run() {

                LOG.info("Beginning load for {}", tile.getTileRelativePath());

                if (Thread.currentThread().isInterrupted()) {
                    LOG.info("loadTask was interrupted before it began");
                    queuedTiles.remove(tile);
                    return;
                }

                // Move from "queued" to "loading" state
                synchronized(queuedTiles) {
                    RequestProcessor.Task task = queuedTiles.get(tile);
                    if (task==null) {
                        LOG.warn("Tile has no task: "+tile.getTileRelativePath());
                        return;
                    }
                    loadingTiles.put(tile, task);
                    queuedTiles.remove(tile);
                }

                ProgressHandle progress = ProgressHandleFactory.createHandle("Loading Tile " + tile.getTileRelativePath() + " ...");

                try {
                    // should we throttle to slow down loading of too many tiles if user is moving a lot?
                    if (! nearVolumeMetadata.contains(tile)) {
                        return;
                    }

                    if (!doUpdateCache) {
                        return;
                    }

                    progress.start();
                    progress.setDisplayName("Loading Tile " + tile.getTileRelativePath() + " ...");
                    progress.switchToIndeterminate();
                    Texture3d tileTexture = tile.loadBrick(10, currentColorChannel, source.getFileType().getExtension());
                    if (tileTexture != null) {
                        if (nearVolumeMetadata.contains(tile)) { // Make sure this tile is still desired after loading
                            nearVolumeInRam.put(tile, tileTexture);
                            // Trigger GPU upload, if appropriate
                            if (desiredDisplayTiles.contains(tile)) {
                                // Trigger GPU upload
                                uploadToGpu(tile);
                            }
                        }
                    }
                    else {
                        LOG.info("Load was interrupted for: {}", tile.getTileRelativePath());
                    }
                } catch (Exception ex) {
                    Exceptions.printStackTrace(ex);
                }
                finally {
                    loadingTiles.remove(tile);
                    progress.finish();
                }
            };
        };
        // Submit load task asynchronously
        int start_lag = 0; // milliseconds
        if (priority < Thread.NORM_PRIORITY) {
            start_lag = 0;
        }

        synchronized (queuedTiles) {
            if (priority == Thread.NORM_PRIORITY) {
                LOG.debug("Cancelling all current tasks to make room for {}", tile.getTileRelativePath());
                // Cancel all current tasks so that this one can execute with haste
                if (expediteTileLoad(queuedTiles, tile)) return;
                if (expediteTileLoad(loadingTiles, tile)) return;
            }
            LOG.info("Queueing brick {} with priority {} (queued={}, loading={})", tile.getTileRelativePath(),priority, queuedTiles.size(), loadingTiles.size());
            queuedTiles.put(tile, loadProcessor.post(loadTask, start_lag, priority));
        }
    }

    private boolean expediteTileLoad(Map<BrickInfo, RequestProcessor.Task> taskMap, BrainTileInfo wantedTile) {
        // Walk the map and abort if the given wanted tile is already there. Cancel any other tasks that may be in its way.
        for(Iterator<Map.Entry<BrickInfo, RequestProcessor.Task>> iterator = taskMap.entrySet().iterator(); iterator.hasNext(); ) {
            Map.Entry<BrickInfo, RequestProcessor.Task> entry = iterator.next();
            BrainTileInfo tileToCancel = (BrainTileInfo)entry.getKey();
            RequestProcessor.Task task = entry.getValue();
            if (tileToCancel.equals(wantedTile) && task.getPriority()==Thread.NORM_PRIORITY) {
                // The tile we want is already loading at the correct priority
                LOG.debug("Tile is already in flight: {}", tileToCancel.getTileRelativePath());
                return true;
            }
            LOG.info("Cancelling load for {} (priority {})", tileToCancel.getTileRelativePath(), task.getPriority());
            task.cancel();
            iterator.remove();
        }
        return false;
    }

    private void uploadToGpu(BrainTileInfo brick) {
        if (! doUpdateCache)
            return;
        if (actualDisplayTiles.containsKey(brick))
            return; // already displayed
        if (! desiredDisplayTiles.contains(brick))
            return; // not needed
        
        Texture3d texture3d = nearVolumeInRam.get(brick);
        if (texture3d == null) {
            LOG.error("Volume should be loaded but isn't: "+brick.getTileRelativePath());
            return; // Sorry, that volume is not loaded FIXME: error handling here
        }

        LOG.info("Loading to GPU: "+brick.getTileRelativePath());

        final BrickActor actor = new BrickActor(brick, texture3d, imageColorModel, volumeState);
        actualDisplayTiles.put(brick, actor);
        
        // Hide obsolete displayed tiles
        int hideCount = actualDisplayTiles.size() - desiredDisplayTiles.size() + 1;
        if (hideCount > 0) {
            // Using iterator to avoid ConcurrentModificationException
            Iterator<Map.Entry<BrickInfo, BrickActor>> iter = actualDisplayTiles.entrySet().iterator();
            while(iter.hasNext()) {
                Map.Entry<BrickInfo, BrickActor> entry = iter.next();
                BrainTileInfo tile = (BrainTileInfo)entry.getKey();
                if (! desiredDisplayTiles.contains(tile)) {
                    iter.remove();
                    // System.out.println("I should be UNdisplaying tile " + tile.getTileRelativePath() + " now");
                    hideCount --;
                    if (hideCount <= 0)
                        break;
                }
            }
        }
        
        fireUpdateInEDT(actor);
    }

    public void addObserver(TileDisplayObserver observer) {
        if (observers.contains(observer))
            return;
        observers.add(observer);
    }

    public void deleteObserver(TileDisplayObserver observer) {
        observers.remove(observer);
    }

    public void deleteObservers() {
        observers.clear();
    }

    public void setColorChannel(int colorChannel) {
        this.currentColorChannel = colorChannel;
    }

    private class CameraObserver implements Observer
    {
        @Override
        public void update(Observable o, Object arg) {
            updateLocation();
        }
    }
    
    public interface TileDisplayObserver {
        void update(BrickActor newTile, Collection<? extends BrickInfo> allTiles);
    }

    private void fireUpdateInEDT(final BrickActor actor) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                for (TileDisplayObserver observer : observers) {
                    observer.update(actor, actualDisplayTiles.keySet());
                }
            }
        });
    }
}