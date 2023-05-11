package com.oxygenxml.git.view.history;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revplot.PlotCommit;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jidesoft.swing.JideSplitPane;
import com.oxygenxml.git.constants.Icons;
import com.oxygenxml.git.constants.UIConstants;
import com.oxygenxml.git.options.OptionsManager;
import com.oxygenxml.git.service.GitAccess;
import com.oxygenxml.git.service.GitEventAdapter;
import com.oxygenxml.git.service.GitOperationScheduler;
import com.oxygenxml.git.service.RevCommitUtil;
import com.oxygenxml.git.service.annotation.TestOnly;
import com.oxygenxml.git.service.entities.FileStatus;
import com.oxygenxml.git.service.exceptions.NoRepositorySelected;
import com.oxygenxml.git.service.exceptions.PrivateRepositoryException;
import com.oxygenxml.git.service.exceptions.RepositoryUnavailableException;
import com.oxygenxml.git.service.exceptions.SSHPassphraseRequiredException;
import com.oxygenxml.git.translator.Tags;
import com.oxygenxml.git.translator.Translator;
import com.oxygenxml.git.utils.Equaler;
import com.oxygenxml.git.utils.FileUtil;
import com.oxygenxml.git.utils.RepoUtil;
import com.oxygenxml.git.view.FilterTextField;
import com.oxygenxml.git.view.event.GitController;
import com.oxygenxml.git.view.event.GitEventInfo;
import com.oxygenxml.git.view.event.GitOperation;
import com.oxygenxml.git.view.history.graph.CommitsGraphCellRender;
import com.oxygenxml.git.view.history.graph.VisualCommitsList.VisualLane;
import com.oxygenxml.git.view.util.TreeUtil;
import com.oxygenxml.git.view.util.UIUtil;

import ro.sync.exml.workspace.api.PluginWorkspace;
import ro.sync.exml.workspace.api.PluginWorkspaceProvider;
import ro.sync.exml.workspace.api.editor.WSEditor;
import ro.sync.exml.workspace.api.listeners.WSEditorChangeListener;
import ro.sync.exml.workspace.api.listeners.WSEditorListener;
import ro.sync.exml.workspace.api.standalone.ui.SplitMenuButton;
import ro.sync.exml.workspace.api.standalone.ui.Table;
import ro.sync.exml.workspace.api.standalone.ui.ToolbarButton;

/**
 * Presents the commits for a given resource.
 */
public class HistoryPanel extends JPanel {
  
  /**
   * The filter allocated percent.
   */
  private static final int FILTER_PERCENT_ALLOCATED = 30;

  /**
   * History label right inset.
   */
  private static final int INFO_HISTORY_WIDTH_INSET = 20;
  
  /**
   * Logger for logging.
   */
  private static final Logger LOGGER =  LoggerFactory.getLogger(HistoryPanel.class);
  
  /**
   * Git API access.
   */
  private static GitAccess gitAccess = GitAccess.getInstance();
  
  /**
   * The translator.
   */
  private static final Translator TRANSLATOR = Translator.getInstance();
  
  /**
   * Table view that presents the commits.
   */
  JTable historyTable;
  
  /**
   * Panel presenting a detailed description of the commit (author, date, etc).
   */
  private JEditorPane commitDescriptionPane;
  
  /**
   * The history label text
   */
  private String historyLabelMessage;
  
  /**
   * The label that shows information about the history we present.
   */
  private JLabel historyInfoLabel;
  
  /**
   * Intercepts clicks in the commit details area.
   */
  private HistoryHyperlinkListener hyperlinkListener;
  
  /**
   * Commit selection listener that updates all the views with details.
   */
  private RowHistoryTableSelectionListener revisionDataUpdater;
  
  /**
   * The changed files from a commit.
   */
  private JTable affectedFilesTable;
  
  /**
   * The listener for commit selection.
   */
  private ListSelectionListener commitSelectionListener;
  
  /**
   * The file path of the resource for which we are currently presenting the
   * history. If <code>null</code>, we present the history for the entire
   * repository.
   */
  private String activeFilePath;
  
  /**
   * Presents the contextual menu.
   */
  private HistoryViewContextualMenuPresenter contextualMenuPresenter;
  
  /**
   * Filter field for quick search
   */
  private FilterTextField filter;
  
  /**
   * Top panel (with the "Showing history" label and the "Refresh" action
   */
  private JPanel topPanel;
  
  /**
   * The graph cell render.
   */
  private final CommitsGraphCellRender graphCellRender;
  
  /**
   * The file presenter for repository commits history.
   */
  private final transient FileHistoryPresenter fileHistoryPresenter = new FileHistoryPresenter();
  
  /**
   * The current strategy to present history.
   */
  private HistoryStrategy currentStrategy;
  
  /**
   * Button that contains all strategy to present history.
   */
  private final SplitMenuButton presentHistoryStrategyButton; 
  
  /**
   * <code>true</code> if the branch has uncommited changes.
   */
  private boolean hasUncommitedChanges  = false;

  /**
   * <code>true<code> if the component has previous state for showed.
   */
  private boolean wasPreviousShowed = false;
  
  /**
   * The previous commits cached.
   */
  private List<CommitCharacteristics> commitsCache = Collections.emptyList();
  
