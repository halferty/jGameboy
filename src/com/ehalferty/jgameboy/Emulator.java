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

public class Emulator {
	private static CPU cpu;
	
	int i = 0;
	
	public void init(byte[] gameBytes) {
		cpu = new CPU(gameBytes);
	}
	
	public void run() {
		
		// Graphics chip emulation will go here.
		
		// For now, just scroll a pixel down the screen.
		
		EmulatorWindow.pixels[0][i] = 0;
		i++;
		if (i > 143) i = 0;
		EmulatorWindow.pixels[0][i] = 2;
		if (cpu.isRunning()) {
			
			// Run a CPU instruction
			cpu.step();
		}
	}
}
