package com.oxygenxml.git.service;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JTextArea;
import javax.swing.text.Highlighter.Highlight;

import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.oxygenxml.git.ProjectAndEditorPageMenuActionsUtil;
import com.oxygenxml.git.service.entities.FileStatus;
import com.oxygenxml.git.service.entities.GitChangeType;
import com.oxygenxml.git.utils.FileUtil;
import com.oxygenxml.git.utils.script.RepoGenerationScript;
import com.oxygenxml.git.view.blame.BlamePerformer;
import com.oxygenxml.git.view.history.HistoryController;
import com.oxygenxml.git.view.staging.ChangesPanel.SelectedResourcesProvider;
import com.oxygenxml.git.view.staging.actions.ShowBlameForUnstagedResourceAction;

import ro.sync.exml.workspace.api.PluginWorkspaceProvider;
import ro.sync.exml.workspace.api.editor.WSEditor;
import ro.sync.exml.workspace.api.editor.page.text.xml.WSXMLTextEditorPage;
import ro.sync.exml.workspace.api.standalone.StandalonePluginWorkspace;
import ro.sync.exml.workspace.api.util.UtilAccess;

/**
 * Scenarios for testing the blame support. 
 */
public class BlameTest extends GitTestBase{

  /**
   * Tests the blame highlights on the text page.
   * 
   * @throws Exception If it fails.
   */
  @Test
  public void testBlame() throws Exception {
    URL script = getClass().getClassLoader().getResource("scripts/blame_script.txt");
    
    File wcTree = new File("target/gen/BlameTest_testBlame");
    RepoGenerationScript.generateRepository(script, wcTree);
    
    try {
      GitAccess.getInstance().setRepositorySynchronously(wcTree.getAbsolutePath());
      
      String content = 
          "Line 1\n" + 
          "Line 2\n" + 
          "Line 3\n" + 
          "Line 4\n" + 
          "Line 5";
      HashMap<Integer, int[]> line2offsets = computeLineMappings(content);
      
      // Use Mockito to mock the Oxygen API.
      WSEditor wsEditor = Mockito.mock(WSEditor.class);
      WSXMLTextEditorPage page = Mockito.mock(WSXMLTextEditorPage.class);
      
      JTextArea textArea = new JTextArea();
      
      Mockito.when(page.getTextComponent()).thenReturn(textArea);
      Mockito.when(wsEditor.getCurrentPage()).thenReturn(page);
      
      // Methods for mapping lines to offsets and back.
      
      Mockito.when(page.getOffsetOfLineStart(Mockito.anyInt())).thenAnswer(new Answer<Integer>() {
        @Override
        public Integer answer(InvocationOnMock invocation) throws Throwable {
          Object key = invocation.getArguments()[0];
          return line2offsets.get(key)[0];
        }
      });
      
      Mockito.when(page.getOffsetOfLineEnd(Mockito.anyInt())).thenAnswer(new Answer<Integer>() {
        @Override
        public Integer answer(InvocationOnMock invocation) throws Throwable {
          return line2offsets.get(invocation.getArguments()[0])[1];
        }
      });
      
      Mockito.when(page.getLineOfOffset(Mockito.anyInt())).thenAnswer(new Answer<Integer>() {
        @Override
        public Integer answer(InvocationOnMock invocation) throws Throwable {
          int offset = (int) invocation.getArguments()[0];
          for (Integer line : line2offsets.keySet()) {
            int[] is = line2offsets.get(line);
            if (is[0] <= offset && offset < is[1]) {
              return line;
            }
          }
          
          return -1;
        }
      });
      
      // Intercept history view requests.
      final List<RevCommit> commits = new ArrayList<>(); 
      HistoryController historyController = Mockito.mock(HistoryController.class);
      Mockito.doAnswer(new Answer<Void>() {
        @Override
        public Void answer(InvocationOnMock invocation) throws Throwable {
          RevCommit rev = (RevCommit) invocation.getArguments()[1];
          commits.add(rev);
          
          return null;
        }
      }).when(historyController).showCommit(Mockito.anyString(), Mockito.any());
      
      Mockito.when(historyController.isHistoryShowing()).thenReturn(Boolean.TRUE);
      
      textArea.setText(content);
      flushAWT();
      
      // Execute blame.
      new BlamePerformer().doit(
          GitAccess.getInstance().getRepository(), "file1.txt", wsEditor, historyController);
      
      Highlight[] highlights = textArea.getHighlighter().getHighlights();
      assertEquals(5, highlights.length);
      
      String expected = dumpOffsetMap(line2offsets);
      String actual = dumpHighlights(highlights);
      
      assertEquals(expected, actual);
      
      // Activate each highlight and collect the requests done to the historyview.
      for (Highlight highlight : highlights) {
        textArea.setCaretPosition(highlight.getStartOffset());
        // Wait for the thread that presents the revision.
        sleep(400);
        flushAWT();
      }
      
      // Asserts the revisions activated in the history view.
      assertEquals(
          "First commit.\n" + 
          "Change 2\n" + 
          "Change 3\n" + 
          "Change 4\n" + 
          "Change 5\n" + 
          "", dumpCommits(commits));

    } finally {
      GitAccess.getInstance().closeRepo();
      
      FileUtil.deleteRecursivelly(wcTree);
    }
  }
  