  /**
   * The last selected commit id.
   */
  private ObjectId selectedCommitId = null;
  

  /**
   * Constructor.
   * 
   * @param gitCtrl Executes a set of Git commands.
   */
  public HistoryPanel(GitController gitCtrl) {
    
    setLayout(new BorderLayout());
    
    this.addHierarchyListener(e ->  {
      final boolean actualState = isShowing();
      if(actualState && !wasPreviousShowed) {
        GitOperationScheduler.getInstance().schedule(() -> RepoUtil.initRepoIfNeeded(true));
      }
      wasPreviousShowed = actualState;
    });
    
    graphCellRender = new CommitsGraphCellRender();
  
    currentStrategy = OptionsManager.getInstance().getHistoryStrategy();
    
    if(currentStrategy == null) {
      currentStrategy = HistoryStrategy.ALL_BRANCHES;
    }
    
    presentHistoryStrategyButton = new SplitMenuButton(currentStrategy.toString(), 
        null, true, false, true, true);
    
    addPresentHistoryActions(presentHistoryStrategyButton);
    
    contextualMenuPresenter = new HistoryViewContextualMenuPresenter(gitCtrl);
    initHistoryTable();

    JScrollPane historyTableScrollPane = new JScrollPane(historyTable);
    historyTable.setFillsViewportHeight(true);

    commitDescriptionPane = new JEditorPane();
    initEditorPane(commitDescriptionPane);
    JScrollPane commitDescriptionScrollPane = new JScrollPane(commitDescriptionPane);

    affectedFilesTable = createAffectedFilesTable();
    affectedFilesTable.setFillsViewportHeight(true);
    JScrollPane affectedFilesTableScrollPane = new JScrollPane(affectedFilesTable);

    Dimension minimumSize = new Dimension(500, 150);
    commitDescriptionScrollPane.setPreferredSize(minimumSize);
    affectedFilesTableScrollPane.setPreferredSize(minimumSize);

    // ----------
    // Top panel (with the "Showing history" label and the "Refresh" action
    // ----------

    topPanel = new JPanel(new GridBagLayout());
    this.addComponentListener(new ComponentAdapter() {
      
      @Override
      public void componentResized(ComponentEvent e) {
        updateTopPanelComponentsSize();
      }});
    
    topPanel.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
    GridBagConstraints constr = new GridBagConstraints();
    constr.fill = GridBagConstraints.HORIZONTAL;
    constr.gridx = 0;
    constr.gridy = 0;
    constr.insets = new Insets(0, 1, 0, 0);
    constr.weightx = 1;

    historyInfoLabel = new JLabel();
    historyInfoLabel.setMinimumSize(new Dimension(10, historyInfoLabel.getMinimumSize().height));
    topPanel.add(historyInfoLabel, constr);
    createAndAddToolbarToTopPanel(topPanel, constr);

    JPanel infoBoxesSplitPane = UIUtil.createSplitPane(JideSplitPane.HORIZONTAL_SPLIT, commitDescriptionScrollPane,
        affectedFilesTableScrollPane, null, 0);
    JideSplitPane centerSplitPane = UIUtil.createSplitPane(JideSplitPane.VERTICAL_SPLIT, historyTableScrollPane,
        infoBoxesSplitPane, this, 0.6);
    centerSplitPane.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

    gitCtrl.addGitListener(new GitEventAdapter() {
      @Override
      public void operationSuccessfullyEnded(GitEventInfo info) {
        if (isShowing()) {
          GitOperation operation = info.getGitOperation();
          switch (operation) {
          case OPEN_WORKING_COPY:
            clearCommitsCache();
            selectedCommitId = null;
            GitOperationScheduler.getInstance().schedule(HistoryPanel.this::showRepositoryHistory);
            break;
          case PULL:
          case PUSH:
          case CREATE_BRANCH:
          case CHECKOUT:
          case DELETE_BRANCH:
          case COMMIT:
          case DISCARD:
          case MERGE:
          case MERGE_RESTART:
          case ABORT_REBASE:
          case CONTINUE_REBASE:
          case REVERT_COMMIT:
          case CREATE_TAG:
          case DELETE_TAG:
          case CHECKOUT_COMMIT:
        	  clearCommitsCache();
            scheduleRefreshHistory();
            break;
          default:
        	  break;
          }
        }
      }
    });

    // Listens on the save event in the Oxygen editor and updates the history table
    PluginWorkspaceProvider.getPluginWorkspace().addEditorChangeListener(new WSEditorChangeListener() {
      @Override
      public void editorOpened(final URL editorLocation) {
        addEditorSaveHook(editorLocation);
      }
    }, PluginWorkspace.MAIN_EDITING_AREA);

    add(centerSplitPane, BorderLayout.CENTER);
  }

