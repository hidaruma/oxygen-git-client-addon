package com.oxygenxml.git.view;

import java.awt.Component;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JFrame;

import org.apache.log4j.Logger;
import org.eclipse.jgit.errors.NoWorkTreeException;

import com.oxygenxml.git.options.OptionsManager;
import com.oxygenxml.git.protocol.GitRevisionURLHandler;
import com.oxygenxml.git.protocol.VersionIdentifier;
import com.oxygenxml.git.service.GitAccess;
import com.oxygenxml.git.service.NoRepositorySelected;
import com.oxygenxml.git.service.entities.FileStatus;
import com.oxygenxml.git.service.entities.GitChangeType;
import com.oxygenxml.git.translator.Tags;
import com.oxygenxml.git.translator.Translator;
import com.oxygenxml.git.utils.FileHelper;
import com.oxygenxml.git.view.event.ChangeEvent;
import com.oxygenxml.git.view.event.StageController;
import com.oxygenxml.git.view.event.StageState;

import ro.sync.exml.workspace.api.PluginWorkspaceProvider;
import ro.sync.exml.workspace.api.standalone.StandalonePluginWorkspace;

/**
 * Displays the diff depending on the what change type the file is.
 * 
 * @author Beniamin Savu
 *
 */
public class DiffPresenter {

	/**
	 * Logger for logging.
	 */
	private static Logger logger = Logger.getLogger(DiffPresenter.class);

	/**
	 * The file on which the diffPresenter works
	 */
	private FileStatus file;

	/**
	 * The frame of the oxygen's diff
	 */
	private Component diffFrame;

	/**
	 * Controller used for staging and unstaging
	 */
	private StageController stageController;

	/**
	 * The translator used for the messages that are displayed to the user
	 */
	private Translator translator;

	public DiffPresenter(FileStatus file, StageController stageController, Translator translator) {
		this.stageController = stageController;
		this.file = file;
		this.translator = translator;
	}

	/**
	 * Perform different actions depending on the file change type. If the file is
	 * a conflict file then a 3-way diff is presented. If the file is a modified
	 * one then a 2-way diff is presented. And if a file is added then the file is
	 * opened
	 * 
	 */
	public void showDiff() {
	  try {
		GitChangeType changeType = file.getChangeType();
    switch (changeType) {
		case CONFLICT:
			conflictDiff();
			break;
		case CHANGED:
		  diffIndexWithHead();
		  break;
		case MODIFIED:
			diffView(changeType);
			break;
		case ADD:
		case UNTRACKED:
			openFile();
			break;
		case SUBMODULE:
			submoduleDiff();
			break;
		default:
			break;
		}
	  } catch (Exception ex) {
	    PluginWorkspaceProvider.getPluginWorkspace().showErrorMessage(ex.getMessage());
	    
	    if (logger.isDebugEnabled()) {
	      logger.debug(ex, ex);
	    }
	  }
	}

	private void submoduleDiff() {
		GitAccess.getInstance().submoduleCompare(file.getFileLocation(), true);
		try {
			URL currentSubmoduleCommit = GitRevisionURLHandler.encodeURL(VersionIdentifier.CURRENT_SUBMODULE, file.getFileLocation());
			URL previouslySubmoduleCommit = GitRevisionURLHandler.encodeURL(VersionIdentifier.PREVIOUSLY_SUBMODULE,
					file.getFileLocation());
			((StandalonePluginWorkspace) PluginWorkspaceProvider.getPluginWorkspace())
					.openDiffFilesApplication(currentSubmoduleCommit, previouslySubmoduleCommit);
		} catch (MalformedURLException e) {
			if (logger.isDebugEnabled()) {
				logger.debug(e, e);
			}
		}
	}

	/**
	 * Opens the file in the Oxygen
	 * 
	 * @throws NoRepositorySelected 
	 * @throws NoWorkTreeException 
	 * @throws MalformedURLException 
	 */
	public void openFile() throws NoWorkTreeException, NoRepositorySelected, MalformedURLException {
	  URL fileURL = null;
	  GitChangeType changeType = file.getChangeType();
	  if (changeType == GitChangeType.ADD) {
	      fileURL = GitRevisionURLHandler.encodeURL(VersionIdentifier.INDEX_OR_LAST_COMMIT, file.getFileLocation());
	  } else {
	    fileURL = FileHelper.getFileURL(file.getFileLocation());  
	  }
	  
    ((StandalonePluginWorkspace) PluginWorkspaceProvider.getPluginWorkspace())
				.open(fileURL);
	}

	/**
	 * Presents a 2-way diff
	 * 
	 * @param changeType The type of change.
	 *  
	 * @throws NoRepositorySelected 
	 * @throws NoWorkTreeException 
	 */
	private void diffView(GitChangeType changeType) throws NoWorkTreeException, NoRepositorySelected {
	  // The local (WC) version.
		URL fileURL = FileHelper.getFileURL(file.getFileLocation());
		URL lastCommitedFileURL = null;

		try {
			lastCommitedFileURL = GitRevisionURLHandler.encodeURL(VersionIdentifier.INDEX_OR_LAST_COMMIT, file.getFileLocation());
		} catch (MalformedURLException e1) {
			if (logger.isDebugEnabled()) {
				logger.debug(e1, e1);
			}
		}
		
		((StandalonePluginWorkspace) PluginWorkspaceProvider.getPluginWorkspace()).openDiffFilesApplication(
		    fileURL, lastCommitedFileURL);

	}
	
