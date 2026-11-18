package `in`.yourview.yvgpscam.map

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import `in`.yourview.yvgpscam.data.model.MapStyle
import com.google.android.gms.maps.model.Tile
import com.google.android.gms.maps.model.TileProvider
import java.io.ByteArrayOutputStream

data class CachedTileBitmapResult(
    val bitmap: Bitmap,
    val isFromAncestorFallback: Boolean
)

/**
 * Google Maps SDK [TileProvider] backed by [OfflineMapRepository]'s persistent disk tile cache.
 *
 * Lookup order for each (x, y, zoom) tile request:
 * 1. Exact tile already persisted on disk (instant, works offline).
 * 2. Live network fetch + atomic disk persistence (only when [allowNetwork] & repository circuit breaker allow).
 * 3. Ancestor zoom level tile on disk (z - 1 .. z - 4), cropped and upscaled to 256x256 PNG so
 *    previously visited areas remain visible even across zoom changes without connectivity.
 * 4. [TileProvider.NO_TILE] when unvisited and offline.
 */
class DiskCachedMapTileProvider(
    private val offlineMapRepository: OfflineMapRepository,
    private val mapStyle: MapStyle = MapStyle.STANDARD,
    private val allowNetwork: () -> Boolean = { offlineMapRepository.isNetworkUsableForTiles() },
    private val tileSizePx: Int = DEFAULT_TILE_SIZE_PX
) : TileProvider {

    override fun getTile(x: Int, y: Int, zoom: Int): Tile {
        if (zoom !in 0..20) return TileProvider.NO_TILE
        val maxIndex = 1 shl zoom
        if (y !in 0 until maxIndex) return TileProvider.NO_TILE
        val wrappedX = ((x % maxIndex) + maxIndex) % maxIndex
        val networkAllowed = allowNetwork() && offlineMapRepository.isNetworkUsableForTiles()

        // 1. Exact disk cache lookup
        val cachedFile = offlineMapRepository.getTileFile(zoom, wrappedX, y, mapStyle)
        if (cachedFile.exists() && cachedFile.length() > 0L) {
            val rawBytes = runCatching { cachedFile.readBytes() }.getOrNull()
            if (rawBytes != null && rawBytes.isNotEmpty()) {
                val finalBytes = if (mapStyle == MapStyle.HYBRID) {
                    compositeHybridOverlayBytes(rawBytes, zoom, wrappedX, y, networkAllowed) ?: rawBytes
                } else {
                    rawBytes
                }
                return Tile(tileSizePx, tileSizePx, finalBytes)
            }
        }

        // 2. Network fetch + persist to disk if online
        if (networkAllowed) {
            val fetched = offlineMapRepository.fetchTileFromNetworkAndCache(zoom, wrappedX, y, mapStyle)
            if (fetched != null && fetched.isNotEmpty()) {
                val finalBytes = if (mapStyle == MapStyle.HYBRID) {
                    compositeHybridOverlayBytes(fetched, zoom, wrappedX, y, allowNet = true) ?: fetched
                } else {
                    fetched
                }
                return Tile(tileSizePx, tileSizePx, finalBytes)
            }
        }

        // 3. Ancestor zoom disk fallback (renders previously visited areas across zoom steps)
        val ancestorBitmap = offlineMapRepository.getBestAvailableCachedTileBitmap(
            z = zoom,
            x = wrappedX,
            y = y,
            mapStyle = mapStyle
        )
        if (ancestorBitmap != null) {
            val encoded = encodeBitmapAsPng(ancestorBitmap)
            ancestorBitmap.recycle()
            if (encoded != null) {
                return Tile(tileSizePx, tileSizePx, encoded)
            }
        }

        return TileProvider.NO_TILE
    }

    /**
     * Direct Bitmap helper shared with [MiniMapTileRenderer] and [InAppInteractiveTileMap]
     * so mini-map stamps and interactive map views leverage the same disk cache & fallback rules.
     */
    fun loadTileBitmap(
        zoom: Int,
        x: Int,
        y: Int,
        offlineOnly: Boolean
    ): CachedTileBitmapResult? {
        if (zoom !in 0..20) return null
        val maxIndex = 1 shl zoom
        if (y !in 0 until maxIndex) return null
        val wrappedX = ((x % maxIndex) + maxIndex) % maxIndex

        val cachedFile = offlineMapRepository.getTileFile(zoom, wrappedX, y, mapStyle)
        if (cachedFile.exists() && cachedFile.length() > 0L) {
            BitmapFactory.decodeFile(cachedFile.absolutePath)?.let { bmp ->
                return CachedTileBitmapResult(bitmap = bmp, isFromAncestorFallback = false)
            }
        }

        val networkAllowed = !offlineOnly && allowNetwork() && offlineMapRepository.isNetworkUsableForTiles()
        if (networkAllowed) {
            val bytes = offlineMapRepository.fetchTileFromNetworkAndCache(zoom, wrappedX, y, mapStyle)
            if (bytes != null) {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { bmp ->
                    return CachedTileBitmapResult(bitmap = bmp, isFromAncestorFallback = false)
                }
            }
        }

        val ancestor = offlineMapRepository.getBestAvailableCachedTileBitmap(
            z = zoom,
            x = wrappedX,
            y = y,
            mapStyle = mapStyle
        ) ?: return null
        return CachedTileBitmapResult(bitmap = ancestor, isFromAncestorFallback = true)
    }

    private fun compositeHybridOverlayBytes(
        baseBytes: ByteArray,
        z: Int,
        x: Int,
        y: Int,
        allowNet: Boolean
    ): ByteArray? {
        val overlayBytes = offlineMapRepository.fetchHybridOverlayTile(z, x, y, allowNetwork = allowNet)
            ?: return null
        val baseBmp = BitmapFactory.decodeByteArray(baseBytes, 0, baseBytes.size) ?: return null
        val overlayBmp = BitmapFactory.decodeByteArray(overlayBytes, 0, overlayBytes.size)
        if (overlayBmp == null) {
            baseBmp.recycle()
            return null
        }
        return try {
            val combined = Bitmap.createBitmap(tileSizePx, tileSizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(combined)
            val dest = android.graphics.Rect(0, 0, tileSizePx, tileSizePx)
            canvas.drawBitmap(baseBmp, null, dest, null)
            canvas.drawBitmap(overlayBmp, null, dest, null)
            val out = encodeBitmapAsPng(combined)
            combined.recycle()
            out
        } finally {
            baseBmp.recycle()
            overlayBmp.recycle()
        }
    }

    private fun encodeBitmapAsPng(bitmap: Bitmap): ByteArray? {
        return runCatching {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.toByteArray()
        }.getOrNull()
    }

    companion object {
        const val DEFAULT_TILE_SIZE_PX = 256
    }
}
