package org.janelia.horta.options;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import net.miginfocom.swing.MigLayout;
import org.openide.util.NbPreferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TileLoadingPanel extends javax.swing.JPanel {

    private static final Logger log = LoggerFactory.getLogger(TileLoadingPanel.class);
    
    public static final String PREFERENCE_CONCURRENT_LOADS = "ConcurrentLoads";
    public static final String PREFERENCE_CONCURRENT_LOADS_DEFAULT = "1";

    public static final String PREFERENCE_RAM_TILE_COUNT = "RamTileCount";
    public static final String PREFERENCE_RAM_TILE_COUNT_DEFAULT = "4";

    public static final String PREFERENCE_ANNOTATIONS_CLICK_MODE = "AnnotationClickMode";
    public static final String CLICK_MODE_SHIFT_LEFT_CLICK = "shift-left-click";
    public static final String CLICK_MODE_LEFT_CLICK = "left-click";
    public static final String PREFERENCE_ANNOTATIONS_CLICK_MODE_DEFAULT = CLICK_MODE_SHIFT_LEFT_CLICK;

    private final TileLoadingOptionsPanelController controller;
    private final JTextField concurrentLoadsField;
    private final JTextField ramTileCountField;
    private JComboBox<String> clickModeCombo;

    DocumentListener listener = new DocumentListener() {
        @Override
        public void insertUpdate(DocumentEvent e) {
            controller.changed();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            controller.changed();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            controller.changed();
        }
    };

    TileLoadingPanel(final TileLoadingOptionsPanelController controller) {
        this.controller = controller;
        initComponents();

        JPanel attrPanel = new JPanel(new MigLayout("wrap 2, ins 20", "[grow 0, growprio 0][grow 100, growprio 100]"));

        this.concurrentLoadsField = new JTextField(10);
        concurrentLoadsField.getDocument().addDocumentListener(listener);
        JLabel titleLabel = new JLabel("Concurrent tile loads: ");
        titleLabel.setLabelFor(concurrentLoadsField);
        attrPanel.add(titleLabel,"gap para");
        attrPanel.add(concurrentLoadsField,"gap para, width 100:400:600, growx");

        this.ramTileCountField = new JTextField(10);
        ramTileCountField.getDocument().addDocumentListener(listener);
        titleLabel = new JLabel("Number of tiles to cache in RAM: ");
        titleLabel.setLabelFor(ramTileCountField);
        attrPanel.add(titleLabel,"gap para");
        attrPanel.add(ramTileCountField,"gap para, width 100:400:600, growx");

        // note: this click-mode preference really belongs in the other panel, ApplicationPanel,
        //  alongside the 2d version; unfortunately, they are currently using the same text string
        //  key in different classes, so doing so would require code to migrate and update the
        //  prefs, which I don't have time for right now
        String [] modeStrings = {CLICK_MODE_LEFT_CLICK, CLICK_MODE_SHIFT_LEFT_CLICK};
        this.clickModeCombo = new JComboBox<>(modeStrings);
        clickModeCombo.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                controller.changed();
            }
        });
        // default to the original behavior, shift-left-click
        clickModeCombo.setSelectedItem(CLICK_MODE_SHIFT_LEFT_CLICK);
        JLabel clickModeLabel = new JLabel("Click mode for adding annotations (3d): ");
        clickModeLabel.setLabelFor(clickModeCombo);
        attrPanel.add(clickModeLabel, "gap para");
        attrPanel.add(clickModeCombo, "gap para");

        add(attrPanel, BorderLayout.CENTER);
    }

    /**
     * This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        setLayout(new java.awt.BorderLayout());
    }// </editor-fold>//GEN-END:initComponents

    void load() {
        concurrentLoadsField.setText(NbPreferences.forModule(TileLoadingPanel.class).get(PREFERENCE_CONCURRENT_LOADS, PREFERENCE_CONCURRENT_LOADS_DEFAULT));
        ramTileCountField.setText(NbPreferences.forModule(TileLoadingPanel.class).get(PREFERENCE_RAM_TILE_COUNT, PREFERENCE_RAM_TILE_COUNT_DEFAULT));
        clickModeCombo.setSelectedItem(NbPreferences.forModule(TileLoadingPanel.class).get(PREFERENCE_ANNOTATIONS_CLICK_MODE, PREFERENCE_ANNOTATIONS_CLICK_MODE_DEFAULT));
    }

    void store() {
        NbPreferences.forModule(TileLoadingPanel.class).put(PREFERENCE_CONCURRENT_LOADS, concurrentLoadsField.getText());
        NbPreferences.forModule(TileLoadingPanel.class).put(PREFERENCE_RAM_TILE_COUNT, ramTileCountField.getText());
        NbPreferences.forModule(TileLoadingPanel.class).put(PREFERENCE_ANNOTATIONS_CLICK_MODE, (String) clickModeCombo.getSelectedItem());
    }

    boolean valid() {
        try {
            Integer.parseInt(concurrentLoadsField.getText());
            Integer.parseInt(ramTileCountField.getText());
            // click mode drop-down is always valid
        }
        catch (NumberFormatException e) {
            return false;
        }
        return true;
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
}
