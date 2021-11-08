package com.oxygenxml.git.view.stash;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.swing.table.AbstractTableModel;

import org.eclipse.jgit.diff.DiffEntry;

import com.oxygenxml.git.service.entities.FileStatus;


/**
 * The model for files status.
 *
 * @author Alex_Smarandche
 */
public class FilesTableModel extends AbstractTableModel {

  /**
   * Index of the file status column.
   */
  public static final int STATUS_COLUMN = 0;

  /**
   * Index of the file location column.
   */
  public static final int LOCATION_COLUMN = 1;

  /**
   * The internal representation of the model
   */
  private final List<FileStatus> filesStatuses = new ArrayList<>();

  /**
   * Compares file statuses.
   */
  private static final Comparator<FileStatus> FILE_STATUS_COMPARATOR = (f1, f2) -> {
    int comparationResult = f1.getChangeType().compareTo(f2.getChangeType());
    if(comparationResult == 0) {
      // Same change type. Third level sort.
      comparationResult = f1.getFileLocation().compareTo(f2.getFileLocation());
    }
    return comparationResult;
  };
  
  @Override
  public boolean isCellEditable(int row, int column) {
    return false;
  }


  @Override
  public int getRowCount() {
    return filesStatuses.size();
  }
  
  
  @Override
  public int getColumnCount() {
    return 2;
  }

  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    Object temp = null;
    switch (columnIndex) {
      case STATUS_COLUMN:
        temp = filesStatuses.get(rowIndex).getChangeType();
        break;
      case LOCATION_COLUMN:
        temp = filesStatuses.get(rowIndex);
        break;
      default:
        break;
    }

    return temp;
  }
  
  @Override
  public Class<?> getColumnClass(int columnIndex) {
    Class<?> clazz = null;
    switch (columnIndex) {
      case STATUS_COLUMN:
        clazz = DiffEntry.ChangeType.class;
        break;
      case LOCATION_COLUMN:
        clazz = FileStatus.class;
        break;
      default:
        break;
    }
    return clazz;
  }


  /**
   * Sets the model with the given files, and also sorts it
   *
   * @param filesStatuses
   *          - the files
   */
  public void setFilesStatus(List<FileStatus> filesStatuses) {
    clear();
    this.filesStatuses.addAll(filesStatuses);
    this.filesStatuses.sort(FILE_STATUS_COMPARATOR);
    fireTableRowsUpdated(0, filesStatuses.size());
  }


  /**
   * Returns the file from the given row.
   *
   * @param rowIndex The row index.
   * 
   * @return the file
   */
  public FileStatus getFileAt(int rowIndex) {
    return filesStatuses.get(rowIndex);
  }


  /**
   * @return The files in the model.
   */
  public List<FileStatus> getFilesStatuses() {
    return filesStatuses;
  }


  /**
   * Removes all rows.
   */
  public void clear() {
    int size = filesStatuses.size();
    filesStatuses.clear();
    fireTableRowsDeleted(0, size);
  }


  /**
   * Get the file path.
   *
   * @param rowIndex  Row index.
   *
   * @return The location of the file.
   */
  public String getFileLocation(int rowIndex) {
    return filesStatuses.get(rowIndex).getFileLocation();
  }


  /**
   * Get the file status at the specified index.
   *
   * @param rowIndex Row index.
   *
   * @return The file status.
   */
  public FileStatus getFileStatus(int rowIndex) {
    return filesStatuses.get(rowIndex);
  }

  
}