  /**
   * Update history info label text and the filter width.
   * 
   * The @historyLabelMessage will be set or a truncate version of this message if no necessary space is provided. 
   */
  private void updateTopPanelComponentsSize() {
    // needed to set a custom dimension to filter for a better resize view than fill with weightX = (FILTER_PERCENT_ALLOCATED) / 100.00
    final Dimension filterDim = new Dimension( 
        (topPanel.getWidth() * FILTER_PERCENT_ALLOCATED) / 100, 
        filter.getPreferredSize().height);
    filter.setPreferredSize(filterDim);
    filter.setMaximumSize(filterDim);
    filter.setMinimumSize(filterDim);
    int newLabelWidth = topPanel.getWidth() - INFO_HISTORY_WIDTH_INSET;
    for(int i = 0; i < topPanel.getComponentCount(); i++) {
      if(topPanel.getComponent(i) != historyInfoLabel) { // reduce width with the other's components
        newLabelWidth -= topPanel.getComponent(i).getWidth();
      }
    }
    newLabelWidth = newLabelWidth >= 0 ? newLabelWidth : 0;
    historyInfoLabel.setText(TreeUtil.getWordToFitInWidth(historyLabelMessage,
        historyInfoLabel.getFontMetrics(historyInfoLabel.getFont()),
        newLabelWidth));
  }
  