  /**
   * <p><b>Description:</b> ask save editor before blame if editor modified.</p>
   * <p><b>Bug ID:</b> EXM-45008</p>
   * 
   * @author sorin_carbunaru
   * 
   * @throws Exception 
   */
  @Test
  public void testBlameFromProjectAndEditorPage_askUserSaveEditor() throws Exception {
    URL script = getClass().getClassLoader().getResource("scripts/blame_script.txt");
    
    File wcTree = new File("target/gen/BlameTest_testBlame");
    RepoGenerationScript.generateRepository(script, wcTree);
    
    try {
      StandalonePluginWorkspace pluginWSMock = Mockito.mock(StandalonePluginWorkspace.class);
      PluginWorkspaceProvider.setPluginWorkspace(pluginWSMock);
      
      Mockito.when(pluginWSMock.getParentFrame()).thenReturn(new JFrame());
      Mockito.when(pluginWSMock.open(Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(true);
      
      // Mock util access
      UtilAccess utilAcces = Mockito.mock(UtilAccess.class);
      Mockito.when(utilAcces.correctURL(Mockito.anyString())).thenReturn("");
      Mockito.when(pluginWSMock.getUtilAccess()).thenReturn(utilAcces);
      
      // Mock confirmation
      boolean confirmDialogShown[] = new boolean[1];
      Mockito.doAnswer(new Answer<Integer>() {
        @Override
        public Integer answer(InvocationOnMock invocation) throws Throwable {
          confirmDialogShown[0] = true;
          return 0;
        }
      }).when(pluginWSMock).showConfirmDialog(
          Mockito.anyString(),
          Mockito.anyString(),
          Mockito.any(),
          Mockito.any());
      
      GitAccess.getInstance().setRepositorySynchronously(wcTree.getAbsolutePath());
      
      String content = 
          "Line 1\n" + 
          "Line 2\n" + 
          "Line 3\n" + 
          "Line 4\n" + 
          "Line 5";
      HashMap<Integer, int[]> line2offsets = computeLineMappings(content);
      
      // Mock editor and page
      boolean editorSaved[] = new boolean[1];
      WSEditor wsEditor = Mockito.mock(WSEditor.class);
      Mockito.when(wsEditor.isModified()).thenReturn(true);
      Mockito.when(pluginWSMock.getEditorAccess(Mockito.any(), Mockito.anyInt())).thenReturn(wsEditor);
      Mockito.doAnswer(new Answer<Void>() {
        @Override
        public Void answer(InvocationOnMock invocation) throws Throwable {
          editorSaved[0] = true;
          return null;
        }
      }).when(wsEditor).save();
      Mockito.doNothing().when(wsEditor).changePage(Mockito.anyString());
      WSXMLTextEditorPage page = Mockito.mock(WSXMLTextEditorPage.class);
      JTextArea textArea = new JTextArea();
      Mockito.when(page.getTextComponent()).thenReturn(textArea);
      Mockito.when(wsEditor.getCurrentPage()).thenReturn(page);
      
      // Methods for mapping lines to offsets and back.
      Mockito.when(page.getOffsetOfLineStart(Mockito.anyInt())).thenAnswer(new Answer<Integer>() {
        @Override
        public Integer answer(InvocationOnMock invocation) throws Throwable {
          Object key = invocation.getArguments()[0];
          return line2offsets.get(key)[0];
        }
      });
      Mockito.when(page.getOffsetOfLineEnd(Mockito.anyInt())).thenAnswer(new Answer<Integer>() {
        @Override
        public Integer answer(InvocationOnMock invocation) throws Throwable {
          return line2offsets.get(invocation.getArguments()[0])[1];
        }
      });
      Mockito.when(page.getLineOfOffset(Mockito.anyInt())).thenAnswer(new Answer<Integer>() {
        @Override
        public Integer answer(InvocationOnMock invocation) throws Throwable {
          int offset = (int) invocation.getArguments()[0];
          for (Integer line : line2offsets.keySet()) {
            int[] is = line2offsets.get(line);
            if (is[0] <= offset && offset < is[1]) {
              return line;
            }
          }
          return -1;
        }
      });
      
      // Intercept history view requests.
      final List<RevCommit> commits = new ArrayList<>(); 
      HistoryController historyController = Mockito.mock(HistoryController.class);
      Mockito.doAnswer(new Answer<Void>() {
        @Override
        public Void answer(InvocationOnMock invocation) throws Throwable {
          RevCommit rev = (RevCommit) invocation.getArguments()[1];
          commits.add(rev);
          
          return null;
        }
      }).when(historyController).showCommit(Mockito.anyString(), Mockito.any());
      
      Mockito.when(historyController.isHistoryShowing()).thenReturn(Boolean.TRUE);
      
      textArea.setText(content);
      flushAWT();
      
      // Execute blame.
      ProjectAndEditorPageMenuActionsUtil.showBlame(
          new File(wcTree, "file1.txt"),
          historyController,
          Collections.emptyList());
      
      assertTrue(confirmDialogShown[0]);
      
      Highlight[] highlights = textArea.getHighlighter().getHighlights();
      assertEquals(5, highlights.length);
      
      String expected = dumpOffsetMap(line2offsets);
      String actual = dumpHighlights(highlights);
      
      assertEquals(expected, actual);
      
      // Activate each highlight and collect the requests done to the history view.
      for (Highlight highlight : highlights) {
        textArea.setCaretPosition(highlight.getStartOffset());
        // Wait for the thread that presents the revision.
        sleep(400);
        flushAWT();
      }
      
      // Asserts the revisions activated in the history view.
      assertEquals(
          "First commit.\n" + 
          "Change 2\n" + 
          "Change 3\n" + 
          "Change 4\n" + 
          "Change 5\n" + 
          "", dumpCommits(commits));

    } finally {
      GitAccess.getInstance().closeRepo();
      
      FileUtil.deleteRecursivelly(wcTree);
    }
  }
  
  /**
   * <p><b>Description:</b> Show blame from staging area. Ask save editor before blame if editor modified.</p>
   * <p><b>Bug ID:</b> EXM-45008</p>
   * 
   * @author sorin_carbunaru
   * 
   * @throws Exception 
   */
  @Test
  public void testShowBlameFromStagingArea_askUserConfirmation() throws Exception {
    URL script = getClass().getClassLoader().getResource("scripts/blame_script.txt");

    File wcTree = new File("target/gen/BlameTest_testBlame");
    RepoGenerationScript.generateRepository(script, wcTree);

    try {
      GitAccess.getInstance().setRepositorySynchronously(wcTree.getAbsolutePath());
      
      String content = 
          "Line 1\n" + 
          "Line 2\n" + 
          "Line 3\n" + 
          "Line 4\n" + 
          "Line 5";
      HashMap<Integer, int[]> line2offsets = computeLineMappings(content);

      // Mock plugin workspace
      StandalonePluginWorkspace pluginWSMock = Mockito.mock(StandalonePluginWorkspace.class);
      PluginWorkspaceProvider.setPluginWorkspace(pluginWSMock);
      
      // Mock "open()"
      Mockito.when(pluginWSMock.open(Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(true);
      
      // Mock util access
      UtilAccess utilAcces = Mockito.mock(UtilAccess.class);
      Mockito.when(utilAcces.correctURL(Mockito.anyString())).thenReturn("");
      Mockito.when(pluginWSMock.getUtilAccess()).thenReturn(utilAcces);

      // Mock confirmation
      boolean confirmDialogShown[] = new boolean[1];
      Mockito.doAnswer(new Answer<Integer>() {
        @Override
        public Integer answer(InvocationOnMock invocation) throws Throwable {
          confirmDialogShown[0] = true;
          return 0;
        }
      }).when(pluginWSMock).showConfirmDialog(
          Mockito.anyString(),
          Mockito.anyString(),
          Mockito.any(),
          Mockito.any());

      // Mock editor and page
      boolean editorSaved[] = new boolean[1];
      WSEditor wsEditor = Mockito.mock(WSEditor.class);
      Mockito.when(wsEditor.isModified()).thenReturn(true);
      Mockito.when(pluginWSMock.getEditorAccess(Mockito.any(), Mockito.anyInt())).thenReturn(wsEditor);
      Mockito.doAnswer(new Answer<Void>() {
        @Override
        public Void answer(InvocationOnMock invocation) throws Throwable {
          editorSaved[0] = true;
          return null;
        }
      }).when(wsEditor).save();
      Mockito.doNothing().when(wsEditor).changePage(Mockito.anyString());
      WSXMLTextEditorPage page = Mockito.mock(WSXMLTextEditorPage.class);
      JTextArea textArea = new JTextArea();
      Mockito.when(page.getTextComponent()).thenReturn(textArea);
      Mockito.when(wsEditor.getCurrentPage()).thenReturn(page);

      // Methods for mapping lines to offsets and back.
      
      Mockito.when(page.getOffsetOfLineStart(Mockito.anyInt())).thenAnswer(new Answer<Integer>() {
        @Override
        public Integer answer(InvocationOnMock invocation) throws Throwable {
          Object key = invocation.getArguments()[0];
          return line2offsets.get(key)[0];
        }
      });
      Mockito.when(page.getOffsetOfLineEnd(Mockito.anyInt())).thenAnswer(new Answer<Integer>() {
        @Override
        public Integer answer(InvocationOnMock invocation) throws Throwable {
          return line2offsets.get(invocation.getArguments()[0])[1];
        }
      });
      Mockito.when(page.getLineOfOffset(Mockito.anyInt())).thenAnswer(new Answer<Integer>() {
        @Override
        public Integer answer(InvocationOnMock invocation) throws Throwable {
          int offset = (int) invocation.getArguments()[0];
          for (Integer line : line2offsets.keySet()) {
            int[] is = line2offsets.get(line);
            if (is[0] <= offset && offset < is[1]) {
              return line;
            }
          }
          
          return -1;
        }
      });
      
      // Intercept history view requests.
      final List<RevCommit> commits = new ArrayList<>(); 
      HistoryController historyController = Mockito.mock(HistoryController.class);
      Mockito.doAnswer(new Answer<Void>() {
        @Override
        public Void answer(InvocationOnMock invocation) throws Throwable {
          RevCommit rev = (RevCommit) invocation.getArguments()[1];
          commits.add(rev);
          
          return null;
        }
      }).when(historyController).showCommit(Mockito.anyString(), Mockito.any());
      
      Mockito.when(historyController.isHistoryShowing()).thenReturn(Boolean.TRUE);
      
      textArea.setText(content);
      flushAWT();

      // >>> Show blame <<<
      ShowBlameForUnstagedResourceAction showBlameAction = 
          new ShowBlameForUnstagedResourceAction(
              historyController,
              new SelectedResourcesProvider() {
                @Override
                public List<FileStatus> getOnlySelectedLeaves() {
                  return null;
                }
                @Override
                public List<FileStatus> getAllSelectedResources() {
                  return Arrays.asList(new FileStatus(GitChangeType.MODIFIED, "file1.txt"));
                }
              });
      showBlameAction.actionPerformed(null);

      assertTrue(confirmDialogShown[0]);
      assertTrue(editorSaved[0]);
      
      Highlight[] highlights = textArea.getHighlighter().getHighlights();
      assertEquals(5, highlights.length);
      
      String expected = dumpOffsetMap(line2offsets);
      String actual = dumpHighlights(highlights);
      
      assertEquals(expected, actual);
    } finally {
      GitAccess.getInstance().closeRepo();
      FileUtil.deleteRecursivelly(wcTree);
    }
  }

  /**
   * Dumps commit messages.
   * 
   * @param commits 
   * @return
   */
  private String dumpCommits(List<RevCommit> commits) {
    StringBuilder b = new StringBuilder();
    for (RevCommit revCommit : commits) {
      b.append(revCommit.getFullMessage()).append("\n");
    }
    
    return b.toString();
  }

  /**
   * Dumps offsets associated with these highlights.
   * 
   * @param highlights
   * @return
   */
  private String dumpHighlights(Highlight[] highlights) {
    StringBuilder b = new StringBuilder();
    
    for (Highlight highlight : highlights) {
      b.append("[").append(highlight.getStartOffset()).append(", ").append(highlight.getEndOffset()).append("]");
    }
    
    return b.toString();
  }

  /**
   * Dumps the detected offsets for content inside the text area.
   * 
   * @param line2offsets
   * @return
   */
  private String dumpOffsetMap(HashMap<Integer, int[]> line2offsets) {
    StringBuilder b = new StringBuilder();
    
    for (Integer integer : line2offsets.keySet()) {
      int[] is = line2offsets.get(integer);
      b.append("[").append(is[0]).append(", ").append(is[1]).append("]");
    }
    
    return b.toString();
  }

  /**
   * Computes the line start/end offsets for the given content.
   * 
   * @param content 
   * @return
   */
  private HashMap<Integer, int[]> computeLineMappings(String content) {
    HashMap<Integer, int[]> offsets = new HashMap<>();
    int line = 1;
    int startOffset = 0;
    for (int i = 0; i < content.length(); i++) {
      char charAt = content.charAt(i);
      if (charAt == '\n') {
        int end = i;
        offsets.put(line, new int[] {startOffset, end});
        startOffset = i + 1;
        line++;
      }
    }
    
    // If the content ends in a new line, add another entry for it.
    if (startOffset< content.length() - 1) {
      offsets.put(line, new int[] {startOffset, content.length()});
    }
    
    return offsets;
  }
}
