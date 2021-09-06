package com.oxygenxml.git.view.stash;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.table.AbstractTableModel;

import com.oxygenxml.git.translator.Tags;
import com.oxygenxml.git.translator.Translator;
import org.apache.log4j.Logger;
import org.eclipse.jgit.revwalk.RevCommit;

import com.oxygenxml.git.service.GitAccess;

/**
 * Model for stashes table.
 *
 * @author Alex_Smarandache
 */
public class StashesTableModel extends AbstractTableModel {

  /**
   * Logger for logging.
   */
  private static final Logger LOGGER = Logger.getLogger(FilesTableModel.class);

  /**
   * Constant for the index representing the stash index.
   */
  public static final int STASH_INDEX_COLUMN = 0;

  /**
   * Constant for the index representing the stash description.
   */
  public static final int STASH_DESCRIPTION_COLUMN = 1;

  /**
   * The internal representation of the model
   */
  private final List<RevCommit> stashes = Collections.synchronizedList(new ArrayList<>());

  /**
   * The columns names.
   */
  private String[] columnsNames = new String[]{
    Translator.getInstance().getTranslation(Tags.ID),
            Translator.getInstance().getTranslation(Tags.DESCRIPTION)
  };


  /**
   * The public constructor.
   *
   * @param stashes List of stashes.
   */
  public StashesTableModel(List<RevCommit> stashes) {
    this.stashes.addAll(stashes);
  }


  @Override
  public int getRowCount() {
    return stashes.size();
  }


  @Override
  public Class<?> getColumnClass(int columnIndex) {
    Class<?> clazz = null;
    switch (columnIndex) {
      case STASH_INDEX_COLUMN:
        clazz = Integer.class;
        break;
      case STASH_DESCRIPTION_COLUMN:
        clazz = String.class;
        break;
      default:
        break;
    }
    return clazz;
  }


  @Override
  public boolean isCellEditable(int row, int column) {
    return false;
  }


  @Override
  public int getColumnCount() {
    return 2;
  }


  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    Object temp = null;
    switch (columnIndex) {
      case STASH_INDEX_COLUMN:
        temp = rowIndex;
        break;
      case STASH_DESCRIPTION_COLUMN:
        temp = stashes.get(rowIndex).getFullMessage();
        break;
      default:
        break;
    }

    return temp;
  }


  @Override
  public String getColumnName(int col) {
    return columnsNames[col];
  }


  /**
   * Removes all rows.
   */
  public void clear() {
    stashes.clear();
  }


  public void removeRow(int index) {
    GitAccess.getInstance().dropStash(index);
    stashes.remove(index);

    for (int row = index + 1; row < stashes.size(); row++) {
      this.setValueAt((int)getValueAt(row, 0) - 1, row, 0);
    }

    this.fireTableRowsUpdated(index, stashes.size());

  }


  /**
   * Returns the file from the given row
   *
   * @param convertedRow
   *          - the row
   * @return the file
   */
  public RevCommit getStashAt(int convertedRow) {
    return stashes.get(convertedRow);
  }


  /**
   * @return The files in the model.
   */
  public List<RevCommit> getStashes() {
    return stashes;
  }


}
