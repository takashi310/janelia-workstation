package org.janelia.it.workstation.gui.framework.progress_meter;

import java.awt.BorderLayout;
import java.awt.Image;
import java.util.Properties;

import org.netbeans.api.settings.ConvertAsProperties;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.windows.TopComponent;
import org.openide.util.NbBundle.Messages;

/**
 * Top component which usually slides in from the right side when a background
 * task is executed. Shows progress and "next step" buttons for all 
 * background tasks.
 */
@ConvertAsProperties(
        dtd = "-//org.janelia.it.workstation.gui.framework.progress_meter//ProgressTopComponent//EN",
        autostore = false
)
@TopComponent.Description(
        preferredID = ProgressTopComponent.PREFERRED_ID,
        //iconBase = "images/cog_small_anim.gif",
        persistenceType = TopComponent.PERSISTENCE_ALWAYS
)
@TopComponent.Registration(mode = "rightSlidingSide", openAtStartup = true, position=100)
@ActionID(category = "Window", id = "ProgressTopComponent")
@ActionReference(path = "Menu/Window", position = 333)
@TopComponent.OpenActionRegistration(
        displayName = "#CTL_ProgressTopComponentAction",
        preferredID = "ProgressTopComponentTopComponent"
)
@Messages({
    "CTL_ProgressTopComponentAction=Background Tasks",
    "CTL_ProgressTopComponent=Background Tasks",
    "HINT_ProgressTopComponentTopComponent=See progress of background tasks"
})
public final class ProgressTopComponent extends TopComponent {

    public static final String PREFERRED_ID = "ProgressTopComponent";
    
    private final ProgressMeterPanel progressMeter = ProgressMeterPanel.getSingletonInstance();
    
    public ProgressTopComponent() {
        initComponents();
        setName(Bundle.CTL_ProgressTopComponent());
        setToolTipText(Bundle.HINT_ProgressTopComponentTopComponent());
    }
    
    @Override
    public Image getIcon() {
        // TODO: figure out a way to reliably repaint the TopComponent's icon, 
        // and use getCurrentIcon to get either the static or animated icon.
        // For now we'll just show the static icon always.
        return progressMeter.getStaticIcon().getImage();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        mainPanel = new javax.swing.JPanel();

        mainPanel.setLayout(new java.awt.BorderLayout());

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(mainPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 234, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(mainPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 222, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel mainPanel;
    // End of variables declaration//GEN-END:variables
    @Override
    public void componentOpened() {
        mainPanel.add(progressMeter, BorderLayout.CENTER);
    }

    @Override
    public void componentClosed() {
        // TODO add custom code on component closing
    }

    void writeProperties(Properties p) {
        // better to version settings since initial version as advocated at
        // http://wiki.apidesign.org/wiki/PropertyFiles
        p.setProperty("version", "1.0");
        // TODO store your settings
    }

    void readProperties(Properties p) {
        String version = p.getProperty("version");
        // TODO read your settings according to their version
    }
}