  /**
   * Presents a 2-way diff
   * 
   * @param changeType The type of change.
   *  
   * @throws NoRepositorySelected 
   * @throws NoWorkTreeException 
   */
  private void diffIndexWithHead() throws NoWorkTreeException, NoRepositorySelected {    
    // The local (WC) version.
    URL leftSideURL = FileHelper.getFileURL(file.getFileLocation());
    URL rightSideURL = null;

    try {
      leftSideURL  = GitRevisionURLHandler.encodeURL(VersionIdentifier.INDEX_OR_LAST_COMMIT, file.getFileLocation());
      
      rightSideURL = GitRevisionURLHandler.encodeURL(VersionIdentifier.LAST_COMMIT, file.getFileLocation());
    } catch (MalformedURLException e1) {
      if (logger.isDebugEnabled()) {
        logger.debug(e1, e1);
      }
    }

    ((StandalonePluginWorkspace) PluginWorkspaceProvider.getPluginWorkspace()).openDiffFilesApplication(leftSideURL,
        rightSideURL);

  }

	/**
	 * Presents a 3-way diff
	 */
	private void conflictDiff() {
		try {
			// builds the URL for the files
			URL local = GitRevisionURLHandler.encodeURL(VersionIdentifier.MINE, file.getFileLocation());
			URL remote = GitRevisionURLHandler.encodeURL(VersionIdentifier.THEIRS, file.getFileLocation());
			URL base = GitRevisionURLHandler.encodeURL(VersionIdentifier.BASE, file.getFileLocation());

			String selectedRepository = OptionsManager.getInstance().getSelectedRepository();
			final File localCopy = new File(selectedRepository, file.getFileLocation());

			// time stamp used for detecting if the file was changed in the diff view
			final long diffStartedTimeStamp = localCopy.lastModified();

			try {
				// checks whether a base commit exists or not. If not, then the a 2-way
				// diff is presented
				if (GitAccess.getInstance().getLoaderFrom(GitAccess.getInstance().getBaseCommit(),
						file.getFileLocation()) == null) {
					diffFrame = (JFrame) ((StandalonePluginWorkspace) PluginWorkspaceProvider.getPluginWorkspace())
							.openDiffFilesApplication(local, remote);
				} else {
					diffFrame = (JFrame) ((StandalonePluginWorkspace) PluginWorkspaceProvider.getPluginWorkspace())
							.openDiffFilesApplication(local, remote, base);
				}
			} catch (IOException e1) {
				if (logger.isDebugEnabled()) {
					logger.debug(e1, e1);
				}
			}
			// checks if the file in conflict has been resolved or not after the diff
			// view was closed
			diffFrame.addComponentListener(new ComponentAdapter() {
				@Override
				public void componentHidden(ComponentEvent e) {
					long diffClosedTimeStamp = localCopy.lastModified();

					if (diffClosedTimeStamp == diffStartedTimeStamp) {

						String[] options = new String[] { "   Yes   ", "   No   " };
						int[] optonsId = new int[] { 0, 1 };
						int response = ((StandalonePluginWorkspace) PluginWorkspaceProvider.getPluginWorkspace()).showConfirmDialog(
								translator.getTraslation(Tags.CHECK_IF_CONFLICT_RESOLVED_TITLE),
								translator.getTraslation(Tags.CHECK_IF_CONFLICT_RESOLVED), options, optonsId);
						if (response == 0) {
							GitAccess.getInstance().remove(file);
							GitAccess.getInstance().restoreLastCommitFile(file.getFileLocation());
							GitAccess.getInstance().add(file);
							StageState oldState = StageState.UNSTAGED;
							StageState newState = StageState.DISCARD;
							List<FileStatus> files = new ArrayList<FileStatus>();
							files.add(file);
							ChangeEvent changeEvent = new ChangeEvent(newState, oldState, files);
							stageController.stateChanged(changeEvent);
						}
					} else {
						file.setChangeType(GitChangeType.MODIFIED);
						StageState oldState = StageState.UNSTAGED;
						StageState newState = StageState.STAGED;
						List<FileStatus> files = new ArrayList<FileStatus>();
						files.add(file);
						ChangeEvent changeEvent = new ChangeEvent(newState, oldState, files);
						stageController.stateChanged(changeEvent);
					}
					diffFrame.removeComponentListener(this);
				}
			});

		} catch (MalformedURLException e1) {
			if (logger.isDebugEnabled()) {
				logger.debug(e1, e1);
			}
		}
	}

	public void setFile(FileStatus fileStatus) {
		this.file = fileStatus;
	}
}
