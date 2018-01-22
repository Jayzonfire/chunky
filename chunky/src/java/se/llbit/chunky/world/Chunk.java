/* Copyright (c) 2010-2016 Jesper Öqvist <jesper@llbit.se>
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
package se.llbit.chunky.world;

import se.llbit.chunky.block.Block;
import se.llbit.chunky.map.AbstractLayer;
import se.llbit.chunky.map.BiomeLayer;
import se.llbit.chunky.map.BlockLayer;
import se.llbit.chunky.map.CaveLayer;
import se.llbit.chunky.map.CorruptLayer;
import se.llbit.chunky.map.MapTile;
import se.llbit.chunky.map.SurfaceLayer;
import se.llbit.chunky.map.UnknownLayer;
import se.llbit.chunky.map.WorldMapLoader;
import se.llbit.chunky.ui.MapViewMode;
import se.llbit.log.Log;
import se.llbit.nbt.Tag;
import se.llbit.nbt.CompoundTag;
import se.llbit.nbt.ErrorTag;
import se.llbit.nbt.ListTag;
import se.llbit.nbt.NamedTag;
import se.llbit.nbt.SpecificTag;
import se.llbit.util.NotNull;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * This class represents a loaded or not-yet-loaded chunk in the world.
 * <p>
 * If the chunk is not yet loaded the loadedLayer field is equal to -1.
 *
 * @author Jesper Öqvist (jesper@llbit.se)
 */
public class Chunk {

  public static final String LEVEL_HEIGHTMAP = ".Level.HeightMap";
  public static final String LEVEL_SECTIONS = ".Level.Sections";
  public static final String LEVEL_BIOMES = ".Level.Biomes";
  private static final String LEVEL_ENTITIES = ".Level.Entities";
  private static final String LEVEL_TILEENTITIES = ".Level.TileEntities";

  /** Chunk width. */
  public static final int X_MAX = 16;

  /** Chunk height. */
  public static final int Y_MAX = 256;

  /** Chunk depth. */
  public static final int Z_MAX = 16;

  private static final int SECTION_Y_MAX = 16;
  private static final int SECTION_BYTES = X_MAX * SECTION_Y_MAX * Z_MAX;
  private static final int SECTION_HALF_NIBBLES = SECTION_BYTES / 2;
  private static final int CHUNK_BYTES = X_MAX * Y_MAX * Z_MAX;

  public static final int BLOCK_LAYER = 1 << 0;
  public static final int SURFACE_LAYER = 1 << 1;
  public static final int CAVE_LAYER = 1 << 2;
  public static final int BIOME_LAYER = 1 << 3;

  private final ChunkPosition position;
  private int loadedLayer = -1;
  protected volatile AbstractLayer layer = UnknownLayer.INSTANCE;
  protected volatile AbstractLayer surface = UnknownLayer.INSTANCE;
  protected volatile AbstractLayer caves = UnknownLayer.INSTANCE;
  protected volatile AbstractLayer biomes = UnknownLayer.INSTANCE;

  private final World world;

  private int dataTimestamp = 0;
  private int layerTimestamp = 0;
  private int surfaceTimestamp = 0;
  private int cavesTimestamp = 0;
  private int biomesTimestamp = 0;

  public Chunk(ChunkPosition pos, World world) {
    this.world = world;
    this.position = pos;
  }

  public void renderLayer(MapTile tile) {
    layer.render(tile);
  }

  public int layerColor() {
    return layer.getAvgColor();
  }

  public void renderSurface(MapTile tile) {
    surface.render(tile);
  }

  public int surfaceColor() {
    return surface.getAvgColor();
  }

  public void renderCaves(MapTile tile) {
    caves.render(tile);
  }

  public int caveColor() {
    return caves.getAvgColor();
  }

  public void renderBiomes(MapTile tile) {
    biomes.render(tile);
  }

  public int biomeColor() {
    return biomes.getAvgColor();
  }

  /**
   * @param request fresh request set
   * @return loaded data, or null if something went wrong
   */
  private Map<String, Tag> getChunkData(Set<String> request) {
    Region region = world.getRegion(position.getRegionPosition());
    ChunkDataSource data = region.getChunkData(position);
    dataTimestamp = data.timestamp;
    if (data.inputStream != null) {
      try (DataInputStream in = data.inputStream) {
        Map<String, Tag> result = NamedTag.quickParse(in, request);
        for (String key : request) {
          if (!result.containsKey(key)) {
            result.put(key, new ErrorTag(""));
          }
        }
        return result;
      } catch (IOException e) {
        // Ignored.
      }
    }
    return null;
  }

