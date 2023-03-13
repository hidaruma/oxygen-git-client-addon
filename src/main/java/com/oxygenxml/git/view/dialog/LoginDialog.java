package com.oxygenxml.git.view.dialog;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import javax.swing.ButtonGroup;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.oxygenxml.git.constants.UIConstants;
import com.oxygenxml.git.options.CredentialsBase;
import com.oxygenxml.git.options.OptionsManager;
import com.oxygenxml.git.options.PersonalAccessTokenInfo;
import com.oxygenxml.git.options.UserAndPasswordCredentials;
import com.oxygenxml.git.translator.Tags;
import com.oxygenxml.git.translator.Translator;

import ro.sync.exml.workspace.api.PluginWorkspaceProvider;
import ro.sync.exml.workspace.api.standalone.ui.OKCancelDialog;
import ro.sync.exml.workspace.api.standalone.ui.OxygenUIComponentsFactory;

@SuppressWarnings("java:S110")
public class LoginDialog extends OKCancelDialog {
  /**
   * GitHub host.
   */
  private static final String GITHUB_COM = "github.com";
  /**
   * Left inset for the inner panels.
   */
  private static final int INNER_PANELS_LEFT_INSET = 21;
  /**
   * The translator for the messages that are displayed in this dialog
   */
  private static final Translator TRANSLATOR = Translator.getInstance();
  /**
   *  Logger for logging.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(LoginDialog.class); 
	/**
	 * The host for which to enter the credentials
	 */
	private String host;
	/**
	 * The error message
	 */
	private String message;
	/**
	 * TextField for entering the username
	 */
	private JTextField tfUsername;
	/**
	 * TextField for entering the password
	 */
	private JPasswordField pfPassword;
	/**
	 * The new credentials stored by this dialog
	 */
	private CredentialsBase credentials;
	/**
	 * Basic (user + password) authentication radio button.
	 */
  private JRadioButton basicAuthRadio;
  /**
   * Personal access token authentication radio button.
   */
  private JRadioButton tokenAuthRadio;
  /**
   * Personal access token password field.
   */
  private JPasswordField tokenPassField;
  /**
   * Username and password panel.
   */
  private JPanel userAndPasswordPanel;

	/**
	 * Constructor.
	 * 
	 * @param host         The host for which to provide the credentials.
	 * @param loginMessage The login message.
	 */
	public LoginDialog(String host, String loginMessage) {
		super(
		    (JFrame) PluginWorkspaceProvider.getPluginWorkspace().getParentFrame(),
		    TRANSLATOR.getTranslation(Tags.LOGIN_DIALOG_TITLE),
		    true);
		
		if (LOGGER.isDebugEnabled()) {
		  final Exception e = new Exception("LOGIN DIALOG WAS SHOWN...");
		  LOGGER.debug(e.getMessage(), e);
		}
		
		this.host = host;
		this.message = loginMessage;
		
		createGUI();

		this.setResizable(false);
		this.pack();
		this.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
	}

	/**
	 * Adds to the dialog the labels and the text fields.
	 */
	public void createGUI() {
		JPanel panel = new JPanel(new GridBagLayout());
		GridBagConstraints gbc = new GridBagConstraints();

		// Info label
		JLabel lblGitRemote = new JLabel(
				"<html>" + message + "<br/>" 
				    + TRANSLATOR.getTranslation(Tags.LOGIN_DIALOG_MAIN_LABEL) 
				    + " <b>" + host + "</b>"
				    + "."
				    + "</html>");
		gbc.insets = new Insets(
		    0,
		    0,
		    UIConstants.COMPONENT_BOTTOM_PADDING,
				0);
		gbc.anchor = GridBagConstraints.WEST;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weightx = 0;
		gbc.weighty = 0;
		gbc.gridx = 0;
		gbc.gridy = 0;
		panel.add(lblGitRemote, gbc);
		
    
    // Personal access token radio
		final ButtonGroup buttonGroup = new ButtonGroup();
    tokenAuthRadio = new JRadioButton(TRANSLATOR.getTranslation(Tags.PERSONAL_ACCESS_TOKEN));
    tokenAuthRadio.setFocusPainted(false);
    gbc.insets = new Insets(0, 0, 0, 0);
    gbc.gridx = 0;
    gbc.gridy ++;
    panel.add(tokenAuthRadio, gbc);
    buttonGroup.add(tokenAuthRadio);
    
    // Token field
    tokenPassField = new JPasswordField();
    gbc.insets = new Insets(
        0,
        INNER_PANELS_LEFT_INSET,
        UIConstants.LAST_LINE_COMPONENT_BOTTOM_PADDING,
        0);
    gbc.gridx = 0;
    gbc.gridy ++;
    gbc.weightx = 1;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    panel.add(tokenPassField, gbc);
    
    // Basic authentication radio
    basicAuthRadio = new JRadioButton(TRANSLATOR.getTranslation(Tags.BASIC_AUTHENTICATION));
    basicAuthRadio.setFocusPainted(false);
    gbc.insets = new Insets(0, 0, 0, 0);
    gbc.gridx = 0;
    gbc.gridy ++;
    panel.add(basicAuthRadio, gbc);
    buttonGroup.add(basicAuthRadio);
    
    // User + password
    userAndPasswordPanel = createUserAndPasswordPanel();
    gbc.gridy ++;
    gbc.insets = new Insets(0, INNER_PANELS_LEFT_INSET, 0, 0);
    panel.add(userAndPasswordPanel, gbc);

		this.add(panel, BorderLayout.CENTER);
		
		ItemListener radioItemListener = e -> {
		  if (e.getStateChange() == ItemEvent.SELECTED) {
		    updateGUI();
		  }
		};
    tokenAuthRadio.addItemListener(radioItemListener);
		basicAuthRadio.addItemListener(radioItemListener);
		
		initGUI();
	}
	