  /**
   * Initialize history table.
   */
  private void initHistoryTable() {
    historyTable = new Table();
    
    historyTable.setIntercellSpacing(new Dimension(0, 0)); 
    historyTable.setShowGrid(false);
    historyTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    historyTable.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(java.awt.event.MouseEvent e) {
        if (e.isPopupTrigger()) {
          showHistoryTableContextualMenu(historyTable, e.getPoint());
        }
      }

      @Override
      public void mouseReleased(java.awt.event.MouseEvent e) {
        mousePressed(e);
      }

      @Override
      public void mouseClicked(MouseEvent e) {
        if (activeFilePath != null && !e.isConsumed() && !e.isPopupTrigger() && e.getClickCount() == 2) {
          e.consume();
          int rowAtPoint = historyTable.rowAtPoint(e.getPoint());
          if (rowAtPoint != -1) {
            updateTableSelection(historyTable, rowAtPoint);
            historyDoubleClickAction(rowAtPoint);
          }
        }
      }
    });
    
  }

  
  /**
   * Adds a hook to refresh the models if the editor is part of the Git working
   * copy.
   * 
   * @param editorLocation Editor to check.
   */
  private void addEditorSaveHook(final URL editorLocation) {
    WSEditor editorAccess = PluginWorkspaceProvider.getPluginWorkspace().getEditorAccess(editorLocation, PluginWorkspace.MAIN_EDITING_AREA);
    if (editorAccess != null) {
      editorAccess.addEditorListener(new WSEditorListener() {
        @Override
        public void editorSaved(int operationType) {
          boolean newHasUncommitedChanges = GitAccess.getInstance().getStatusCache().getStatus().hasUncommittedChanges();
          if(hasUncommitedChanges != newHasUncommitedChanges) {
            GitOperationScheduler.getInstance().schedule(() -> treatEditorSavedEvent(editorLocation));
          }
          hasUncommitedChanges = newHasUncommitedChanges;
        }
      });
    }
  }

  
  /**
   * Add actions for present history in different way. 
   * <br>
   * History could be presented so: All branches(remote + locals), All local branches, Current branch(remote + local), Current local branch.
   * 
   * @param button
   */
  private void addPresentHistoryActions(final SplitMenuButton button) {
	  
	  final ButtonGroup branchActionsGroup = new ButtonGroup();
	  final HistoryStrategy[] strategies   = HistoryStrategy.values();
	  
	  for(HistoryStrategy strategy : strategies) {
		   
		   AbstractAction action = new AbstractAction(strategy.toString()) {
		
			@Override
			public void actionPerformed(ActionEvent arg0) {
				currentStrategy = strategy;
				button.setText(strategy.toString());
				OptionsManager.getInstance().setHistoryStrategy(strategy);
				scheduleRefreshHistory();
			}
			
		   };
		   
		   final JRadioButtonMenuItem menuItem = new JRadioButtonMenuItem(action);
		   menuItem.setToolTipText(strategy.getToolTipText());
		   branchActionsGroup.add(menuItem);
		   button.add(menuItem);
		   if(currentStrategy.equals(strategy)) {
			   menuItem.setSelected(true);
		   }
	  }
  }
	
	 
  
  
  
  /**
   * Treat editor saved event.
   * 
   * @param editorLocation Editor URL.
   */
  private void treatEditorSavedEvent(final URL editorLocation) {
    File localFile = null;
    if ("file".equals(editorLocation.getProtocol())) {
      localFile = PluginWorkspaceProvider.getPluginWorkspace().getUtilAccess().locateFile(editorLocation);
      if (localFile != null) {
        String fileInWorkPath = localFile.toString();
        fileInWorkPath = FileUtil.rewriteSeparator(fileInWorkPath);

        try {
          String selectedRepositoryPath = GitAccess.getInstance().getWorkingCopy().getAbsolutePath();
          selectedRepositoryPath = FileUtil.rewriteSeparator(selectedRepositoryPath);

          if (isShowing() && fileInWorkPath.startsWith(selectedRepositoryPath)) {
            scheduleRefreshHistory();
          }
        } catch (NoRepositorySelected e) {
          LOGGER.debug(e.getMessage(), e);
        }
      }
    }
  }

  
  /**
   * Opens the first action in the contextual menu when an element inside the
   * history table is double clicked.
   * 
   * @param rowAtPoint Position of the element in the history table.
   */
  private void historyDoubleClickAction(int rowAtPoint) {
    HistoryCommitTableModel historyTableModel = (HistoryCommitTableModel) historyTable.getModel();
    int convertedSelectedRow = historyTable.convertRowIndexToModel(rowAtPoint);
    CommitCharacteristics commitCharacteristics = historyTableModel.getAllCommits().get(convertedSelectedRow);
    try {
      Optional<FileStatus> optionalFileStatus = contextualMenuPresenter.getFileStatus(activeFilePath,
          commitCharacteristics);
      if (optionalFileStatus.isPresent()) {
        FileStatus fileStatus = optionalFileStatus.get();
        List<Action> contextualActions = 
            contextualMenuPresenter.getFileContextualActions(fileStatus, commitCharacteristics, false);
        if (!contextualActions.isEmpty()) {
          contextualActions.get(0).actionPerformed(null);
        }
      }
    } catch (IOException | GitAPIException e1) {
      PluginWorkspaceProvider.getPluginWorkspace().showErrorMessage(e1.getMessage());
      LOGGER.error(e1.getMessage(), e1);
    }
  }

  
  /**
   * Creates the table that presents the files changed in a revision.
   * 
   * @return The table that presents the files.
   */
  private JTable createAffectedFilesTable() {
    JTable table = UIUtil.createResourcesTable(new HistoryTableAffectedFilesModel(), () -> false);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    table.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(java.awt.event.MouseEvent e) {
        if (e.isPopupTrigger()) {
          showResourcesContextualMenu(table, e.getPoint());
        }
      }

      @Override
      public void mouseReleased(java.awt.event.MouseEvent e) {
        mousePressed(e);
      }

      @Override
      public void mouseClicked(MouseEvent e) {
        if (!e.isConsumed() && !e.isPopupTrigger() && e.getClickCount() == 2) {
          e.consume();
          int rowAtPoint = table.rowAtPoint(e.getPoint());
          if (rowAtPoint != -1) {
            updateTableSelection(table, rowAtPoint);

            HistoryTableAffectedFilesModel model = (HistoryTableAffectedFilesModel) table.getModel();
            int convertedSelectedRow = table.convertRowIndexToModel(rowAtPoint);
            FileStatus file = model.getFileStatus(convertedSelectedRow);

            HistoryCommitTableModel historyTableModel = (HistoryCommitTableModel) historyTable.getModel();
            CommitCharacteristics commitCharacteristics = historyTableModel.getAllCommits()
                .get(historyTable.getSelectedRow());

            List<Action> contextualActions = 
                contextualMenuPresenter.getFileContextualActions(file, commitCharacteristics, false);
            if (!contextualActions.isEmpty()) {
              contextualActions.get(0).actionPerformed(null);
            }
          }
        }
      }
    });

    return table;
  }

  
  /**
   * Show the contextual menu on the resources changed on a revision.
   * 
   * @param affectedFilesTable The table with the files from a committed on a
   *                           revision.
   * @param point              The point where to show the contextual menu.
   */
  protected void showResourcesContextualMenu(JTable affectedFilesTable, Point point) {
    int rowAtPoint = affectedFilesTable.rowAtPoint(point);
    if (rowAtPoint != -1) {
      updateTableSelection(affectedFilesTable, rowAtPoint);

      HistoryTableAffectedFilesModel model = (HistoryTableAffectedFilesModel) affectedFilesTable.getModel();
      int convertedSelectedRow = affectedFilesTable.convertRowIndexToModel(rowAtPoint);
      FileStatus file = model.getFileStatus(convertedSelectedRow);

      HistoryCommitTableModel historyTableModel = (HistoryCommitTableModel) historyTable.getModel();
      CommitCharacteristics commitCharacteristics = historyTableModel.getAllCommits()
          .get(historyTable.getSelectedRow());

      JPopupMenu jPopupMenu = new JPopupMenu();
      contextualMenuPresenter.populateContextActionsForFile(jPopupMenu, file, commitCharacteristics, false);
      jPopupMenu.show(affectedFilesTable, point.x, point.y);
    }
  }

  
  /**
   * Show the contextual menu on the history table.
   * 
   * @param historyTable The table with the files from a committed on a revision.
   * @param point        The point where to show the contextual menu.
   */
  protected void showHistoryTableContextualMenu(JTable historyTable, Point point) {
    // If we present the history for a specific file.
    int rowAtPoint = historyTable.rowAtPoint(point);
    if (rowAtPoint != -1) {
      updateTableSelection(historyTable, rowAtPoint);

      int[] selectedRows = historyTable.getSelectedRows();
      CommitCharacteristics[] cc = new CommitCharacteristics[selectedRows.length];
      for (int i = 0; i < selectedRows.length; i++) {
        HistoryCommitTableModel historyTableModel = (HistoryCommitTableModel) historyTable.getModel();
        int convertedSelectedRow = historyTable.convertRowIndexToModel(selectedRows[i]);
        CommitCharacteristics commitCharacteristics = historyTableModel.getAllCommits().get(convertedSelectedRow);
        cc[i] = commitCharacteristics;
      }

      try {
        JPopupMenu jPopupMenu = new JPopupMenu();
        contextualMenuPresenter.populateContextualActionsHistoryContext(jPopupMenu, activeFilePath, cc);

        jPopupMenu.show(historyTable, point.x, point.y);
      } catch (IOException | GitAPIException e) {
        LOGGER.error(e.getMessage(), e);
      }
  
    }
  }

  
  /**
   * Checks if a row is selected and selects it if it isn't.
   * 
   * @param table    Table.
   * @param rowIndex Row index to check.
   */
  private void updateTableSelection(JTable table, int rowIndex) {
    int[] selectedRows = table.getSelectedRows();
    boolean alreadySelected = Arrays.stream(selectedRows).anyMatch(r -> r == rowIndex);
    if (!alreadySelected) {
      table.getSelectionModel().setSelectionInterval(rowIndex, rowIndex);
    }
  }
  

  /**
   * Initializes the split with the proper font and other properties.
   * 
   * @param editorPane Editor pane to initialize.
   */
  private static void initEditorPane(JEditorPane editorPane) {
    // Forces the JEditorPane to take the font from the UI, rather than the HTML
    // document.
    editorPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
    Font font = UIManager.getDefaults().getFont("TextArea.font");
    if (font != null) {
      editorPane.setFont(font);
    }
    editorPane.setBorder(new EmptyBorder(0, UIConstants.LEFT_BORDER_SPACE, 0, 0));
    editorPane.setContentType("text/html");
    editorPane.setEditable(false);

  }
  

  /**
   * Creates the toolbar.
   * 
   * @param topPanel Parent for the toolbar.
   * @param constr   The GridBagLayout constraints
   */
  private void createAndAddToolbarToTopPanel(JPanel topPanel, GridBagConstraints constr) {
    @SuppressWarnings("java:S110")
    FilterTextField filterTemp = new FilterTextField(
        Translator.getInstance().getTranslation(Tags.TYPE_TEXT_TO_FILTER)) {
      @Override
      public void filterChanged(String text) {
        TableModel tableModel = historyTable.getModel();
        if(tableModel instanceof HistoryCommitTableModel) {
          HistoryCommitTableModel historyTableModel = (HistoryCommitTableModel) tableModel;
          graphCellRender.setShouldBePainted(text == null || text.isEmpty());
          historyTableModel.filterChanged(text);
        }
      }
    };

    // Add the Refresh action to the toolbar
    Action refreshAction = new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if(isShowing()) {
          RepoUtil.initRepoIfNeeded(true);
        }
        clearCommitsCache();
        scheduleRefreshHistory();
      }
    };
    
    constr.gridx++;
    constr.fill = GridBagConstraints.NONE;
    constr.weightx = 0;
    topPanel.add(presentHistoryStrategyButton, constr);
    
    refreshAction.putValue(Action.SMALL_ICON, Icons.getIcon(Icons.REFRESH_ICON));
    refreshAction.putValue(Action.SHORT_DESCRIPTION, Translator.getInstance().getTranslation(Tags.REFRESH));
    ToolbarButton refreshButton = new ToolbarButton(refreshAction, false);
    constr.gridx++;
    constr.fill = GridBagConstraints.NONE;
    constr.weightx = 0;
    topPanel.add(refreshButton);

    this.filter = filterTemp;
    constr.insets = new Insets(0, 7, 0, 0);
    constr.gridx++;
    constr.fill = GridBagConstraints.NONE;
    constr.weightx = 0;
    topPanel.add(filter, constr);

    add(topPanel, BorderLayout.NORTH);
  }

  
  /**
   * Shows the commit history for the entire repository.
   */
  public void showRepositoryHistory() {
    showHistory(null, true);
  }
  

  /**
   * Shows the commit history for the given file.
   * 
   * @param filePath File for which to present the commit that changed him.
   */
  public void showHistory(String filePath) {
    showHistory(filePath, false);
  }

  
  /**
   * Schedules commit history to show for the active file.
   */
  public void scheduleRefreshHistory() {
    GitOperationScheduler.getInstance().schedule(() -> showHistory(activeFilePath, true));
  }
  

  /**
   * Shows the commit history for the entire repository.
   * 
   * @param filePath File for which to present the commit that changed him.
   * @param force    <code>true</code> to recompute the history data, even if the
   *                 view already presents the history for the given resource.
   */
  private void showHistory(String filePath, boolean force) {
	 
    SwingUtilities.invokeLater(() -> updateSelectionMode(filePath));

    if (force
        // Check if we don't already present the history for this path!!!!
        || !Equaler.verifyEquals(filePath, activeFilePath)) {
      this.activeFilePath = filePath;

      try {
        // Make sure we know about the remote as well, to present data about the
        // upstream branch.
        tryFetch();

        final Repository repository = gitAccess.getRepository();
        final RenameTracker renameTracker = new RenameTracker();
        final List<CommitCharacteristics> commitCharacteristicsVector = gitAccess.getCommitsCharacteristics(
            currentStrategy, filePath, renameTracker);
        final boolean shouldRefreshHistory = checkForCommitsUpdate(commitCharacteristicsVector);
        if(shouldRefreshHistory) {
        	updateHistoryView(filePath, repository, renameTracker, commitCharacteristicsVector);    
        } 
      } catch (NoRepositorySelected | IOException e) {
        LOGGER.debug(e.getMessage(), e);
        PluginWorkspaceProvider.getPluginWorkspace()
            .showErrorMessage("Unable to present history because of: " + e.getMessage());
      }
    } 
  }

  /**
   * This method is called to refresh the history view informations.
   * 
   * @param filePath                      File for which to present the commit that changed him.
   * @param repository                    The current repository.
   * @param renameTracker                 The rename tracker for the current file path presented.
   * @param actualCommits                 The actual commits for the given repository.
   * 
   * @throws NoRepositorySelected  When no repository is loaded.
   * @throws IOException           When IO problems occur.
   */
	private void updateHistoryView(
			final String filePath, 
			final Repository repository, 
			final RenameTracker renameTracker,
			final List<CommitCharacteristics> actualCommits) 
					throws NoRepositorySelected, IOException {
		File directory = gitAccess.getWorkingCopy();
		final ObjectId branchHeadObjectId = getLocalBranchHead(actualCommits, repository);
		if(branchHeadObjectId != null) {
			graphCellRender.setLastCommitIdForCurrentBranch(branchHeadObjectId.getName());
    }
		historyLabelMessage = TRANSLATOR.getTranslation(Tags.REPOSITORY) + ": " + directory.getName() + ". "
		    + TRANSLATOR.getTranslation(Tags.BRANCH) + ": " + gitAccess.getBranchInfo().getBranchName() + ".";
		if (filePath != null) {
		  directory = new File(directory, filePath); // NOSONAR findsecbugs:PATH_TRAVERSAL_IN
		  historyLabelMessage += " " + TRANSLATOR.getTranslation(Tags.FILE) + ": " + directory.getName() + ".";
		}
    
		updateTopPanelComponentsSize();
		
		historyInfoLabel.setToolTipText(historyLabelMessage);
		historyInfoLabel.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));

		// Install selection listener.
		if (revisionDataUpdater != null) {
		  historyTable.getSelectionModel().removeListSelectionListener(revisionDataUpdater);
		}
		
		if(commitSelectionListener != null) {
			historyTable.getSelectionModel().removeListSelectionListener(commitSelectionListener);
		}

		fileHistoryPresenter.setFilePath(filePath);
		
		SwingUtilities.invokeLater(() -> {
			 final HistoryTableAffectedFilesModel dataModel = (HistoryTableAffectedFilesModel) affectedFilesTable.getModel();
		     dataModel.setFilesStatus(Collections.emptyList());
		     dataModel.setFilePathPresenter(fileHistoryPresenter);
		});
    
		HistoryAffectedFileCellRender cellRender = (HistoryAffectedFileCellRender) affectedFilesTable.getDefaultRenderer(FileStatus.class);
		cellRender.setFilePresenter(fileHistoryPresenter);
		
		commitDescriptionPane.setText("");

		hasUncommitedChanges = GitAccess.getInstance().getStatusCache().getStatus().hasUncommittedChanges();
    
		final CommitsAheadAndBehind commitsAheadAndBehind = RevCommitUtil.getCommitsAheadAndBehind(repository,
		    repository.getFullBranch());
		Optional.ofNullable(contextualMenuPresenter).ifPresent(
		    menuPresenter -> menuPresenter.setCommitsAheadAndBehind(commitsAheadAndBehind));
		// Compute the row height.
		final String currentBranchName = gitAccess.getBranchInfo().getBranchName();
		final Map<String, List<String>> localBranchesMap = gitAccess.getBranchMap(repository, ConfigConstants.CONFIG_KEY_LOCAL);
		CommitMessageTableRenderer renderer = new CommitMessageTableRenderer(repository, commitsAheadAndBehind,
		    currentBranchName, getTagMap(repository),
		    localBranchesMap,
		    gitAccess.getBranchMap(repository, ConfigConstants.CONFIG_KEY_REMOTE));
		
		final int rh = getRowHeight(renderer, getFirstCommit(actualCommits));

		final HistoryCommitTableModel historyModel = new HistoryCommitTableModel(
		    actualCommits);
		
		SwingUtilities.invokeLater(() -> { 
		  historyModel.filterChanged(filter.getText());
		  historyTable.setModel(historyModel);
		  updateHistoryTableWidths();
		  historyTable.setDefaultRenderer(PlotCommit.class, graphCellRender);
		  historyTable.setDefaultRenderer(CommitCharacteristics.class, renderer);
		  historyTable.setDefaultRenderer(Date.class, new DateTableCellRenderer(UIUtil.DATE_FORMAT_PATTERN));
		  TableColumn authorColumn = historyTable.getColumn(TRANSLATOR.getTranslation(Tags.AUTHOR));
		  authorColumn.setCellRenderer(createAuthorColumnRenderer());
		  historyTable.setRowHeight(rh);
		});

		revisionDataUpdater = new RowHistoryTableSelectionListener(getUpdateDelay(), 
			historyTable, commitDescriptionPane, actualCommits, 
			affectedFilesTable, renameTracker, fileHistoryPresenter
		);
		historyTable.getSelectionModel().addListSelectionListener(revisionDataUpdater);
		commitSelectionListener = new ListSelectionListener() {
		  
		  @Override
		  public void valueChanged(ListSelectionEvent e) {
		  	final int selectedCommit = historyTable.getSelectedRow();
			   if(historyTable.getModel() instanceof HistoryCommitTableModel) {
			    	HistoryCommitTableModel model = (HistoryCommitTableModel) historyTable.getModel();
			    	final List<CommitCharacteristics> commits = model.getAllCommits();
			    	final boolean isValidIndex = selectedCommit >= 0 && commits.size() > selectedCommit;
					  final PlotCommit<VisualLane> commit = isValidIndex ? commits.get(selectedCommit).getPlotCommit() : null;
					  if(commit != null) {
					  	selectedCommitId = commit.toObjectId();
					  }
			   }		   
		  }
		};
		historyTable.getSelectionModel().addListSelectionListener(commitSelectionListener);

		// Install hyperlink listener.
		if (hyperlinkListener != null) {
		  commitDescriptionPane.removeHyperlinkListener(hyperlinkListener);
		}
		hyperlinkListener = new HistoryHyperlinkListener(historyTable, actualCommits);
		commitDescriptionPane.addHyperlinkListener(hyperlinkListener);
		
		SwingUtilities.invokeLater(() -> { 
			if(selectedCommitId == null || !selectCommit(selectedCommitId)) {
			  // Select the local branch HEAD.
			  try {
					selectLocalBranchHead(actualCommits, repository);
				} catch (IOException ex) {
					LOGGER.error(ex.getMessage(), ex);
				}
			}
		});

		
	}

  /**
   * This method checks for commits update.
   * 
   * @param commitCharacteristicsVector The actual commits for the current repository.
   * 
   * @return <code>true</code> if the actual commits have changed compared to the cache commits.
   */
  private boolean checkForCommitsUpdate(final List<CommitCharacteristics> commitCharacteristicsVector) {
  	boolean areCommitsChanged = commitCharacteristicsVector.size() != commitsCache.size();
  	if(!areCommitsChanged) {
  		final int noOfCommits = commitsCache.size();
  		for(int i = 0; i < noOfCommits; i++) {
  			if(!Objects.equals(commitCharacteristicsVector.get(i).getCommitId(), commitsCache.get(i).getCommitId())) {
  				areCommitsChanged = true;
  				break;
  			}
  		}
  	}
  	commitsCache = commitCharacteristicsVector;
  	return areCommitsChanged;
  }

  /**
   * Select the local branch HEAD.
   * 
   * @param commitCharacteristicsVector List of the commit characteristics.
   * @param repo                        The current repository.
   * 
   * @throws IOException 
   */
  private void selectLocalBranchHead(final List<CommitCharacteristics> commitCharacteristics,
		  final Repository repo) throws IOException {
  	final ObjectId objectId = getLocalBranchHead(commitCharacteristics, repo);
  	if (objectId != null) {
	    selectCommit(objectId);
		}
  }
  
  /**
   * Get the local branch HEAD.
   * 
   * @param commitCharacteristicsVector List of the commit characteristics.
   * @param repo                        The current repository.
   * 
   * @return The ObjectId for local branch head.
   * 
   * @throws IOException 
   */
  private ObjectId getLocalBranchHead(final List<CommitCharacteristics> commitCharacteristics,
		  final Repository repo) throws IOException {
  	ObjectId toReturn = null;
	  if(!commitCharacteristics.isEmpty()) {
		  String fullBranch = repo.getFullBranch();
		  Ref branchHead = repo.exactRef(fullBranch);
		  if (branchHead != null) {
		  	toReturn = branchHead.getObjectId();
		  }
	  }
	  return toReturn;
  }
  

  /**
   * Gets the tags from the current repository.
   * 
   * @param repo Git repository.
   * 
   * @return The tags or an empty map. Never null.
   */
  private Map<String, List<String>> getTagMap(Repository repo) {
    Map<String, List<String>> tagMap = new HashMap<>();
    try {
      tagMap = gitAccess.getTagMap(repo);
    } catch (GitAPIException | IOException e) {
      LOGGER.debug(e.getMessage(), e);
    }

    return tagMap;
  }

  
  /**
   * Gets the preferred height needed to render the commit information.
   * 
   * @param renderer Commit message renderer.
   * @param ff       Commit to render.
   * 
   * @return The preferred row height.
   */
  private int getRowHeight(CommitMessageTableRenderer renderer, CommitCharacteristics ff) {
    Component tableCellRendererComponent = renderer.getTableCellRendererComponent(historyTable, ff, false, false, 1, 1);

    int rowHeight = historyTable.getRowHeight();
    if (rowHeight < tableCellRendererComponent.getPreferredSize().height) {
      rowHeight = tableCellRendererComponent.getPreferredSize().height;
    }

    return rowHeight;
  }

  
  /**
   * Gets the first actually commit from the list of commits. It ignores the
   * {@link GitAccess.UNCOMMITED_CHANGES} entry.
   * 
   * @param commitCharacteristics A list with commits from the repository.
   * 
   * @return The top actual commit.
   */
  private CommitCharacteristics getFirstCommit(final List<CommitCharacteristics> commitCharacteristics) {
    Iterator<CommitCharacteristics> iterator = commitCharacteristics.iterator();
    CommitCharacteristics first = null;
    while (first == null && iterator.hasNext()) {
      CommitCharacteristics cc = iterator.next();

      if (cc != GitAccess.UNCOMMITED_CHANGES) {
        first = cc;
      }
    }

    return first;
  }

  
  /**
   * Updates the selection model in the table to either single and multiple.
   * 
   * @param filePath An optional file to show the history for.
   */
  private void updateSelectionMode(String filePath) {
    if (filePath != null && filePath.length() > 0) {
      if(historyTable.getSelectionModel().getSelectionMode() != ListSelectionModel.MULTIPLE_INTERVAL_SELECTION) {
        historyTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
      }
    } else if(historyTable.getSelectionModel().getSelectionMode() != ListSelectionModel.SINGLE_SELECTION) {
        historyTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    }
  }
  

  /**
   * @return A cell renderer for the author column.
   */
  @SuppressWarnings("java:S110")
  private DefaultTableCellRenderer createAuthorColumnRenderer() {
    return new DefaultTableCellRenderer() {
      @Override
      public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
          int row, int column) {
        JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        String text = label.getText();
        int indexOfLT = text.indexOf(" <");
        if (indexOfLT != -1) {
          text = text.substring(0, indexOfLT);
        }
        label.setText(text);
        return label;
      }
    };
  }
  

  /**
   * Tries a fetch to update remote information.
   */
  private void tryFetch() {
    try {
      gitAccess.fetch();
    } catch (SSHPassphraseRequiredException | PrivateRepositoryException | RepositoryUnavailableException e) {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug(e.getMessage(), e);
      }
    }
  }

  
  /**
   * Coalescing for selecting the row in HistoryTable.
   */
  static final int TIMER_DELAY = 500;

  
  /**
   * @return Milliseconds. Controls how fast the satellite views are updated after
   *         a new revision is selected.
   */
  protected int getUpdateDelay() {
    return TIMER_DELAY;
  }
  

  /**
   * Distribute widths to the columns according to their content.
   */
  private void updateHistoryTableWidths() {
	int graphColWidth = 50; // NOSONAR
    int dateColWidth = 100; // NOSONAR
    int authorColWidth = 120; // NOSONAR
    int commitIdColWidth = 80; // NOSONAR

    TableColumnModel tcm = historyTable.getColumnModel();
    TableColumn column = tcm.getColumn(HistoryCommitTableModel.COMMIT_GRAPH);
    column.setPreferredWidth(graphColWidth);
    
    column = tcm.getColumn(HistoryCommitTableModel.COMMIT_MESSAGE);
    column.setPreferredWidth(historyTable.getWidth() - authorColWidth - authorColWidth - dateColWidth - graphColWidth);

    column = tcm.getColumn(HistoryCommitTableModel.DATE);
    column.setPreferredWidth(dateColWidth);

    column = tcm.getColumn(HistoryCommitTableModel.AUTHOR);
    column.setPreferredWidth(authorColWidth);

    column = tcm.getColumn(HistoryCommitTableModel.COMMIT_ABBREVIATED_ID);
    column.setPreferredWidth(commitIdColWidth);
  }

  
  /**
   * Shows the commit history for the given file.
   * 
   * @param filePath        Path of the file, relative to the working copy.
   * @param activeRevCommit The commit to select in the view.
   */
  public void showCommit(String filePath, RevCommit activeRevCommit) {
    showHistory(filePath);
    if (activeRevCommit != null) {
      ObjectId id = activeRevCommit.getId();
      selectCommit(id);
    }
  }

  
  /**
   * Selects the commit with the given ID.
   * 
   * @param id Id of the repository to select.
   * 
   * @return <code>true<code> if the commit was selected, <code>false</code> if the commit with the given id was not found in the history table.
   */
  private boolean selectCommit(ObjectId id) {
    boolean wasCommitSelected = false;
    if(historyTable.getModel() instanceof HistoryCommitTableModel) {
    	HistoryCommitTableModel model = (HistoryCommitTableModel) historyTable.getModel();
      List<CommitCharacteristics> commits = model.getAllCommits();
      for (int i = 0; i < commits.size(); i++) {
        CommitCharacteristics commitCharacteristics = commits.get(i);
        if (id.getName().equals(commitCharacteristics.getCommitId())) {
          final int selection = i;
          wasCommitSelected = true;
          selectedCommitId = id;
          SwingUtilities.invokeLater(() -> {
            historyTable.scrollRectToVisible(historyTable.getCellRect(selection, 0, true));
            updateTableSelection(historyTable, selection);
          });
          break;
        }
      }
    }
      
    return wasCommitSelected;
 }

 
  /**
   * @return the table with the affected files.
   */
  public JTable getAffectedFilesTable() {
    return affectedFilesTable;
  }
  

  /**
   * @return the history table.
   */
  public JTable getHistoryTable() {
    return historyTable;
  }

  /**
   * This method clear the previous commits cache.
   */
  private void clearCommitsCache() {
    commitsCache = Collections.emptyList();
  }
  
  /**
   * Setter for current strategy to present commits.
   * 
   * @param currentStrategy The new strategy.
   */
  @TestOnly
  public void setCurrentStrategy(final HistoryStrategy currentStrategy) {
    this.currentStrategy = currentStrategy;
  }
  
}
