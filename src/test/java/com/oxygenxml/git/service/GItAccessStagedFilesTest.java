package com.oxygenxml.git.service;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.oxygenxml.git.service.entities.FileStatus;
import com.oxygenxml.git.service.entities.GitChangeType;
import com.oxygenxml.git.utils.FileUtil;

import junit.framework.TestCase;

public class GItAccessStagedFilesTest extends TestCase {


	private final static String LOCAL_TEST_REPOSITPRY = "target/test-resources/GItAccessStagedFilesTest";
	private GitAccess gitAccess = GitAccess.getInstance();

	protected void setUp() throws Exception {
		gitAccess.createNewRepository(LOCAL_TEST_REPOSITPRY);
		File file = new File(LOCAL_TEST_REPOSITPRY + "/test.txt");
		try {
			file.createNewFile();
		} catch (IOException e) {
			e.printStackTrace();
		}
		gitAccess.add(new FileStatus(GitChangeType.ADD, file.getName()));
		gitAccess.commit("file test added");
	}
	
	protected void tearDown() {
	  gitAccess.cleanUp();
	  File dirToDelete = new File(LOCAL_TEST_REPOSITPRY);
	  FileUtil.deleteRecursivelly(dirToDelete);
	}

	@Test
	public void testGetStagedFilesForModify(){
		try {
			PrintWriter out = new PrintWriter(LOCAL_TEST_REPOSITPRY + "/test.txt");
			out.println("modificare");
			out.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		gitAccess.addAll(gitAccess.getUnstagedFiles());
		List<FileStatus> actual = gitAccess.getStagedFiles();
		List<FileStatus> expected = new ArrayList<>();
		expected.add(new FileStatus(GitChangeType.CHANGED, "test.txt"));
		assertEquals(actual, expected);
	}
	
	@Test
	public void testGetStagedFilesForAddedFiles() {
		File file = new File(LOCAL_TEST_REPOSITPRY + "/add.txt");
		try {
			file.createNewFile();
		} catch (IOException e) {
			e.printStackTrace();
		}
		gitAccess.addAll(gitAccess.getUnstagedFiles());
		List<FileStatus> actual = gitAccess.getStagedFiles();
		List<FileStatus> expected = new ArrayList<>();
		expected.add(new FileStatus(GitChangeType.ADD, "add.txt"));
		assertEquals(actual, expected);
	}
	
	@Test
	public void testGetStagedFilesForDeletedFiles() {
		File file = new File(LOCAL_TEST_REPOSITPRY + "/test.txt");
		file.delete();
		
		gitAccess.addAll(gitAccess.getUnstagedFiles());
		List<FileStatus> actual = gitAccess.getStagedFiles();
		List<FileStatus> expected = new ArrayList<>();
		expected.add(new FileStatus(GitChangeType.REMOVED, "test.txt"));
		assertEquals(actual, expected);
	}
}