  /**
   * Reset the rendered layers in this chunk.
   */
  public synchronized void reset() {
    layer = UnknownLayer.INSTANCE;
    caves = UnknownLayer.INSTANCE;
    surface = UnknownLayer.INSTANCE;
  }

  /**
   * @return The position of this chunk
   */
  public ChunkPosition getPosition() {
    return position;
  }

  /**
   * Render block highlight.
   */
  public void renderHighlight(MapTile tile, Block hlBlock, int hlColor) {
    layer.renderHighlight(tile, hlBlock, hlColor);
  }

  /**
   * Parse the chunk from the region file and render the current
   * layer, surface and cave maps.
   */
  public synchronized void loadChunk(WorldMapLoader loader) {

    int requestedLayer = world.currentLayer();
    MapViewMode renderer = loader.getChunkRenderer();
    ChunkView view = loader.getMapView();

    if (!shouldReloadChunk(renderer, view, requestedLayer)) {
      return;
    }

    loadedLayer = requestedLayer;

    Map<String, Tag> data = getChunkData(renderer.getRequest(view));

    int layers = renderer.getLayers(view);
    if ((layers & BLOCK_LAYER) != 0) {
      layerTimestamp = dataTimestamp;
      loadLayer(data, requestedLayer);
    }
    if ((layers & SURFACE_LAYER) != 0) {
      surfaceTimestamp = dataTimestamp;
      loadSurface(data);
    }
    if ((layers & BIOME_LAYER) != 0) {
      biomesTimestamp = dataTimestamp;
      loadBiomes(data);
    }
    if ((layers & CAVE_LAYER) != 0) {
      cavesTimestamp = dataTimestamp;
      loadCaves(data);
    }

    world.chunkUpdated(position);
  }

  private void loadSurface(Map<String, Tag> data) {
    if (data == null) {
      surface = CorruptLayer.INSTANCE;
      return;
    }

    Heightmap heightmap = world.heightmap();
    Tag sections = data.get(LEVEL_SECTIONS);
    if (sections.isList()) {
      int[] heightmapData = extractHeightmapData(data);
      byte[] biomeData = extractBiomeData(data);
      byte[] chunkData = new byte[CHUNK_BYTES];
      byte[] blockData = new byte[CHUNK_BYTES];
      extractChunkData(data, chunkData, blockData);
      updateHeightmap(heightmap, position, chunkData, heightmapData);
      surface = new SurfaceLayer(world.currentDimension(), chunkData, biomeData, blockData);
      queueTopography();
    } else {
      surface = CorruptLayer.INSTANCE;
    }
  }

  private void loadBiomes(Map<String, Tag> data) {
    if (data == null) {
      biomes = CorruptLayer.INSTANCE;
    } else {
      biomes = new BiomeLayer(extractBiomeData(data));
    }
  }

  private void loadLayer(Map<String, Tag> data, int requestedLayer) {
    if (data == null) {
      layer = CorruptLayer.INSTANCE;
      return;
    }

    Tag sections = data.get(LEVEL_SECTIONS);
    if (sections.isList()) {
      byte[] biomeData = extractBiomeData(data);
      byte[] chunkData = new byte[CHUNK_BYTES];
      extractChunkData(data, chunkData, new byte[CHUNK_BYTES]);
      layer = new BlockLayer(chunkData, biomeData, requestedLayer);
    } else {
      layer = CorruptLayer.INSTANCE;
    }
  }

  private void loadCaves(Map<String, Tag> data) {
    if (data == null) {
      caves = CorruptLayer.INSTANCE;
      return;
    }

    Tag sections = data.get(LEVEL_SECTIONS);
    if (sections.isList()) {
      int[] heightmapData = extractHeightmapData(data);
      byte[] chunkData = new byte[CHUNK_BYTES];
      extractChunkData(data, chunkData, new byte[CHUNK_BYTES]);
      caves = new CaveLayer(chunkData, heightmapData);
    } else {
      caves = CorruptLayer.INSTANCE;
    }
  }

  private byte[] extractBiomeData(@NotNull Map<String, Tag> data) {
    Tag biomesTag = data.get(LEVEL_BIOMES);
    if (biomesTag.isByteArray(X_MAX * Z_MAX)) {
      return biomesTag.byteArray();
    } else {
      return new byte[X_MAX * Z_MAX];
    }
  }

  private int[] extractHeightmapData(@NotNull Map<String, Tag> data) {
    Tag heightmapTag = data.get(LEVEL_HEIGHTMAP);
    if (heightmapTag.isIntArray(X_MAX * Z_MAX)) {
      return heightmapTag.intArray();
    } else {
      int[] fallback = new int[X_MAX * Z_MAX];
      for (int i = 0; i < fallback.length; ++i) {
        fallback[i] = Y_MAX - 1;
      }
      return fallback;
    }
  }

