package org.janelia.it.workstation.browser.gui.dialogs.download;

import javax.swing.event.ChangeListener;

import org.janelia.it.workstation.browser.ConsoleApp;
import org.openide.WizardDescriptor;
import org.openide.util.HelpCtx;

public class DownloadWizardPanel4 implements WizardDescriptor.Panel<WizardDescriptor> {

    /**
     * The visual component that displays this panel. If you need to access the
     * component from this class, just use getComponent().
     */
    private DownloadVisualPanel4 component;

    // Get the visual component for the panel. In this template, the component
    // is kept separate. This can be more efficient: if the wizard is created
    // but never displayed, or not all panels are displayed, it is better to
    // create only those which really need to be visible.
    @Override
    public DownloadVisualPanel4 getComponent() {
        if (component == null) {
            component = new DownloadVisualPanel4();
        }
        return component;
    }

    @Override
    public HelpCtx getHelp() {
        // Show no Help button for this panel:
        return HelpCtx.DEFAULT_HELP;
        // If you have context help:
        // return new HelpCtx("help.key.here");
    }

    @Override
    public boolean isValid() {
        // If it is always OK to press Next or Finish, then:
        return true;
        // If it depends on some condition (form filled out...) and
        // this condition changes (last form field filled in...) then
        // use ChangeSupport to implement add/removeChangeListener below.
        // WizardDescriptor.ERROR/WARNING/INFORMATION_MESSAGE will also be useful.
    }

    @Override
    public void addChangeListener(ChangeListener l) {
    }

    @Override
    public void removeChangeListener(ChangeListener l) {
    }

    @Override
    public void readSettings(WizardDescriptor wiz) {
        DownloadWizardState state = (DownloadWizardState)wiz.getProperty(DownloadWizardIterator.PROP_WIZARD_STATE);
        getComponent().init(state);
    }

    @Override
    public void storeSettings(WizardDescriptor wiz) {
        DownloadWizardState state = (DownloadWizardState)wiz.getProperty(DownloadWizardIterator.PROP_WIZARD_STATE);
        state.setDownloadItems(getComponent().getDownloadItems());
        
      String filePattern = (String)getComponent().getFilenamePattern();
      boolean found = false;
      for (String pattern : DownloadVisualPanel4.STANDARD_FILE_PATTERNS) {
          if (pattern.equals(filePattern)) {
              found = true;
              break;
          }
      }
      if (!found) {
          ConsoleApp.getConsoleApp().setModelProperty(DownloadVisualPanel4.FILE_PATTERN_PROP_NAME, filePattern);
      }
        
    }

}
