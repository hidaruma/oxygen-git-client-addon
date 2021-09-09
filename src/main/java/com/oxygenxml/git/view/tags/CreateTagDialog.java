package com.oxygenxml.git.view.tags;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;

import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;

import com.oxygenxml.git.constants.UIConstants;
import com.oxygenxml.git.service.GitAccess;
import com.oxygenxml.git.service.NoRepositorySelected;
import com.oxygenxml.git.translator.Tags;
import com.oxygenxml.git.translator.Translator;
import com.oxygenxml.git.view.util.CoalescingDocumentListener;
import com.oxygenxml.git.view.util.UIUtil;

import ro.sync.exml.workspace.api.PluginWorkspaceProvider;
import ro.sync.exml.workspace.api.standalone.ui.OKCancelDialog;
import ro.sync.exml.workspace.api.standalone.ui.TextField;

/**
 * Dialog used to create a new local tag 
 * 
 * @author gabriel_nedianu
 *
 */
public class CreateTagDialog extends OKCancelDialog {
  /**
   * Logger for logging.
   */
  private static final Logger logger = LogManager.getLogger(CreateTagDialog.class.getName());
  /**
   * Translator.
   */
  private static final Translator translator = Translator.getInstance();
  /**
   * A text field for the tag title.
   */
  private JTextField tagTitleField;
  /**
   * A text field for the tag message.
   */
  private JTextArea tagMessageField;
  /**
   * The error message area.
   */
  private JTextArea errorMessageTextArea;
  /**
   * Check box to choose whether or not to checkout the branch when 
   * creating it from a local branch or commit.
   */
  private JCheckBox pushTagCheckBox;

  /**
   * Public constructor.
   * 
   * @param title            The title of the dialog.
   */
  public CreateTagDialog() {
    super(PluginWorkspaceProvider.getPluginWorkspace() != null
        ? (JFrame) PluginWorkspaceProvider.getPluginWorkspace().getParentFrame(): null,
            translator.getTranslation(Tags.CREATE_TAG_COMMIT_TITLE),
            true);

    // Create GUI
    JPanel panel = new JPanel(new GridBagLayout());
    createGUI(panel);
    getOkButton().setText(translator.getTranslation(Tags.CREATE));
    getContentPane().add(panel);
    setResizable(true);
    pack();
    
    // Enable or disable the OK button based on the user input
    updateUI("");
    Runnable updateRunnable = () -> updateUI(tagTitleField.getText());
    
    tagTitleField.getDocument().addDocumentListener(new CoalescingDocumentListener(updateRunnable));
    tagTitleField.requestFocus();

    if (PluginWorkspaceProvider.getPluginWorkspace() != null) {
      setLocationRelativeTo((JFrame) PluginWorkspaceProvider.getPluginWorkspace().getParentFrame());
    }
    setMinimumSize(new Dimension(UIConstants.TAG_CREATE_DIALOG_PREF_WIDTH, UIConstants.TAG_CREATE_DIALOG_PREF_HEIGHT));
    setVisible(true);
  }

