/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.janelia.it.workstation.ab2;

import javax.swing.BoxLayout;
import javax.swing.SwingUtilities;

import org.janelia.it.jacs.integration.FrameworkImplProvider;
import org.janelia.it.workstation.ab2.controller.AB2Controller;
import org.janelia.it.workstation.ab2.event.AB2SampleAddedEvent;
import org.janelia.it.workstation.ab2.model.AB2Data;
import org.janelia.it.workstation.browser.events.Events;
import org.janelia.it.workstation.browser.model.SampleImage;
import org.netbeans.api.settings.ConvertAsProperties;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.TopComponent;
import org.openide.windows.TopComponentGroup;
import org.openide.windows.WindowManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Top component which displays something.
 */
@ConvertAsProperties(
        dtd = "-//org.janelia.it.workstation.ab2//AB2//EN",
        autostore = false
)
@TopComponent.Description(
        preferredID = AB2TopComponent.TC_NAME,
        //iconBase="SET/PATH/TO/ICON/HERE", 
        persistenceType = TopComponent.PERSISTENCE_ALWAYS
)
@TopComponent.Registration(mode = "editor", openAtStartup = false)
@ActionID(category = "Window", id = "org.janelia.it.workstation.ab2.AB2TopComponent")
@ActionReference(path = "Menu/Window/Alignment Board", position = 30)
@TopComponent.OpenActionRegistration(
        displayName = "#CTL_AB2Action",
        preferredID = AB2TopComponent.TC_NAME
)
@Messages({
    "CTL_AB2Action=AB2",
    "CTL_AB2TopComponent=AB2 Window",
    "HINT_AB2TopComponent=AB2 window"
})
public final class AB2TopComponent extends TopComponent {

    private Logger logger = LoggerFactory.getLogger(AB2TopComponent.class);

    public static final String TC_NAME = "AB2TopComponent";
    public static final String TC_VERSION = "1.0";

    private static AB2TopComponent ab2TopComponent;

    private AB2GLPanel ab2GLPanel;
    private AB2Controller ab2Controller;
    private AB2Data ab2Data;

    private AB2TopComponent() {
        logger.info("AB2TopComponent() constructed");
        initComponents();
        setName(Bundle.CTL_AB2TopComponent());
        setToolTipText(Bundle.HINT_AB2TopComponent());
    }

    public static AB2TopComponent findComp() {
        if (ab2TopComponent == null) {
            TopComponent tc = WindowManager.getDefault().findTopComponent(TC_NAME);
            if (tc == null) {
                tc = AB2TopComponent.createComp();
            }
            ab2TopComponent=(AB2TopComponent)tc;
        }
        return ab2TopComponent;
    }

    public static AB2TopComponent createComp() {
        if (ab2TopComponent == null) {
            ab2TopComponent = new AB2TopComponent();
        }
        return ab2TopComponent;
    }

    public void loadSampleImage(SampleImage sampleImage, boolean isUserDriven) {
        logger.info("loadSampleImage() calling controller.processEvent() with AB2SampleAddedEvent");
        AB2Controller.getController().processEvent(new AB2SampleAddedEvent(sampleImage));
    }
    
    private void initMyComponents() {
        if (ab2Controller == null) {
            ab2Controller = AB2Controller.getController();
        }
        if (ab2Data == null) {
            ab2Data = new AB2Data();
        }
        if (ab2GLPanel==null) {
            ab2GLPanel=new AB2GLPanel(600, 400, ab2Controller);
            ab2Controller.setGljPanel(ab2GLPanel);
            glWrapperPanel.setLayout(new BoxLayout(glWrapperPanel, BoxLayout.Y_AXIS));
            glWrapperPanel.add(ab2GLPanel);
//            AB2SkeletonDomainObject skeletonDomainObject=new AB2SkeletonDomainObject();
//            try {
//                logger.info("Check3");
//                skeletonDomainObject.createSkeletonsAndVolume(10);
//                logger.info("Created skeleton domain object");
//            } catch (Exception ex) {
//                ex.printStackTrace();
//                logger.error(ex.getMessage());
//                return;
//            }
//            logger.info("Check4");
//            ab2Controller.setDomainObject(skeletonDomainObject);
        }
        ab2GLPanel.setVisible(true);
        glWrapperPanel.setVisible(true);
        ab2Controller.start();
    }

    private void closeMyComponents() {
        ab2Controller.shutdown();
        //try { Thread.sleep(5000); } catch (Exception ex) {}
    }

        /**
         * This method is called from within the constructor to initialize the form.
         * WARNING: Do NOT modify this code. The content of this method is always
         * regenerated by the Form Editor.
         */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        glWrapperPanel = new javax.swing.JPanel();

        javax.swing.GroupLayout glWrapperPanelLayout = new javax.swing.GroupLayout(glWrapperPanel);
        glWrapperPanel.setLayout(glWrapperPanelLayout);
        glWrapperPanelLayout.setHorizontalGroup(
            glWrapperPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 1000, Short.MAX_VALUE)
        );
        glWrapperPanelLayout.setVerticalGroup(
            glWrapperPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 750, Short.MAX_VALUE)
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(glWrapperPanel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(glWrapperPanel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel glWrapperPanel;
    // End of variables declaration//GEN-END:variables
    @Override
    public void componentOpened() {
        logger.info("AB2TopComponent opened()");
        Events.getInstance().registerOnEventBus(this);
        initMyComponents();
    }

    @Override
    public void componentClosed() {
        logger.info("AB2TopComponent closed()");
        closeMyComponents();
        Events.getInstance().unregisterOnEventBus(this);
        Runnable runnable = new Runnable() {
            public void run() {
                TopComponentGroup tcg = WindowManager.getDefault().findTopComponentGroup(
                        "AB2TopComponent"
                );
                if (tcg != null) {
                    tcg.close();
                }
            }
        };
        if ( SwingUtilities.isEventDispatchThread() ) {
            runnable.run();
        }
        else {
            try {
                SwingUtilities.invokeAndWait(runnable);
            }
            catch (Exception ex) {
                FrameworkImplProvider.handleException(ex);
            }
        }

    }

    void writeProperties(java.util.Properties p) {
        // better to version settings since initial version as advocated at
        // http://wiki.apidesign.org/wiki/PropertyFiles
        p.setProperty("version", "1.0");
        // TODO store your settings
    }

    void readProperties(java.util.Properties p) {
        String version = p.getProperty("version");
        // TODO read your settings according to their version
    }
}
