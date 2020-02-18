package com.oxygenxml.git.view.historycomponents;

import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.Action;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.MenuElement;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.Ignore;
import org.powermock.api.mockito.PowerMockito;

import com.oxygenxml.git.service.GitTestBase;
import com.oxygenxml.git.service.entities.FileStatus;
import com.oxygenxml.git.view.StagingResourcesTableModel;
import com.oxygenxml.git.view.event.GitController;

/**
 * UI level tests for history.
 *  
 * @author alex_jitianu
 */
@Ignore
public class HistoryPanelTestBase extends GitTestBase {

  protected HistoryPanel historyPanel;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    setUpHistoryPanel();
  }

  private void setUpHistoryPanel() {
    // Initialize history panel.
    historyPanel = new HistoryPanel(new GitController()) {
      @Override
      protected int getUpdateDelay() {
        return 0;
      }

      @Override
      public boolean isShowing() {
        // Branch related refresh is done only if the view is displayed.
        return true;
      }
    };

  }

  /**
   * Asserts the presented affected files.
   * 
   * @param historyPanel History table.
   * @param expected The expected content.
   */
  protected void assertAffectedFiles(HistoryPanel historyPanel, String expected) {
    JTable affectedFilesTable = historyPanel.affectedFilesTable;
    StagingResourcesTableModel affectedFilesModel = (StagingResourcesTableModel) affectedFilesTable.getModel();
    String dumpFS = dumpFS(affectedFilesModel.getFilesStatuses());

    assertEquals(expected, dumpFS);
  }

  /**
   * Selects a specific revision in the history table and asserts its description. 
   * 
   * @param historyTable History table.
   * @param row Which row to select.
   * @param expected The expected revision description.
   */
  protected void selectAndAssertRevision(JTable historyTable, int row, String expected) {
    HistoryCommitTableModel model = (HistoryCommitTableModel) historyTable.getModel();
    historyTable.getSelectionModel().setSelectionInterval(row, row);
    // There is a timer involved.
    try {
      Thread.sleep(100);
    } catch (InterruptedException e) {}
    flushAWT();
    CommitCharacteristics selectedObject = (CommitCharacteristics) model.getValueAt(historyTable.getSelectedRow(), 0);
    assertEquals(replaceDate(expected), toString(selectedObject));
  }

  protected String replaceDate(String expected) {
    return expected.replaceAll("\\{date\\}",  DATE_FORMAT.format(new Date()));
  }
  

  protected List<Action> getCompareWithPreviousAction(
      FileStatus fileStatus, 
      CommitCharacteristics cc) throws IOException, GitAPIException {
    HistoryViewContextualMenuPresenter menuPresenter = 
        new HistoryViewContextualMenuPresenter(PowerMockito.mock(GitController.class));
    JPopupMenu jPopupMenu = new JPopupMenu();
    menuPresenter.populateContextualActions(jPopupMenu, fileStatus.getFileLocation(), cc);
    
    MenuElement[] subElements = jPopupMenu.getSubElements();
    
    List<Action> actions = Arrays.asList(subElements).stream()
        .map(t -> ((JMenuItem) t).getAction())
        .filter(t -> ((String) t.getValue(Action.NAME)).startsWith("Compare_file_with_previous_"))
        .collect(Collectors.toList());

    assertFalse("Unable to find the 'Compare with previous version' action.", actions.isEmpty());
    
    return actions;
  }
  
  protected Action getCompareWithWCAction(
      FileStatus fileStatus, 
      CommitCharacteristics cc) throws IOException, GitAPIException {
    HistoryViewContextualMenuPresenter menuPresenter = 
        new HistoryViewContextualMenuPresenter(PowerMockito.mock(GitController.class));
    JPopupMenu jPopupMenu = new JPopupMenu();
    menuPresenter.populateContextualActions(jPopupMenu, fileStatus.getFileLocation(), cc);
    
    MenuElement[] subElements = jPopupMenu.getSubElements();
    
//    for (int i = 0; i < subElements.length; i++) {
//      System.out.println(((JMenuItem) subElements[i]).getAction().getValue(Action.NAME));
//    }
    
    List<Action> actions = Arrays.asList(subElements).stream()
        .map(t -> ((JMenuItem) t).getAction())
        .filter(t -> ((String) t.getValue(Action.NAME)).startsWith("Compare_file_with_working_tree_version"))
        .collect(Collectors.toList());

    assertFalse("Unable to find the 'Compare_with_working_tree_version' action.", actions.isEmpty());
    
    return actions.get(0);
  }
}