  private void extractChunkData(@NotNull Map<String, Tag> data, @NotNull byte[] blocks,
      @NotNull byte[] blockData) {
    Tag sections = data.get(LEVEL_SECTIONS);
    if (sections.isList()) {
      for (SpecificTag section : ((ListTag) sections)) {
        Tag yTag = section.get("Y");
        int yOffset = yTag.byteValue() & 0xFF;
        Tag blockStates = section.get("BlockStates");
        if (blockStates.isLongArray(1)) {
          ListTag palette = section.get("Palette").asList();
          int[] bp = new int[palette.size()];
          int[] bd = new int[palette.size()];
          int paletteIndex = 0;
          for (Tag item : palette.asList()) {
            String name = item.asCompound().get("Name").stringValue("");
            if (name.startsWith("minecraft:")) {
              name = name.substring(10);
              Block block;
              if (Block.nameMap.containsKey(name)) {
                block = Block.nameMap.get(name);
              } else {
                switch (name) {
                  case "infested_stone":
                    block = Block.HIDDENSILVERFISH;
                  case "granite":
                    block = Block.STONE;
                    bd[paletteIndex] = 1;
                    break;
                  case "diorite":
                    block = Block.STONE;
                    bd[paletteIndex] = 3;
                    break;
                  case "andesite":
                    block = Block.STONE;
                    bd[paletteIndex] = 5;
                    break;
                  case "grass_block":
                    block = Block.GRASS;
                    break;
                  default:
                    Log.info("Unknown block ID: " + name);
                    block = Block.GOLDBLOCK;
                }
              }
              bp[paletteIndex++] = block.id;
            }
          }
          long[] bs = blockStates.longArray();
          if (bs.length != 256 && bs.length != 320) {
            Log.info("bs.length: " + bs.length);
          }
          int offset = SECTION_BYTES * yOffset;
          for (int i = 0; i < bs.length; ++i) {
            long l = bs[i];
            int b0 = (int) (l >>> 56);
            int b1 = (int) (l >>> 48) & 0xFF;
            int b2 = (int) (l >>> 40) & 0xFF;
            int b3 = (int) (l >>> 32) & 0xFF;
            int b4 = (int) (l >>> 24) & 0xFF;
            int b5 = (int) (l >>> 16) & 0xFF;
            int b6 = (int) (l >>> 8) & 0xFF;
            int b7 = (int) l & 0xFF;
            if (b0 < bp.length) {
              blocks[offset] = (byte) bp[b0];
              blockData[offset] = (byte) bd[b0];
            }
            if (b1 < bp.length) {
              blocks[offset + 1] = (byte) bp[b1];
              blockData[offset + 1] = (byte) bd[b1];
            }
            if (b2 < bp.length) {
              blocks[offset + 2] = (byte) bp[b2];
              blockData[offset + 2] = (byte) bd[b2];
            }
            if (b3 < bp.length) {
              blocks[offset + 3] = (byte) bp[b3];
              blockData[offset + 3] = (byte) bd[b3];
            }
            if (b4 < bp.length) {
              blocks[offset + 4] = (byte) bp[b4];
              blockData[offset + 4] = (byte) bd[b4];
            }
            if (b5 < bp.length) {
              blocks[offset + 5] = (byte) bp[b5];
              blockData[offset + 5] = (byte) bd[b5];
            }
            if (b6 < bp.length) {
              blocks[offset + 6] = (byte) bp[b6];
              blockData[offset + 6] = (byte) bd[b6];
            }
            if (b7 < bp.length) {
              blocks[offset + 7] = (byte) bp[b7];
              blockData[offset + 7] = (byte) bd[b7];
            }
            offset += 8;
          }
        } else {
          Tag blocksTag = section.get("Blocks");
          if (blocksTag.isByteArray(SECTION_BYTES)) {
            System.arraycopy(blocksTag.byteArray(), 0, blocks, SECTION_BYTES * yOffset,
                SECTION_BYTES);
          } else {
            Log.info("no blocks");
          }
          Tag dataTag = section.get("Data");
          if (dataTag.isByteArray(SECTION_HALF_NIBBLES)) {
            System.arraycopy(dataTag.byteArray(), 0, blockData, SECTION_HALF_NIBBLES * yOffset,
                SECTION_HALF_NIBBLES);
          }
        }
      }
    }
  }

  /**
   * Load heightmap information from a chunk heightmap array
   * and insert into a quadtree.
   */
  public static void updateHeightmap(Heightmap heightmap, ChunkPosition pos, byte[] blocksArray,
      int[] chunkHeightmap) {
    for (int x = 0; x < 16; ++x) {
      for (int z = 0; z < 16; ++z) {
        int y = chunkHeightmap[z * 16 + x];
        y = Math.max(1, y - 1);
        for (; y > 1; --y) {
          Block block = Block.get(blocksArray[Chunk.chunkIndex(x, y, z)]);
          if (block != Block.AIR && !block.isWater())
            break;
        }
        heightmap.set(y, pos.x * 16 + x, pos.z * 16 + z);
      }
    }
  }

