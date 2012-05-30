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

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class CPU {
	
	// CPU data
	private int A, B, C, D, E, H, L, M, T;
	private int SP, PC, AF, BC, DE, HL;
	private boolean FZ, FC, FH, FN;
	private int [] mem = new int [0x10000];
	private boolean interrupts_enabled, running;
	
	// Emulator data
	private int trace_count = 0;
	
	
	// Constructor
	public CPU(byte [] cartBytes) {
		
		// Clear all memory
		for (int i = 0; i < mem.length; i++) {
			mem[i] = 0;
		}
		for (int i = 0; i < cartBytes.length; i++) {
			mem[i] = cartBytes[i];
		}
		
		// Reset the CPU data
		reset();
	}

	// Reset to GameBoy boot up values
	public void reset() {
		setAF(0x01B0);
		setBC(0x0013);
		setDE(0x00D8);
		setHL(0x014D);
		PC = 0x0100;
		SP = 0xFFFE;
		M = 0;
		T = 0;
		interrupts_enabled = false;
		running = true;
		writeByte(0xFF05, 0x00); // TIMA
		writeByte(0xFF06, 0x00); // TMA
		writeByte(0xFF07, 0x00); // TAC
		writeByte(0xFF10, 0x80); // NR10
		writeByte(0xFF11, 0xBF); // NR11
		writeByte(0xFF12, 0xF3); // NR12
		writeByte(0xFF14, 0xBF); // NR14
		writeByte(0xFF16, 0x3F); // NR21
		writeByte(0xFF17, 0x00); // NR22
		writeByte(0xFF19, 0xBF); // NR24
		writeByte(0xFF1A, 0x7F); // NR30
		writeByte(0xFF1B, 0xFF); // NR31
		writeByte(0xFF1C, 0x9F); // NR32
		writeByte(0xFF1E, 0xBF); // NR33
		writeByte(0xFF20, 0xFF); // NR41
		writeByte(0xFF21, 0x00); // NR42
		writeByte(0xFF22, 0x00); // NR43
		writeByte(0xFF23, 0xBF); // NR30
		writeByte(0xFF24, 0x77); // NR50
		writeByte(0xFF25, 0xF3); // NR51
		writeByte(0xFF26, 0xF1); // NR52
		writeByte(0xFF40, 0x91); // LCDC
		writeByte(0xFF42, 0x00); // SCY
		writeByte(0xFF43, 0x00); // SCX
		writeByte(0xFF45, 0x00); // LYC
		writeByte(0xFF47, 0xFC); // BGP
		writeByte(0xFF48, 0xFF); // OBP0
		writeByte(0xFF49, 0xFF); // OBP1
		writeByte(0xFF4A, 0x00); // WY
		writeByte(0xFF4B, 0x00); // WX
		writeByte(0xFFFF, 0x00); // IE
	}
	
	
	// Read/Decode/Execute one instruction
	// TODO: Add correct timing
	public void step() {
		
		// Make sure all registers are 8-bits or 16-bits.
		maskRegs();
		
		// Read and break apart an instruction
		int instr = readByte(PC) & 0xFF;
		int ih = (byte) ((instr >> 4) & 0x0F);
		int il = (byte) (instr & 0x0F);
		int op2 = decodeOp2(il);
		
		// Print out each instruction as it runs
		System.out.println(trace_count++ +
				") PC=" + Integer.toHexString(PC) +
				"\tSP=" + Integer.toHexString(SP) +
				"\tI=" + Integer.toHexString(instr) +
				"\tAF=" + Integer.toHexString(getAF()) +
				"\tBC=" + Integer.toHexString(getBC()) +
				"\tDE=" + Integer.toHexString(getDE()) +
				"\tHL=" + Integer.toHexString(getHL()));
		
		// TODO: Lookup table of timings
		
		// LD instructions
		if ((3 < ih) && (ih < 8)) {
			if (il < 8) {
				if (ih == 4) B = op2;
				else if (ih == 5) D = op2;
				else if (ih == 6) H = op2;
				else if (ih == 7) writeByte(getHL(), op2);
			} else {
				if (ih == 4) C = op2;
				else if (ih == 5) E = op2;
				else if (ih == 6) L = op2;
				else if (ih == 7) A = op2;
			}
		// ADD/SUB/AND/OR/ADC/SBC/XOR/CP instructions
		} else if ((7 < ih) && (ih < 0x0C)) {
			if (il < 8) {
				if (ih == 0x8) A = add8(A, op2);
				else if (ih == 0x9) A = sub8(A, op2);
				else if (ih == 0xA) A =  and8(A, op2);
				else if (ih == 0xB) A =  or8(A, op2);
			} else {
				if (ih == 0x8) A = adc8(A, op2);
				else if (ih == 0x9) A = sbc8(A, op2);
				else if (ih == 0xA) A =  xor8(A, op2);
				else if (ih == 0xB) sub8(A, op2);
			}
		} else if (ih == 0xCB) {
			instr = readByte(PC) & 0xFF;
			ih =  ((instr >> 4) & 0x0F);
			il =  (instr & 0x0F);
			op2 = decodeOp2(il);
			if (il < 8) {
				if (ih == 0x0) op2 = rlc8(op2);
				else if (ih == 0x1) op2 = rl8(op2);
				else if (ih == 0x2) op2 = sl8(op2);
				else if (ih == 0x3) op2 = swap8(op2);
				else if (ih == 0x4) testBit(op2, 0);
				else if (ih == 0x5) testBit(op2, 2);
				else if (ih == 0x6) testBit(op2, 4);
				else if (ih == 0x7) testBit(op2, 6);
				else if (ih == 0x8) op2 =  setBit(op2, 0, false);
				else if (ih == 0x9) op2 =  setBit(op2, 2, false);
				else if (ih == 0xA) op2 =  setBit(op2, 4, false);
				else if (ih == 0xB) op2 =  setBit(op2, 6, false);
				else if (ih == 0xC) op2 =  setBit(op2, 0, true);
				else if (ih == 0xD) op2 =  setBit(op2, 2, true);
				else if (ih == 0xE) op2 =  setBit(op2, 4, true);
				else if (ih == 0xF) op2 =  setBit(op2, 6, true);
			} else {
				if (ih == 0x0) op2 = rrc8(op2);
				else if (ih == 0x1) op2 = rr8(op2);
				else if (ih == 0x2) op2 = sr8(op2);
				else if (ih == 0x3) op2 = srl8(op2);
				else if (ih == 0x4) testBit(op2, 1);
				else if (ih == 0x5) testBit(op2, 3);
				else if (ih == 0x6) testBit(op2, 5);
				else if (ih == 0x7) testBit(op2, 7);
				else if (ih == 0x8) op2 =  setBit(op2, 1, false);
				else if (ih == 0x9) op2 =  setBit(op2, 3, false);
				else if (ih == 0xA) op2 =  setBit(op2, 5, false);
				else if (ih == 0xB) op2 =  setBit(op2, 7, false);
				else if (ih == 0xC) op2 =  setBit(op2, 1, true);
				else if (ih == 0xD) op2 =  setBit(op2, 3, true);
				else if (ih == 0xE) op2 =  setBit(op2, 5, true);
				else if (ih == 0xF) op2 =  setBit(op2, 7, true);
			}
		} else if (instr == 0x00) { // NOP
		} else if (instr == 0x10) { running = false;
		} else if (instr == 0x20) { PC += 1 + (FZ? 0 : readByte(PC + 1));
		} else if (instr == 0x30) { PC += 1 + (FC? 0 : readByte(PC + 1));
		} else if (instr == 0x01) { setBC(readShort(++PC)); PC++;
		} else if (instr == 0x11) { setDE(readShort(++PC)); PC++;
		} else if (instr == 0x21) { setHL(readShort(++PC)); PC++;
		} else if (instr == 0x31) { SP = readShort(++PC); PC++;
		} else if (instr == 0x02) { writeShort(getBC(), A);
		} else if (instr == 0x12) { writeShort(getDE(), A);
		} else if (instr == 0x22) { writeShort(getHL(), A); setHL(inc16(getHL()));
		} else if (instr == 0x32) { writeShort(getHL(), A); setHL(dec16(getHL()));
		} else if (instr == 0x03) { setBC(inc16(getBC()));
		} else if (instr == 0x13) { setDE(inc16safe(getDE()));
		} else if (instr == 0x23) { setHL(inc16(getHL()));
		} else if (instr == 0x33) { SP = inc16(SP);
		} else if (instr == 0x04) { B = inc8(B);
		} else if (instr == 0x14) { D = inc8(D);
		} else if (instr == 0x24) { H = inc8(H);
		} else if (instr == 0x34) { setHL(inc16(getHL()));
		} else if (instr == 0x05) { B = dec8(B);
		} else if (instr == 0x15) { D = dec8(D);
		} else if (instr == 0x25) { H = dec8(H);
		} else if (instr == 0x35) { setHL(dec16(getHL()));
		} else if (instr == 0x06) { B = readByte(++PC);
		} else if (instr == 0x16) { D = readByte(++PC);
		} else if (instr == 0x26) { H = readByte(++PC);
		} else if (instr == 0x36) { writeByte(getHL(), readByte(++PC));
		} else if (instr == 0x07) { A = rlc8(A);
		} else if (instr == 0x17) { A = rl8(A);
		} else if (instr == 0x27) { System.out.println("DAA encountered. Should implement."); running = false;
		} else if (instr == 0x37) { FC = true; FH = false; FN = false;
		} else if (instr == 0x08) { writeShort(readShort(++PC), SP);  PC++;
		} else if (instr == 0x18) { PC += 1 + readByte(++PC);
		} else if (instr == 0x28) { PC += 1 + (FZ? readShort(PC + 1) : 0);
		} else if (instr == 0x38) { PC += 1 + (FC? readShort(PC + 1) : 0);
		} else if (instr == 0x09) { setHL(add16(getHL(), getBC()));
		} else if (instr == 0x19) { setHL(add16(getHL(), getDE())); 
		} else if (instr == 0x29) { setHL(add16(getHL(), getHL()));
		} else if (instr == 0x39) { setHL(add16(getHL(), SP));
		} else if (instr == 0x0A) { A = readByte(getBC());
		} else if (instr == 0x1A) { A = readByte(getDE());
		} else if (instr == 0x2A) {
			A = readByte(getHL()); setHL(inc16safe(getHL()));
		} else if (instr == 0x3A) { A = readByte(getHL()); setHL(dec16(getHL()));
		} else if (instr == 0x0B) { setBC(dec16safe(getBC()));
		} else if (instr == 0x1B) { setDE(dec16safe(getDE()));
		} else if (instr == 0x2B) { setHL(dec16safe(getHL()));
		} else if (instr == 0x3B) { SP = dec16(SP);
		} else if (instr == 0x0C) { C = inc8(C);
		} else if (instr == 0x1C) { E = inc8(E);
		} else if (instr == 0x2C) { L = inc8(L);
		} else if (instr == 0x3C) { A = inc8(A);
		} else if (instr == 0x0D) { C = dec8(C);
		} else if (instr == 0x1D) { E = dec8(E);
		} else if (instr == 0x2D) { L = dec8(L);
		} else if (instr == 0x3D) { A = dec8(A);
		} else if (instr == 0x0E) { C = readByte(++PC);
		} else if (instr == 0x1E) { E = readByte(++PC);
		} else if (instr == 0x2E) { L = readByte(++PC);
		} else if (instr == 0x3E) { A = readByte(++PC);
		} else if (instr == 0x0F) { A = rrc8(A);
		} else if (instr == 0x1F) { A = rr8(A);
		} else if (instr == 0x2F) { A =  ~A; FH = true; FN = true;
		} else if (instr == 0x3F) { FC = !FC; FN = false; FH = false;
		} else if (instr == 0xC0) { if (!FZ) PC = pop();
		} else if (instr == 0xD0) { if (!FC) PC = pop();
		} else if (instr == 0xE0) { writeByte((0xFF00 + readByte(++PC)), A);
		} else if (instr == 0xF0) { A = readByte((0xFF00 + readByte(++PC)));
		} else if (instr == 0xC1) { setBC(pop());
		} else if (instr == 0xD1) { setDE(pop());
		} else if (instr == 0xE1) { setHL(pop());
		} else if (instr == 0xF1) { setAF(pop());
		} else if (instr == 0xC2) { PC = (FZ? PC + 2 : readShort(PC + 1));
		} else if (instr == 0xD2) { PC = (FC? PC + 2 : readShort(PC + 1));
		} else if (instr == 0xE2) { writeByte((0xFF00 + C), A);
		} else if (instr == 0xF2) { A = readByte(0xFF00 + C);
		} else if (instr == 0xC3) { PC = readShort(PC + 1) - 1;
		} else if (instr == 0xD3) { illegalOpcode(ih, il);
		} else if (instr == 0xE3) { illegalOpcode(ih, il);
		} else if (instr == 0xF3) { interrupts_enabled = false;
		} else if (instr == 0xC4) { PC = ((!FZ)? readShort(PC + 1) : PC + 2);
		} else if (instr == 0xD4) { PC = ((!FC)? readShort(PC + 1) : PC + 2);
		} else if (instr == 0xE4) { illegalOpcode(ih, il);
		} else if (instr == 0xF4) { illegalOpcode(ih, il);
		} else if (instr == 0xC5) { push(getBC());
		} else if (instr == 0xD5) { push(getDE());
		} else if (instr == 0xE5) { push(getHL());
		} else if (instr == 0xF5) { push(getAF());
		} else if (instr == 0xC6) { A = add8(A, readByte(++PC));
		} else if (instr == 0xD6) { A = sub8(A, readByte(++PC));
		} else if (instr == 0xE6) { A =  (A & readByte(++PC));
		} else if (instr == 0xF6) { A =  (A | readByte(++PC));
		} else if (instr == 0xC7) { push(PC + 1); PC = 0x00; interrupts_enabled = false;
		} else if (instr == 0xD7) { push(PC + 1); PC = 0x10; interrupts_enabled = false;
		} else if (instr == 0xE7) { push(PC + 1); PC = 0x20; interrupts_enabled = false;
		} else if (instr == 0xF7) { push(PC + 1); PC = 0x30; interrupts_enabled = false;
		} else if (instr == 0xC8) { if (FZ) PC = pop(); interrupts_enabled = false;
		} else if (instr == 0xD8) { if (FC) PC = pop(); interrupts_enabled = false;
		} else if (instr == 0xE8) { SP += readByte(++PC);
		} else if (instr == 0xF8) { setHL((SP + readByte(++PC)));
		} else if (instr == 0xC9) { PC = pop();
		} else if (instr == 0xD9) { PC = pop(); interrupts_enabled = true;
		} else if (instr == 0xE9) { PC = readShort(getHL());
		} else if (instr == 0xF9) { SP = getHL();
		} else if (instr == 0xCA) { PC = (FZ? readShort(PC + 1) : PC + 2);
		} else if (instr == 0xDA) { PC = (FC? readShort(PC + 1) : PC + 2);
		} else if (instr == 0xEA) { writeByte(readShort(PC + 1), A); PC += 2;
		} else if (instr == 0xFA) { A = readByte(readShort(PC + 1));
		} else if (instr == 0xDB) { illegalOpcode(ih, il);
		} else if (instr == 0xEB) { illegalOpcode(ih, il);
		} else if (instr == 0xFB) { interrupts_enabled = true;
		} else if (instr == 0xCC) { if (FZ) { push(PC + 2); PC = readShort(PC + 1); } else { PC += 2; }
		} else if (instr == 0xDC) { if (FC) { push(PC + 2); PC = readShort(PC + 1); } else { PC += 2; }
		} else if (instr == 0xEC) { illegalOpcode(ih, il);
		} else if (instr == 0xFC) { illegalOpcode(ih, il);
		} else if (instr == 0xCD) { push(PC + 2); PC = readShort(PC + 1) - 1;
		} else if (instr == 0xDD) { illegalOpcode(ih, il);
		} else if (instr == 0xED) { illegalOpcode(ih, il);
		} else if (instr == 0xFD) { illegalOpcode(ih, il);
		} else if (instr == 0xCE) { A = adc8(A, readByte(++PC));
		} else if (instr == 0xDE) { A = sbc8(A, readByte(++PC));
		} else if (instr == 0xEE) { A ^= readByte(++PC);
		} else if (instr == 0xFE) { sub8(A, readByte(++PC));
		} else if (instr == 0xCF) { push(PC + 1); PC = 0x08; interrupts_enabled = false;
		} else if (instr == 0xDF) { push(PC + 1); PC = 0x18; interrupts_enabled = false;
		} else if (instr == 0xEF) { push(PC + 1); PC = 0x28; interrupts_enabled = false;
		} else if (instr == 0xFF) { push(PC + 1); PC = 0x38; interrupts_enabled = false;
		} else {
			System.out.println("Unhandled opcode! " +
					Integer.toHexString(ih) + "" +
					Integer.toHexString(il));
		}
		AF = getAF();
		BC = getBC();
		DE = getDE();
		HL = getHL();
		PC++;
	}
	
	private void illegalOpcode(int ih, int il) {
		System.out.println("Illegal opcode! " +
				Integer.toHexString(ih) + "" +
				Integer.toHexString(il));
	}

	private int or8(int a2, int op2) {
		int res = (a2 | op2) & 0xFF;
		FZ = (res == 0);
		FC = false;
		FN = false;
		FH = false;
		return res;
	}

	private int xor8(int a2, int op2) {
		int res = (a2 ^ op2) & 0xFF;
		FZ = (res == 0);
		FC = false;
		FN = false;
		FH = false;
		return res;
	}

	private int and8(int a2, int op2) {
		int res = (a2 & op2) & 0xFF;
		FZ = (res == 0);
		FC = false;
		FN = false;
		FH = true;
		return res;
	}

	private int pop() {
		return  ((readByte(SP++) << 8) | readByte(SP++));
	}

	private void push(int i) {
		writeByte(--SP, lByte(i));
		writeByte(--SP, hByte(i));
	}

	private int srl8(int b) {
		FC = ((b & 0x1) != 0);
		return  (b >>> 1);
	}

	private int sr8(int b) {
		FC = ((b & 0x1) != 0);
		return  ((b & 0x80) | (b >>> 1));
	}
	
	private int sl8(int b) {
		FC = ((b >>> 7) != 0);
		return  (b << 1);
	}

	private void testBit(int b, int n) {
		FZ = getBit(b, n);
		FN = false;
		FH = false;
	}

	private int rr8(int a) {
		int bit_7 = FC? 0x80 : 0;
		FC = getBit(a, 0);
		a = (a >>> 1) | bit_7;
		FN = false;
		FH = false;
		FZ = (a == 0);
		return a;
	}

	private int rl8(int a) {
		int bit_0 = FC? 1 : 0;
		FC = getBit(a, 7);
		a = ((a << 1) & 0xFF) | bit_0;
		FZ = (a == 0);
		FN = false;
		FH = false;
		return a;
	}

	private int rrc8(int a) {
		FC = getBit(a, 0);
		a = ((a >>> 1) & 0xFF) | (a << 7);
		FZ = (a == 0);
		FN = false;
		FH = false;
		return a;
	}

	private int rlc8(int a) {
		FC = getBit(a, 7);
		a = ((a << 1) & 0xFF) | (a >>> 7);
		FZ = (a == 0);
		FN = false;
		FH = false;
		return a;
	}

	private int add8(int a, int b) {
		int sum = a + b;
		FC = getBit(sum, 8);
		FN = false;
		FZ = (sum == 0);
		FH = (getBit(sum, 3) && getBit(b, 3));
		return sum;
	}

	private int adc8(int a, int b) {
		int sum = a + b;
		if (FC) sum++;
		FC = getBit(sum, 8);
		FN = false;
		FZ = (sum == 0);
		FH = (getBit(sum, 3) && getBit(b, 3));
		return sum;
	}

	private int add16(int a, int b) {
		int sum = a + b;
		FC = getBit(sum, 16);
		FN = false;
		FH = (getBit(sum, 11) && getBit(b, 11));
		return sum;
	}
	
	private int sub8(int a, int b) {
		int dif = a - b;
		FC = (dif < 0);
		FZ = (dif == 0);
		FN = true;
		FH = (!getBit(a, 3) && getBit(b, 3));
		return dif;
	}
	
	private int sbc8(int a, int b) {
		int dif = a - b;
		if (FC) dif--;
		FC = (dif < 0);
		FZ = (dif == 0);
		FN = true;
		FH = (!getBit(a, 3) && getBit(b, 3));
		return dif;
	}
	
	private int sub16(int a, int b) {
		int dif = a - b;
		FC = (dif < 0);
		FZ = (dif == 0);
		FN = true;
		FH = (!getBit(a, 11) && getBit(b, 11));
		return dif;
	}
	
	private int sbc16(int a, int b) {
		int dif = a - b;
		if (FC) dif--;
		FC = (dif < 0);
		FZ = (dif == 0);
		FN = true;
		FH = (!getBit(a, 11) && getBit(b, 11));
		return dif;
	}
	
	
	

	// Set/get flags
	private boolean getBit(int data, int bit) { return ((1 << bit) & data & 0xFF) != 0; }
	private void writeByte(int addr, int data) { mem[addr & 0xFFFF] = data & 0xFF; }
	private void writeShort(int addr, int data) {
		writeByte(addr, lByte(data));
		writeByte(addr + 1, hByte(data));
	}
	private int readByte(int addr) { return mem[addr]; }
	private int readShort(int addr) { return twoBytesToShort(readByte(addr + 1), readByte(addr)); }
	private int getM() { return M; }
	private int getT() { return T; }
	private void setM(int m) { M = m; }
	private void setT(int t) { T = t; }
	private int getAF() { return twoBytesToShort(A, getF()); }
	private int getBC() { return twoBytesToShort(B, C); }
	private int getDE() { return twoBytesToShort(D, E); }
	private int getHL() { return twoBytesToShort(H, L); }
	private void setAF(int aF) { A = hByte(aF); setF(lByte(aF)); }
	private void setBC(int bc) { B = hByte(bc); C = lByte(bc); }
	private void setDE(int dE) { D = hByte(dE); E = lByte(dE); }
	private void setHL(int hL) { H = hByte(hL); L = lByte(hL); }
	private void setF(int i) {
		i &= 0xFF;
		FZ = ((i & 0x80) != 0);
		FC = ((i & 0x40) != 0);
		FH = ((i & 0x20) != 0);
		FN = ((i & 0x10) != 0);
	}
	private int getF() {
		return ((FZ)? 0x80 : 0) | ((FC)? 0x40 : 0) | ((FH)? 0x20 : 0) | ((FN)? 0x10 : 0);
	}
	private int twoBytesToShort(int H, int L) { return (((H & 0xFF) << 8) | (L & 0xFF)); }
	private int hByte(int s) { return (s & 0xFF00) >> 8; }
	private int lByte(int s) { return (s & 0xFF); }
	private int setBit(int value, int bit, boolean set) {
		int bit_to_set = 1 << bit;
		return (set? (value | bit_to_set) : (value & (~bit_to_set)));
	}
	private int decodeOp2(int b) {
		int op2l = b & 0x7;
		if (op2l == 0)  return B;
		else if (op2l == 1) return C;
		else if (op2l == 2) return D;
		else if (op2l == 3) return E;
		else if (op2l == 4) return H;
		else if (op2l == 5) return L;
		else if (op2l == 6) return readByte(getHL());
		else return A;
	}
	private int inc8(int c) {
		FH = ((c & 0xF) == 0xF);
		c++;
		FZ = ((c & 0xFF) == 0);
		FN = false;
		return c & 0xFF;
	}
	private int dec8(int c) {
		FH = ((c & 0xF) == 0x0);
		c--;
		FZ = ((c & 0xFF) == 0);
		FN = true;
		return c & 0xFF;
	}
	private int inc16(int d) { return add16(d, 1); }
	private int dec16(int d) { return sub16(d, 1); }
	private int inc16safe(int d) { return d + 1; }
	private int dec16safe(int d) { return d - 1; }
	private int swap8(int b) { return  ((b << 3) | (b >>> 3)); }

	public boolean isRunning() { return running; }
	public void setRunning(boolean running) { this.running = running; }
	
	private void maskRegs() {
		A &= 0xFF;
		B &= 0xFF;
		C &= 0xFF;
		D &= 0xFF;
		E &= 0xFF;
		setF(getF() & 0xFF);
		H &= 0xFF;
		L &= 0xFF;
		SP &= 0xFFFF;
		PC &= 0xFFFF;
	}
}