  /**
   * Adds the elements to the user interface/
   * 
   * @param panel         The panel in which the components are added.
   */
  private void createGUI(JPanel panel) {
      
    int topInset = UIConstants.COMPONENT_TOP_PADDING;
    int leftInset = 5;
    
    // Tag title label.
    JLabel label = new JLabel(translator.getTranslation(Tags.CREATE_TAG_TITLE_LABEL) + ":");
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.weightx = 0;
    gbc.weighty = 0;
    gbc.fill = GridBagConstraints.NONE;
    gbc.anchor = GridBagConstraints.BASELINE_LEADING;
    gbc.insets = new Insets(0, 0, 0, 0);
    panel.add(label, gbc);

    // Tag title field.
    tagTitleField = new TextField();
    tagTitleField.setPreferredSize(new Dimension(200, tagTitleField.getPreferredSize().height));
    gbc.gridx ++;
    gbc.weightx = 1;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.anchor = GridBagConstraints.BASELINE;
    gbc.insets = new Insets(0, leftInset, 0, 0);
    panel.add(tagTitleField, gbc);

    label.setLabelFor(tagTitleField);

    // Error message area
    errorMessageTextArea = UIUtil.createMessageArea("");
    errorMessageTextArea.setForeground(Color.RED);
    Font font = errorMessageTextArea.getFont();
    errorMessageTextArea.setFont(font.deriveFont(font.getSize() - 1.0f));
    gbc.gridy ++;
    gbc.gridwidth = 1;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.insets = new Insets(topInset, leftInset, 0, 0);
    panel.add(errorMessageTextArea, gbc);

    // Tag message label.
    JLabel messageLabel = new JLabel(translator.getTranslation(Tags.CREATE_TAG_MESSAGE_LABEL) + ":");
    gbc.gridx = 0;
    gbc.gridy++;
    gbc.anchor = GridBagConstraints.BASELINE_LEADING;
    gbc.insets = new Insets(topInset, 0, 0, 5);      
    panel.add(messageLabel, gbc);

    // Tag message field.
    tagMessageField = new JTextArea();
    JScrollPane tagMessageScrollPane = new JScrollPane(tagMessageField);
    tagMessageScrollPane.setPreferredSize(new Dimension(200, 2* tagMessageField.getPreferredSize().height));
    tagMessageField.selectAll();
    gbc.gridx ++;
    gbc.weightx = 1;
    gbc.weighty = 1; 
    gbc.fill = GridBagConstraints.BOTH;
    gbc.anchor = GridBagConstraints.BASELINE;
    gbc.insets = new Insets(topInset, leftInset, 0, 0);
    panel.add(tagMessageScrollPane, gbc);

    label.setLabelFor(tagMessageField);

    // "Push tag" check box
    pushTagCheckBox = new JCheckBox(translator.getTranslation(Tags.CREATE_TAG_PUSH_CHECKBOX));
    pushTagCheckBox.setSelected(false);
    gbc.gridx = 0;
    gbc.gridy ++;
    gbc.gridwidth = 2;
    gbc.weightx = 0;
    gbc.weighty = 0; 
    gbc.anchor = GridBagConstraints.WEST;
    gbc.fill = GridBagConstraints.NONE;
    gbc.insets = new Insets(7, 0, 0, 0);
    panel.add(pushTagCheckBox, gbc);

  }

  /**
   * @see ro.sync.exml.workspace.api.standalone.ui.OKCancelDialog.doOK()
   */
  @Override
  protected void doOK() {
    updateUI(tagTitleField.getText());
    if (getOkButton().isEnabled()) {
      super.doOK();
    }
  }
  
  /**
   * Update UI components depending whether the provided tag title
   * is valid or not.
   * 
   * @param tagTitle The tag title provided in the input field.
   */
  private void updateUI(String tagTitle) {

    boolean titleAlreadyExists = false;
    boolean titleContainsSpace = false;
    boolean titleContainsInvalidChars = false;
    
    if (!tagTitle.isEmpty()) {
      titleContainsSpace = tagTitle.contains(" ");
      titleContainsInvalidChars = !Repository.isValidRefName(Constants.R_TAGS + tagTitle);
      if (titleContainsSpace) {
        errorMessageTextArea.setText(translator.getTranslation(Tags.TAG_CONTAINS_SPACES));
      } else if (titleContainsInvalidChars) {
        errorMessageTextArea.setText(translator.getTranslation(Tags.TAG_CONTAINS_INVALID_CHARS));
      } else  {
        try {
          titleAlreadyExists = GitAccess.getInstance().existsTag(tagTitle);
        } catch (NoRepositorySelected | IOException e) {
          logger.debug(e, e);
        }
        errorMessageTextArea.setText(titleAlreadyExists ? translator.getTranslation(Tags.TAG_ALREADY_EXISTS) : "");
      } 
    }

    boolean isTagTitleValid = !tagTitle.isEmpty() && !titleAlreadyExists && !titleContainsSpace && !titleContainsInvalidChars;
    getOkButton().setEnabled(isTagTitleValid);
  }

  /**
   * Gets the tag title to be set for the new tag.
   * 
   * @return The name for the tag.
   */
  public String getTagTitle() {
    return tagTitleField.getText();
  }
  
  /**
   * Gets the tag message to be set for the new tag.
   * 
   * @return The message for the tag.
   */
  public String getTagMessage() {
    return tagMessageField.getText();
  }
  
  /**
   * @return <code>true</code> to push the newly created tag.
   */
  public boolean shouldPushNewTag() {
    return  pushTagCheckBox.isSelected();
  }
}