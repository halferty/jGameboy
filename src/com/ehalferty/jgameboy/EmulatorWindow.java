/**
* Copyright 2012 by Ed Halferty
*
* This file is part of jGameboy.
*
* jGameboy is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* jGameboy is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with jGameboy. If not, see <http://www.gnu.org/licenses/>.
*/
package com.ehalferty.jgameboy;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.image.BufferStrategy;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import javax.swing.JFrame;


public class EmulatorWindow extends JFrame {
	
	// Constants
	static int SCALE = 4;
	
	// Shades of grey used by the gameboy.
	static Color [] colors = {
			new Color(0, 0, 0),
			new Color(85, 85, 85),
			new Color(171, 171, 171),
			new Color(255, 255, 255)
	};
	
	// Globals
	protected static byte[] gameBytes;
	protected static int [][] pixels = new int[160][144];
	private BufferStrategy bf;
	private Graphics2D g;
	private Emulator emu = new Emulator();
	
	// Main
	public static void main(String [] args) {
		
		// default ROM file
		String ROMFileName = "demo.gb";
		if (args.length > 0) {
			ROMFileName = args[0];
		}
		
		// Try to open the file
		File f = new File(ROMFileName);
		
		// Try to read the file as binary data
		try {
			gameBytes = Files.readAllBytes(f.toPath());
			new EmulatorWindow();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public EmulatorWindow() {
		setTitle("Gameboy Emulator");
		setLocation(20, 20);
		setSize(SCALE * 160 + 50, SCALE * 144 + 50);
		setVisible(true);
		setResizable(false);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		createBufferStrategy(2);
		bf = getBufferStrategy();
		
		// Clear all pixels
		for (int i = 0; i < 160; i++) {
			for (int j = 0; j < 144; j++) {
				pixels[i][j] = 0;
			}
		}
		
		// Initialize the emulator
		emu.init(gameBytes);
		
		// Run the main game loop
		gameLoop();
	}
	
	private void gameLoop() {
		long lastLoopTime = System.nanoTime();
		while (true) {
			long now = System.nanoTime();
		      lastLoopTime = now;
		      drawFrame();
		      emu.run();
		      try {
		    	  Thread.sleep( (lastLoopTime-System.nanoTime())/1000000 + 10 );
		      } catch (Exception e) { }
		}
	}
	
	// Draw the pixels to the JFrame at the correct scale
	private void drawFrame() {
		try {
			g = (Graphics2D) bf.getDrawGraphics();
			int prevColor = 0;
			for (int i = 0; i < 160; i++) {
				for (int j = 0; j < 144; j++) {
					int x = i * SCALE + 30;
					int y = j * SCALE + 30;
					if (pixels[i][j] == prevColor) {
						
					} else {
						g.setColor(colors[pixels[i][j]]);
					}
					prevColor = pixels[i][j];
					g.fillRect(x, y, SCALE, SCALE);
				}
			}
		} finally {
			g.dispose();
		}
		bf.show();
		Toolkit.getDefaultToolkit().sync();
	}
}