  private boolean shouldReloadChunk(MapViewMode renderer, ChunkView view, int requestedLayer) {
    int timestamp = Integer.MAX_VALUE;
    int layers = renderer.getLayers(view);
    if ((layers & BLOCK_LAYER) != 0) {
      if (requestedLayer != loadedLayer) {
        return true;
      }
      timestamp = layerTimestamp;
    }
    if ((layers & SURFACE_LAYER) != 0) {
      timestamp = Math.min(timestamp, surfaceTimestamp);
    }
    if ((layers & BIOME_LAYER) != 0) {
      timestamp = Math.min(timestamp, biomesTimestamp);
    }
    if ((layers & CAVE_LAYER) != 0) {
      timestamp = Math.min(timestamp, cavesTimestamp);
    }
    if (timestamp == 0) {
      return true;
    }
    Region region = world.getRegion(position.getRegionPosition());
    return region.chunkChangedSince(position, timestamp);
  }

  private void queueTopography() {
    for (int x = -1; x <= 1; ++x) {
      for (int z = -1; z <= 1; ++z) {
        ChunkPosition pos = ChunkPosition.get(position.x + x, position.z + z);
        Chunk chunk = world.getChunk(pos);
        if (!chunk.isEmpty()) {
          world.chunkTopographyUpdated(chunk);
        }
      }
    }
  }

  /**
   * @return <code>true</code> if this is an empty (non-existing) chunk
   */
  public boolean isEmpty() {
    return false;
  }

  /**
   * Render the topography of this chunk.
   */
  public synchronized void renderTopography() {
    surface.renderTopography(position, world.heightmap());
    world.chunkUpdated(position);
  }

  /**
   * Load the block data for this chunk.
   */
  public synchronized void getBlockData(byte[] blocks, byte[] blockData, byte[] biomes,
      Collection<CompoundTag> tileEntities, Collection<CompoundTag> entities) {

    for (int i = 0; i < CHUNK_BYTES; ++i) {
      blocks[i] = 0;
    }

    for (int i = 0; i < X_MAX * Z_MAX; ++i) {
      biomes[i] = 0;
    }

    for (int i = 0; i < (CHUNK_BYTES) / 2; ++i) {
      blockData[i] = 0;
    }

    Set<String> request = new HashSet<>();
    request.add(LEVEL_SECTIONS);
    request.add(LEVEL_BIOMES);
    request.add(LEVEL_ENTITIES);
    request.add(LEVEL_TILEENTITIES);
    Map<String, Tag> data = getChunkData(request);
    // TODO: improve error handling here.
    if (data == null) {
      return;
    }
    Tag sections = data.get(LEVEL_SECTIONS);
    Tag biomesTag = data.get(LEVEL_BIOMES);
    Tag entitiesTag = data.get(LEVEL_ENTITIES);
    Tag tileEntitiesTag = data.get(LEVEL_TILEENTITIES);
    if (sections.isList() && biomesTag.isByteArray(X_MAX * Z_MAX) && tileEntitiesTag.isList()
        && entitiesTag.isList()) {
      byte[] chunkBiomes = extractBiomeData(data);
      System.arraycopy(chunkBiomes, 0, biomes, 0, chunkBiomes.length);
      extractChunkData(data, blocks, blockData);
      ListTag list = (ListTag) entitiesTag;
      for (SpecificTag tag : list) {
        if (tag.isCompoundTag())
          entities.add((CompoundTag) tag);
      }
      list = (ListTag) tileEntitiesTag;
      for (SpecificTag tag : list) {
        if (tag.isCompoundTag())
          tileEntities.add((CompoundTag) tag);
      }
    }
  }

  /**
   * @return Integer index into a chunk YXZ array
   */
  public static int chunkIndex(int x, int y, int z) {
    return x + Chunk.X_MAX * (z + Chunk.Z_MAX * y);
  }

  /**
   * @return Integer index into a chunk XZ array
   */
  public static int chunkXZIndex(int x, int z) {
    return x + Chunk.X_MAX * z;
  }

  @Override public String toString() {
    return "Chunk: " + position.toString();
  }

  public String biomeAt(int blockX, int blockZ) {
    if (biomes instanceof BiomeLayer) {
      BiomeLayer biomeLayer = (BiomeLayer) biomes;
      return biomeLayer.biomeAt(blockX, blockZ);
    } else {
      return "unknown";
    }
  }
}