  /**
   * Init GUI.
   */
  private void initGUI() {
    setOkButtonText(TRANSLATOR.getTranslation(Tags.AUTHENTICATE));
    
    if (GITHUB_COM.equals(host)) {
      tokenAuthRadio.doClick();
    } else {
      basicAuthRadio.doClick();
    }
  }

	/**
	 * Update GUI.
	 */
  private void updateGUI() {
    Component[] components = userAndPasswordPanel.getComponents();
    for (Component component : components) {
      component.setEnabled(basicAuthRadio.isSelected());
    }
    tokenPassField.setEnabled(tokenAuthRadio.isSelected());

    SwingUtilities.invokeLater(() -> {
      if (tokenPassField.isEnabled()) {
        tokenPassField.requestFocus();
      } else if (tfUsername.isEnabled()) {
        tfUsername.requestFocus();
      }
    });
  }

	/**
	 * @return The username and password panel.
	 */
  private JPanel createUserAndPasswordPanel() {
    JPanel userAndPassPanel = new JPanel(new GridBagLayout());
    
    // Username label
		JLabel lbUsername = new JLabel(TRANSLATOR.getTranslation(Tags.LOGIN_DIALOG_USERNAME_LABEL));
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(
		    0,
		    0,
        UIConstants.COMPONENT_BOTTOM_PADDING,
        UIConstants.COMPONENT_RIGHT_PADDING);
		c.anchor = GridBagConstraints.WEST;
		c.weightx = 0;
		c.weighty = 0;
		c.gridx = 0;
		c.gridy = 0;
		userAndPassPanel.add(lbUsername, c);

		// Username text field
		tfUsername = OxygenUIComponentsFactory.createTextField();
		tfUsername.setPreferredSize(new Dimension(250, tfUsername.getPreferredSize().height));
		c.insets = new Insets(
        0,
        0,
        UIConstants.COMPONENT_BOTTOM_PADDING,
        0);
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1;
		c.gridx ++;
		userAndPassPanel.add(tfUsername, c);

		// Password label
		JLabel lbPassword = new JLabel(TRANSLATOR.getTranslation(Tags.LOGIN_DIALOG_PASS_WORD_LABEL));
		c.insets = new Insets(
        0,
        0,
        UIConstants.COMPONENT_BOTTOM_PADDING,
        UIConstants.COMPONENT_RIGHT_PADDING);
		c.fill = GridBagConstraints.NONE;
		c.weightx = 0;
		c.gridx = 0;
		c.gridy ++;
		userAndPassPanel.add(lbPassword, c);

		// Password text field
		pfPassword = new JPasswordField();
		pfPassword.setPreferredSize(new Dimension(250, pfPassword.getPreferredSize().height));
		c.insets = new Insets(
        0,
        0,
        UIConstants.COMPONENT_BOTTOM_PADDING,
        0);
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1;
		c.gridx ++;
		userAndPassPanel.add(pfPassword, c);
		
		return userAndPassPanel;
  }

	@Override
	protected void doOK() {
	  if (basicAuthRadio.isSelected()) {
	    String username = tfUsername.getText().trim();
	    String password = new String(pfPassword.getPassword());
	    credentials = new UserAndPasswordCredentials(username, password, host);
    } else {
      String tokenValue = new String(tokenPassField.getPassword());
      credentials = new PersonalAccessTokenInfo(host, tokenValue);
    }
	  
	  OptionsManager.getInstance().saveGitCredentials(credentials);
	  
		super.doOK();
	}

	/**
	 * @return The user credentials retrieved from the user. <code>null</code> if the user canceled
	 * the dialog.
	 */
	public CredentialsBase getCredentials() {
		return credentials;
	}
	
}