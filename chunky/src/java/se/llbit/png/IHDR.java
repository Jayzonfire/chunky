/* Copyright (c) 2012-2015 Jesper Öqvist <jesper@llbit.se>
 *
 * This file is part of Chunky.
 *
 * Chunky is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Chunky is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with Chunky.  If not, see <http://www.gnu.org/licenses/>.
 */
package se.llbit.png;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * @author Jesper Öqvist <jesper.oqvist@cs.lth.se>
 */
public class IHDR extends PngChunk {

	/**
	 * The PNG chunk type identifier
	 */
	public static final int CHUNK_TYPE = 0x49484452;

	private static final int BIT_DEPTH = 8;
	protected static final int COLOR_TYPE_RGB = 2; // Each pixel is an RGB triple.
	protected static final int COLOR_TYPE_RGBA = 6; // Each pixel is an RGBA quad.
	private static final int COMPRESSION_METHOD = 0; // Deflate/inflate compression.
	private static final int FILTER_METHOD = 0;
	private static final int INTERLACE_METHOD = 0;
	private final int colorType;
	private int crc;
	private final int width;
	private final int height;

	public IHDR(int width, int height) {
		this(width, height, COLOR_TYPE_RGB, BIT_DEPTH);
	}

	public IHDR(int width, int height, int colorType) {
		this(width, height, colorType, BIT_DEPTH);
	}

	public IHDR(int width, int height, int colorType, int bitDepth) {
		this.width = width;
		this.height = height;
		this.colorType = colorType;
	}

	@Override
	public int getChunkType() {
		return CHUNK_TYPE;
	}

	@Override
	protected void writeChunkData(DataOutputStream out) throws IOException {
		CrcOutputStream crcOutputStream = new CrcOutputStream();
		DataOutputStream crcOut = new DataOutputStream(crcOutputStream);

		crcOut.writeInt(CHUNK_TYPE);

		crcOut.writeInt(width);
		out.writeInt(width);

		crcOut.writeInt(height);
		out.writeInt(height);

		crcOut.writeByte(BIT_DEPTH);
		out.writeByte(BIT_DEPTH);

		crcOut.writeByte(colorType);
		out.writeByte(colorType);

		crcOut.writeByte(COMPRESSION_METHOD);
		out.writeByte(COMPRESSION_METHOD);

		crcOut.writeByte(FILTER_METHOD);
		out.writeByte(FILTER_METHOD);

		crcOut.writeByte(INTERLACE_METHOD);
		out.writeByte(INTERLACE_METHOD);

		crc = crcOutputStream.getCRC();
		crcOut.close();
	}

	@Override
	public int getChunkLength() {
		return 13;
	}

	@Override
	public int getChunkCRC() {
		return crc;
	}


}
