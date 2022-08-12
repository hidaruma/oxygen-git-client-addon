package com.oxygenxml.git.view;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import org.awaitility.Awaitility;
import org.awaitility.Duration;
import org.mockito.Mockito;

import com.oxygenxml.git.options.OptionsManager;
import com.oxygenxml.git.service.GitAccess;
import com.oxygenxml.git.service.GitControllerBase;
import com.oxygenxml.git.service.GitTestBase;
import com.oxygenxml.git.translator.Tags;
import com.oxygenxml.git.translator.Translator;
import com.oxygenxml.git.view.event.GitController;
import com.oxygenxml.git.view.staging.OpenProjectDialog;
import com.oxygenxml.git.view.staging.WorkingCopySelectionPanel;

import ro.sync.basic.io.FileSystemUtil;
import ro.sync.exml.workspace.api.PluginWorkspaceProvider;
import ro.sync.exml.workspace.api.standalone.StandalonePluginWorkspace;
import ro.sync.exml.workspace.api.standalone.project.ProjectController;

/**
 * Contains tests for xpr files detection.
 * 
 * @author alex_smarandache
 *
 */
public class WorkingCopyXprDetectionTest extends GitTestBase {

  /**
   * <p><b>Description:</b> Tests auto opening an .xpr file project when option is enabled.</p>
   * 
   * <p><b>Bug ID:</b> EXM-46694</p>
   *
   * @author alex_smarandache
   * 
   * @throws Exception When problems occur.
   */
  public void testAutoOpenXPR() throws Exception {
    OptionsManager.getInstance().setDetectAndOpenXprFiles(true);
    
    final String localRepository1 = "localrepo1"; 
    final String[] currentProject = new String[1];
    final String localRepository2 = "localrepo2";
    final String dir = "target/test-resources/WorkingCopyXprDetection";
    
    final StandalonePluginWorkspace pluginWS = Mockito.mock(StandalonePluginWorkspace.class);
    Mockito.when(pluginWS.open(Mockito.any(URL.class))).thenAnswer(invocation -> {
      currentProject[0] = new File(((URL)invocation.getArgument(0)).toURI()).getPath();
      return true;
    });
    
    final JFrame frame = new JFrame();
    Mockito.when(pluginWS.getParentFrame()).thenReturn(frame);
    
    final List<File> files = new ArrayList<>();
    createResorces(dir, localRepository1, localRepository2, files);
    assertEquals(7, files.size());
    
    final int mainDirectoryIndex = 0;
    final int localRepo2Index = 2;
    final int xpr1Index = 3;
    final int xpr2Index = 4;
   
    final ProjectController projectManager = Mockito.mock(ProjectController.class);
    Mockito.when(projectManager.getCurrentProjectURL()).thenReturn(files.get(localRepo2Index).toURI().toURL());
    Mockito.when(pluginWS.getProjectManager()).thenReturn(projectManager);
    PluginWorkspaceProvider.setPluginWorkspace(pluginWS);
    
    try {
      GitControllerBase mock = Mockito.mock(GitControllerBase.class);
      GitAccess instance = GitAccess.getInstance();
      Mockito.when(mock.getGitAccess()).thenReturn(instance);
      WorkingCopySelectionPanel wcPanel = new WorkingCopySelectionPanel(new GitController(instance), true);
      frame.getContentPane().add(wcPanel);
      frame.pack();

      SwingUtilities.invokeAndWait(() -> frame.setVisible(true));
      sleep(100);

      instance.createNewRepository(new File(dir, localRepository1).getPath());
      Awaitility.await().atMost(Duration.FIVE_HUNDRED_MILLISECONDS).until(() ->
        files.get(xpr1Index).getAbsolutePath().equals(currentProject[0]));

   
      instance.createNewRepository(new File(dir, localRepository2).getPath());
      OpenProjectDialog dialog = (OpenProjectDialog) findDialog(
          Translator.getInstance().getTranslation(Tags.DETECT_AND_OPEN_XPR_FILES_DIALOG_TITLE));
      assertNotNull(dialog);
      assertEquals(3, dialog.getFilesCombo().getItemCount());
      
      // assert that the repository was not changed without confirmation.
      Awaitility.await().atMost(Duration.FIVE_HUNDRED_MILLISECONDS).until(() ->
        files.get(xpr1Index).getAbsolutePath().equals(currentProject[0]));

      dialog.getOkButton().doClick();
      Awaitility.await().atMost(Duration.FIVE_HUNDRED_MILLISECONDS).until(() ->
        files.get(xpr2Index).getAbsolutePath().equals(currentProject[0]));
      
    } finally {
      FileSystemUtil.deleteRecursivelly(files.get(mainDirectoryIndex));
      frame.setVisible(false);
      frame.dispose();
      OptionsManager.getInstance().setDetectAndOpenXprFiles(false);
    }
  }

  /**
   * Creates the following resources: a directory with two directories inside it. The first directory contains
   * a xpr file and the second directory contains two xpr files.
   *     
   * @param mainDirectory           The main directory.
   * @param localRepository1        The first local repository folder.
   * @param localRepository2        The second local repository folder.
   * @param files                   The list to collect created files.
   * 
   * @throws URISyntaxException
   * @throws IOException
   */
  private void createResorces(
      final String mainDirectory, 
      final String localRepository1, 
      final String localRepository2,
      final List<File> files) throws URISyntaxException, IOException {
    final File mainDir = new File(mainDirectory);
    if(!mainDir.exists()) {
      mainDir.mkdirs();
    }
    files.add(mainDir);
    final File localRepositoryFile1 = new File(mainDir, localRepository1);
    final File localRepositoryFile2 = new File(mainDir, localRepository2);
    localRepositoryFile1.mkdir();
    localRepositoryFile2.mkdir();
    files.add(localRepositoryFile1);
    files.add(localRepositoryFile2);
    final File xpr1 = new File(localRepositoryFile1, "file1.xpr");
    xpr1.createNewFile();
    files.add(xpr1);
    
    IntStream.range(2, 5).forEachOrdered(index -> {
      final File file = new File(localRepositoryFile2, "file" + index + ".xpr");
      try {
        file.createNewFile();
      } catch (IOException e) {
        e.printStackTrace();
      }
      files.add(file);
    });

  }

}
