package org.arch.util;

import static org.junit.Assert.*;

import java.io.IOException;

import org.junit.Test;

public class FileHelperTest
{

	@Test
	public void testMoveFileFileString() throws IOException
	{
		FileHelper.moveFile("test.txt", "F:\\");
	}

}
