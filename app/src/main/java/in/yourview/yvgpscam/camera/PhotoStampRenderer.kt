package `in`.yourview.yvgpscam.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.res.ResourcesCompat
import `in`.yourview.yvgpscam.R
import `in`.yourview.yvgpscam.data.model.GpsTelemetry
import `in`.yourview.yvgpscam.data.model.StampConfig
import `in`.yourview.yvgpscam.data.model.StampFont
import `in`.yourview.yvgpscam.data.model.StampLayoutTemplate
import `in`.yourview.yvgpscam.data.model.StampPosition
import `in`.yourview.yvgpscam.data.model.StampTemplate
import `in`.yourview.yvgpscam.util.QrCodeHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

class PhotoStampRenderer(private val context: Context) {

    private val typefaceCache = ConcurrentHashMap<Int, Typeface>()

    fun stampBitmap(
        source: Bitmap,
        telemetry: GpsTelemetry,
        config: StampConfig,
        miniMapBitmap: Bitmap?,
        capturedAtMillis: Long = System.currentTimeMillis(),
        isFrontCamera: Boolean = false,
        deviceOrientationDegrees: Int = 0
    ): Bitmap {
        val output = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        val w = output.width.toFloat()
        val h = output.height.toFloat()
        val shortEdge = min(w, h)
        val outerMargin = shortEdge * 0.028f

        val layout = prepareStampCardLayout(
            frameWidth = w,
            frameHeight = h,
            telemetry = telemetry,
            config = config,
            miniMapBitmap = miniMapBitmap,
            capturedAtMillis = capturedAtMillis
        )

        val cardLeft = when (config.stampPosition) {
            StampPosition.TOP_LEFT, StampPosition.BOTTOM_LEFT -> outerMargin
            StampPosition.TOP_RIGHT, StampPosition.BOTTOM_RIGHT -> (w - outerMargin - layout.cardWidth).coerceAtLeast(outerMargin)
            StampPosition.TOP_CENTER, StampPosition.BOTTOM_CENTER -> ((w - layout.cardWidth) / 2f).coerceAtLeast(outerMargin)
        }

        val cardTop = when (config.stampPosition) {
            StampPosition.TOP_LEFT, StampPosition.TOP_CENTER, StampPosition.TOP_RIGHT -> outerMargin
            StampPosition.BOTTOM_LEFT, StampPosition.BOTTOM_CENTER, StampPosition.BOTTOM_RIGHT ->
                (h - outerMargin - layout.cardHeight).coerceAtLeast(outerMargin)
        }

        drawStampCardOnCanvas(
            canvas = canvas,
            cardLeft = cardLeft,
            cardTop = cardTop,
            layout = layout,
            telemetry = telemetry,
            config = config,
            isFrontCamera = isFrontCamera
        )

        return output
    }

    /**
     * Renders the stamp card onto a tightly sized transparent ARGB_8888 bitmap
     * suitable for Media3 video overlay compositing.
     */
    fun renderStampCardOnly(
        frameWidth: Int,
        frameHeight: Int,
        telemetry: GpsTelemetry,
        config: StampConfig,
        miniMapBitmap: Bitmap?,
        capturedAtMillis: Long = System.currentTimeMillis(),
        isFrontCamera: Boolean = false
    ): Bitmap {
        val safeW = frameWidth.coerceAtLeast(480).toFloat()
        val safeH = frameHeight.coerceAtLeast(480).toFloat()
        val layout = prepareStampCardLayout(
            frameWidth = safeW,
            frameHeight = safeH,
            telemetry = telemetry,
            config = config,
            miniMapBitmap = miniMapBitmap,
            capturedAtMillis = capturedAtMillis
        )
        val outW = kotlin.math.ceil(layout.cardWidth.toDouble()).toInt().coerceAtLeast(16)
        val outH = kotlin.math.ceil(layout.cardHeight.toDouble()).toInt().coerceAtLeast(16)
        val cardBitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        cardBitmap.eraseColor(Color.TRANSPARENT)
        val canvas = Canvas(cardBitmap)
        drawStampCardOnCanvas(
            canvas = canvas,
            cardLeft = 0f,
            cardTop = 0f,
            layout = layout,
            telemetry = telemetry,
            config = config,
            isFrontCamera = isFrontCamera
        )
        return cardBitmap
    }

    private fun prepareStampCardLayout(
        frameWidth: Float,
        frameHeight: Float,
        telemetry: GpsTelemetry,
        config: StampConfig,
        miniMapBitmap: Bitmap?,
        capturedAtMillis: Long
    ): PreparedStampCardLayout {
        val shortEdge = min(frameWidth, frameHeight)
        val baseScale = (shortEdge / 1080f).coerceAtLeast(0.45f) * config.stampSize.scaleFactor
        val outerMargin = shortEdge * 0.028f

        val typefacePrimary = loadTypeface(config.stampFont)
        val typefaceBold = Typeface.create(typefacePrimary, Typeface.BOLD)
        val typefaceMono = loadFontCached(R.font.jetbrains_mono) ?: Typeface.MONOSPACE

        val accentInt = config.accentColor.argbInt
        val isLight = config.template == StampTemplate.CLASSIC_WHITE ||
            config.layoutTemplate == StampLayoutTemplate.CLASSIC
        val alphaInt = (config.stampOpacity.coerceIn(0.35f, 1.0f) * 255f).roundToInt().coerceIn(110, 255)
        val foregroundAlphaInt = (config.stampOpacity.coerceIn(0.35f, 1.0f).coerceAtLeast(0.65f) * 255f)
            .roundToInt().coerceIn(140, 255)
        val logoAlphaInt = (config.logoOpacity.coerceIn(0.25f, 1.0f) * 255f).roundToInt().coerceIn(64, 255)

        val cardBgColor = when {
            isLight -> Color.argb(alphaInt, 246, 249, 251)
            config.template == StampTemplate.MINIMAL_BAR -> Color.argb((alphaInt * 0.85f).roundToInt().coerceAtLeast(100), 9, 13, 17)
            config.template == StampTemplate.CORNER_BADGE -> Color.argb(alphaInt, 13, 20, 26)
            else -> Color.argb((alphaInt * 0.86f).roundToInt().coerceAtLeast(110), 11, 14, 18)
        }
        val titleTextColor = if (isLight) {
            Color.argb(foregroundAlphaInt, 14, 23, 28)
        } else {
            Color.argb(foregroundAlphaInt, 247, 250, 252)
        }
        val bodyTextColor = if (isLight) {
            Color.argb(foregroundAlphaInt, 55, 72, 86)
        } else {
            Color.argb(foregroundAlphaInt, 208, 220, 229)
        }

        val maxCardWidth = when (config.template) {
            StampTemplate.CORNER_BADGE -> (frameWidth - outerMargin * 2f) * 0.78f
            StampTemplate.MINIMAL_BAR -> frameWidth - outerMargin * 2f
            else -> min(frameWidth - outerMargin * 2f, shortEdge * 0.98f * config.stampSize.scaleFactor.coerceAtMost(1.04f))
        }

        val cardPadding = 20f * baseScale
        val cornerRadius = 22f * baseScale

        val useQrPanel = config.layoutTemplate == StampLayoutTemplate.SCAN_LOCATION
        val useCompassPanel = config.layoutTemplate == StampLayoutTemplate.NAVIGATIONAL_COMPASS

        val qrBitmap = if (useQrPanel) {
            val url = QrCodeHelper.buildLocationQrUrl(telemetry.latitude, telemetry.longitude)
            QrCodeHelper.generateQrBitmap(url, 220)
        } else null

        val activeMiniMap = when {
            useQrPanel -> null
            config.showMiniMap && config.template != StampTemplate.MINIMAL_BAR -> miniMapBitmap
            else -> null
        }

        val leftModuleWidth = when {
            useQrPanel -> 182f * baseScale
            useCompassPanel -> 182f * baseScale
            activeMiniMap != null -> when (config.template) {
                StampTemplate.CORNER_BADGE -> 140f * baseScale
                else -> 178f * baseScale
            }.coerceAtMost(maxCardWidth * 0.24f)
            else -> 0f
        }
        val leftModuleGap = if (leftModuleWidth > 0f) 18f * baseScale else 0f

        val brandColWidth = 148f * baseScale
        val brandGap = 14f * baseScale

        val headlineFontSize = 31f * baseScale
        val bodyFontSize = 21f * baseScale
        val metaFontSize = 20f * baseScale
        val lineSpacing = 7f * baseScale

        val textLines = mutableListOf<StampTextLine>()

        if (config.layoutTemplate == StampLayoutTemplate.REPORTING) {
            val statusPart = "[${config.reportingLabel.ifBlank { "Check In" }.uppercase(Locale.US)}]"
            val fullStatus = if (config.showNumbering) "$statusPart  #${config.photoSequenceNumber}" else statusPart
            textLines.add(
                StampTextLine(
                    text = fullStatus,
                    size = bodyFontSize,
                    color = Color.rgb(16, 224, 154),
                    typeface = typefaceBold
                )
            )
        } else if (config.layoutTemplate == StampLayoutTemplate.DATETIME) {
            if (config.showDateTime) {
                val timeOnly = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(capturedAtMillis))
                val dateOnly = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault()).format(Date(capturedAtMillis))
                textLines.add(
                    StampTextLine(
                        text = "$timeOnly  |  $dateOnly",
                        size = headlineFontSize * 1.06f,
                        color = Color.rgb(245, 197, 24),
                        typeface = typefaceBold
                    )
                )
            }
            val extras = buildList {
                if (config.showTimeZone) add(telemetry.formatTimeZoneOffset(capturedAtMillis))
                if (config.showNumbering) add("#${config.photoSequenceNumber}")
            }
            if (extras.isNotEmpty()) {
                textLines.add(
                    StampTextLine(
                        text = extras.joinToString(" • "),
                        size = bodyFontSize,
                        color = bodyTextColor,
                        typeface = typefacePrimary
                    )
                )
            }
        } else if (config.layoutTemplate == StampLayoutTemplate.NAVIGATIONAL_COMPASS) {
            textLines.add(
                StampTextLine(
                    text = "Facing ${telemetry.compassCardinal} (${telemetry.compassAzimuth.roundToInt()}°)",
                    size = headlineFontSize * 0.94f,
                    color = Color.rgb(245, 197, 24),
                    typeface = typefaceBold
                )
            )
        }

        val flagSuffix = if (config.showCountryFlag && telemetry.countryFlagEmoji.isNotBlank()) {
            " ${telemetry.countryFlagEmoji}"
        } else ""

        if (config.showShortAddress) {
            textLines.add(
                StampTextLine(
                    text = "${telemetry.displayHeadlineAddress}$flagSuffix",
                    size = headlineFontSize,
                    color = titleTextColor,
                    typeface = typefaceBold
                )
            )
        } else if (flagSuffix.isNotBlank()) {
            textLines.add(
                StampTextLine(
                    text = flagSuffix.trim(),
                    size = bodyFontSize,
                    color = titleTextColor,
                    typeface = typefaceBold
                )
            )
        }

        if (config.showAddress && telemetry.addressLine.isNotBlank() && config.template != StampTemplate.CORNER_BADGE) {
            textLines.add(
                StampTextLine(
                    text = telemetry.addressLine,
                    size = bodyFontSize,
                    color = bodyTextColor,
                    typeface = typefacePrimary,
                    maxLines = 2
                )
            )
        }

        val plusPart = if (config.showPlusCode && telemetry.plusCode != null) "Plus Code : ${telemetry.plusCode}" else ""
        val coordPart = if (config.showCoordinates) telemetry.formatCoordinates(config.coordinateFormat) else ""
        val combinedCoord = listOf(plusPart, coordPart).filter { it.isNotBlank() }.joinToString("  •  ")
        if (combinedCoord.isNotBlank()) {
            textLines.add(
                StampTextLine(
                    text = combinedCoord,
                    size = bodyFontSize,
                    color = bodyTextColor,
                    typeface = typefacePrimary
                )
            )
        }

        if (config.layoutTemplate != StampLayoutTemplate.DATETIME) {
            val timeParts = buildList {
                if (config.showDateTime) add(telemetry.formatTimestamp(config.dateTimeFormat, capturedAtMillis))
                if (config.showTimeZone) add(telemetry.formatTimeZoneOffset(capturedAtMillis))
                if (config.showNumbering && config.layoutTemplate != StampLayoutTemplate.REPORTING) {
                    add("#${config.photoSequenceNumber}")
                }
            }
            if (timeParts.isNotEmpty()) {
                textLines.add(
                    StampTextLine(
                        text = timeParts.joinToString(" • "),
                        size = bodyFontSize,
                        color = bodyTextColor,
                        typeface = typefacePrimary
                    )
                )
            }
        }

        if (config.showPersonName && config.personName.isNotBlank()) {
            textLines.add(
                StampTextLine(
                    text = "Person : ${config.personName.trim()}",
                    size = bodyFontSize,
                    color = bodyTextColor,
                    typeface = typefacePrimary
                )
            )
        }

        if (config.showNote && config.customNote.isNotBlank()) {
            textLines.add(
                StampTextLine(
                    text = "Note : ${config.customNote.trim()}",
                    size = bodyFontSize,
                    color = titleTextColor,
                    typeface = typefacePrimary
                )
            )
        }

        val metricsList = mutableListOf<Pair<String, Int>>()
        if (config.showCompassAndSpeed) {
            val windOrSpeed = telemetry.weatherWindKmh ?: telemetry.speedKmh
            val speedStr = windOrSpeed?.let { String.format(Locale.US, "%.0f km/h", it) }
                ?: "${telemetry.compassAzimuth.roundToInt()}° ${telemetry.compassCardinal}"
            metricsList.add(speedStr to Color.rgb(56, 189, 248))
            if (windOrSpeed != null) {
                metricsList.add("${telemetry.compassAzimuth.roundToInt()}° ${telemetry.compassCardinal}" to Color.rgb(79, 195, 247))
            }
        }
        if (config.showWeather && telemetry.weatherHumidityPercent != null) {
            metricsList.add("${telemetry.weatherHumidityPercent}%" to Color.rgb(96, 165, 250))
        }
        if (config.showAltitude) {
            val altStr = telemetry.altitudeMeters?.let { String.format(Locale.US, "%.0f m", it) } ?: "-- m"
            metricsList.add(altStr to Color.rgb(245, 197, 24))
        }
        if (config.showMagneticField) {
            val magStr = telemetry.magneticFieldMicroTesla?.let { String.format(Locale.US, "%.0f µT", it) } ?: "-- µT"
            metricsList.add(magStr to Color.rgb(255, 90, 95))
        }
        if (config.showPressure) {
            val pressStr = telemetry.pressureHpa?.let { String.format(Locale.US, "%.0f hPa", it) } ?: "-- hPa"
            metricsList.add(pressStr to Color.rgb(167, 139, 250))
        }
        if (config.showAccuracy) {
            val accStr = telemetry.accuracyMeters?.let { String.format(Locale.US, "±%.0fm", it) } ?: "-- m"
            metricsList.add(accStr to Color.rgb(16, 224, 154))
        }

        val cardWidth = when (config.template) {
            StampTemplate.MINIMAL_BAR -> frameWidth - outerMargin * 2f
            else -> maxCardWidth
        }

        val centerTextMaxW = (cardWidth - cardPadding * 2f - leftModuleWidth - leftModuleGap - brandColWidth - brandGap).coerceAtLeast(140f)

        val wrappedRows = mutableListOf<Pair<String, StampTextLine>>()
        for (line in textLines) {
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = line.size
                typeface = line.typeface
            }
            if (line.maxLines <= 1) {
                val ellipsized = TextUtils.ellipsize(line.text, paint, centerTextMaxW, TextUtils.TruncateAt.END).toString()
                wrappedRows.add(ellipsized to line)
            } else {
                val words = line.text.split(" ")
                val line1 = StringBuilder()
                val line2 = StringBuilder()
                var onSecond = false
                for (word in words) {
                    if (!onSecond) {
                        val candidate = if (line1.isEmpty()) word else "$line1 $word"
                        if (paint.measureText(candidate) <= centerTextMaxW || line1.isEmpty()) {
                            line1.clear().append(candidate)
                        } else {
                            onSecond = true
                            line2.append(word)
                        }
                    } else {
                        if (line2.isEmpty()) line2.append(word) else line2.append(' ').append(word)
                    }
                }
                wrappedRows.add(TextUtils.ellipsize(line1.toString(), paint, centerTextMaxW, TextUtils.TruncateAt.END).toString() to line)
                if (line2.isNotEmpty()) {
                    wrappedRows.add(TextUtils.ellipsize(line2.toString(), paint, centerTextMaxW, TextUtils.TruncateAt.END).toString() to line)
                }
            }
        }

        val textHeight = wrappedRows.sumOf { (_, line) -> (line.size * 1.24f + lineSpacing).toDouble() }.toFloat()
        val logoBadgeSize = 86f * baseScale
        val brandHeight = logoBadgeSize + 64f * baseScale
        val metricsRowHeight = if (metricsList.isNotEmpty()) metaFontSize * 1.6f + 8f * baseScale else 0f
        val contentHeight = maxOf(215f * baseScale, maxOf(textHeight, brandHeight) + metricsRowHeight)
        val cardHeight = (contentHeight + cardPadding * 2f).coerceAtMost(frameHeight * 0.44f)

        return PreparedStampCardLayout(
            baseScale = baseScale,
            isLight = isLight,
            accentInt = accentInt,
            foregroundAlphaInt = foregroundAlphaInt,
            logoAlphaInt = logoAlphaInt,
            cardBgColor = cardBgColor,
            titleTextColor = titleTextColor,
            cardWidth = cardWidth,
            cardHeight = cardHeight,
            cardPadding = cardPadding,
            cornerRadius = cornerRadius,
            useQrPanel = useQrPanel,
            useCompassPanel = useCompassPanel,
            qrBitmap = qrBitmap,
            activeMiniMap = activeMiniMap,
            leftModuleWidth = leftModuleWidth,
            leftModuleGap = leftModuleGap,
            brandColWidth = brandColWidth,
            logoBadgeSize = logoBadgeSize,
            metaFontSize = metaFontSize,
            metricsRowHeight = metricsRowHeight,
            lineSpacing = lineSpacing,
            typefaceBold = typefaceBold,
            typefaceMono = typefaceMono,
            wrappedRows = wrappedRows,
            metricsList = metricsList
        )
    }

    private fun drawStampCardOnCanvas(
        canvas: Canvas,
        cardLeft: Float,
        cardTop: Float,
        layout: PreparedStampCardLayout,
        telemetry: GpsTelemetry,
        config: StampConfig,
        isFrontCamera: Boolean
    ) {
        val baseScale = layout.baseScale
        val cardPadding = layout.cardPadding
        val cardRect = RectF(cardLeft, cardTop, cardLeft + layout.cardWidth, cardTop + layout.cardHeight)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = layout.cardBgColor
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(cardRect, layout.cornerRadius, layout.cornerRadius, bgPaint)

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (layout.isLight) Color.argb(80, 14, 23, 28) else Color.argb(46, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = (1.6f * baseScale).coerceAtLeast(1f)
        }
        canvas.drawRoundRect(cardRect, layout.cornerRadius, layout.cornerRadius, strokePaint)

        val contentLeft = cardRect.left + cardPadding

        // Left module: QR Code, Compass Dial, or Mini-Map
        if (layout.useQrPanel && layout.qrBitmap != null) {
            val qrSize = min(layout.leftModuleWidth, cardRect.height() - cardPadding * 2f - 22f * baseScale)
            val qrRect = RectF(contentLeft, cardRect.top + cardPadding, contentLeft + qrSize, cardRect.top + cardPadding + qrSize)
            drawRoundedBitmap(canvas, layout.qrBitmap, qrRect, 12f * baseScale, 255)
            val qrCapPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(245, 197, 24)
                textSize = 14f * baseScale
                typeface = layout.typefaceMono
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("SCAN LOCATION", qrRect.centerX(), qrRect.bottom + 18f * baseScale, qrCapPaint)
            layout.qrBitmap.recycle()
        } else if (layout.useCompassPanel) {
            val dialRadius = min(layout.leftModuleWidth, cardRect.height() - cardPadding * 2f) * 0.44f
            val dialCenterX = contentLeft + layout.leftModuleWidth / 2f
            val dialCenterY = cardRect.centerY()
            drawCompassDial(
                canvas = canvas,
                centerX = dialCenterX,
                centerY = dialCenterY,
                radius = dialRadius,
                azimuthDegrees = telemetry.compassAzimuth,
                baseScale = baseScale
            )
        } else if (layout.activeMiniMap != null && !layout.activeMiniMap.isRecycled) {
            val mapRect = RectF(
                contentLeft,
                cardRect.top + cardPadding,
                contentLeft + layout.leftModuleWidth,
                cardRect.bottom - cardPadding
            )
            drawRoundedBitmap(
                canvas,
                layout.activeMiniMap,
                mapRect,
                14f * baseScale,
                layout.foregroundAlphaInt.coerceAtLeast(220)
            )
            val mapBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(90, 255, 255, 255)
                style = Paint.Style.STROKE
                strokeWidth = (1.5f * baseScale).coerceAtLeast(1f)
            }
            canvas.drawRoundRect(mapRect, 14f * baseScale, 14f * baseScale, mapBorder)
        }

        val actualInfoLeft = contentLeft + layout.leftModuleWidth + layout.leftModuleGap
        var cursorY = cardRect.top + cardPadding

        val metricsReserve = if (layout.metricsList.isNotEmpty()) layout.metricsRowHeight + 4f * baseScale else 0f
        val maxTextBottom = cardRect.bottom - cardPadding - metricsReserve
        for ((renderedText, line) in layout.wrappedRows) {
            if (cursorY + line.size > maxTextBottom) break
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = line.color
                textSize = line.size
                typeface = line.typeface
            }
            cursorY += line.size
            canvas.drawText(renderedText, actualInfoLeft, cursorY, paint)
            cursorY += layout.lineSpacing + line.size * 0.2f
        }

        // Right Mandatory Brand Column
        val brandColCenterX = cardRect.right - cardPadding - layout.brandColWidth / 2f
        val logoLeft = brandColCenterX - layout.logoBadgeSize / 2f
        val logoTop = cardRect.top + cardPadding + 4f * baseScale
        val logoBmp = LogoBitmapProvider.loadStampLogoBitmap(
            context = context,
            targetSizePx = layout.logoBadgeSize.roundToInt().coerceAtLeast(64)
        )
        val logoRect = RectF(logoLeft, logoTop, logoLeft + layout.logoBadgeSize, logoTop + layout.logoBadgeSize)
        drawRoundedBitmap(canvas, logoBmp, logoRect, 16f * baseScale, layout.logoAlphaInt)
        logoBmp.recycle()

        val brandLabel = config.customBrandLabel.ifBlank { "YVGPSCAM" }
        val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = layout.titleTextColor
            textSize = 18.5f * baseScale
            typeface = layout.typefaceBold
            textAlign = Paint.Align.CENTER
        }
        val brandTextY = logoRect.bottom + 22f * baseScale
        val truncatedBrand = TextUtils.ellipsize(brandLabel, brandPaint, layout.brandColWidth, TextUtils.TruncateAt.END).toString()
        canvas.drawText(truncatedBrand, brandColCenterX, brandTextY, brandPaint)

        val rightSubText = when {
            config.showCameraIndicator -> if (isFrontCamera) "Front Cam" else "Rear Cam"
            config.showWeather && telemetry.weatherTempCelsius != null -> "${telemetry.weatherTempCelsius.roundToInt()}°C"
            else -> telemetry.accuracyMeters?.let { "GPS ±${it.roundToInt()}m" }
                ?: if (telemetry.hasFix) "GPS LOCK" else "WAIT GPS"
        }
        val accPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (config.showWeather && telemetry.weatherTempCelsius != null) {
                Color.rgb(245, 197, 24)
            } else if (telemetry.hasFix) {
                layout.accentInt
            } else {
                Color.rgb(245, 197, 24)
            }
            textSize = 17f * baseScale
            typeface = layout.typefaceMono
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(rightSubText, brandColCenterX, brandTextY + 24f * baseScale, accPaint)

        if (layout.metricsList.isNotEmpty()) {
            val metricsY = cardRect.bottom - cardPadding - 4f * baseScale
            var metricCursorX = actualInfoLeft
            val metricTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = layout.titleTextColor
                textSize = layout.metaFontSize
                typeface = layout.typefaceMono
            }
            val dotRadius = 5.5f * baseScale
            val availableRight = cardRect.right - cardPadding
            for ((label, dotColor) in layout.metricsList) {
                val textW = metricTextPaint.measureText(label)
                val itemTotalW = dotRadius * 2f + 8f * baseScale + textW + 22f * baseScale
                if (metricCursorX + textW + dotRadius * 2f > availableRight) break

                val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(layout.foregroundAlphaInt, Color.red(dotColor), Color.green(dotColor), Color.blue(dotColor))
                    style = Paint.Style.FILL
                }
                canvas.drawCircle(
                    metricCursorX + dotRadius,
                    metricsY - layout.metaFontSize * 0.34f,
                    dotRadius,
                    dotPaint
                )
                canvas.drawText(label, metricCursorX + dotRadius * 2f + 8f * baseScale, metricsY, metricTextPaint)
                metricCursorX += itemTotalW
            }
        }
    }

    private data class PreparedStampCardLayout(
        val baseScale: Float,
        val isLight: Boolean,
        val accentInt: Int,
        val foregroundAlphaInt: Int,
        val logoAlphaInt: Int,
        val cardBgColor: Int,
        val titleTextColor: Int,
        val cardWidth: Float,
        val cardHeight: Float,
        val cardPadding: Float,
        val cornerRadius: Float,
        val useQrPanel: Boolean,
        val useCompassPanel: Boolean,
        val qrBitmap: Bitmap?,
        val activeMiniMap: Bitmap?,
        val leftModuleWidth: Float,
        val leftModuleGap: Float,
        val brandColWidth: Float,
        val logoBadgeSize: Float,
        val metaFontSize: Float,
        val metricsRowHeight: Float,
        val lineSpacing: Float,
        val typefaceBold: Typeface,
        val typefaceMono: Typeface,
        val wrappedRows: List<Pair<String, StampTextLine>>,
        val metricsList: List<Pair<String, Int>>
    )

    private fun drawCompassDial(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        azimuthDegrees: Float,
        baseScale: Float
    ) {
        val dialFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(20, 30, 38)
            style = Paint.Style.FILL
        }
        val dialRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(245, 197, 24)
            style = Paint.Style.STROKE
            strokeWidth = 3f * baseScale
        }
        canvas.drawCircle(centerX, centerY, radius, dialFill)
        canvas.drawCircle(centerX, centerY, radius, dialRing)

        val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(180, 255, 255, 255)
            strokeWidth = 2f * baseScale
            strokeCap = Paint.Cap.ROUND
        }
        for (deg in 0 until 360 step 45) {
            val rad = Math.toRadians((deg - 90).toDouble())
            val inner = radius * if (deg % 90 == 0) 0.72f else 0.84f
            tickPaint.color = if (deg == 0) Color.rgb(255, 90, 95) else Color.argb(180, 255, 255, 255)
            canvas.drawLine(
                centerX + (inner * cos(rad)).toFloat(),
                centerY + (inner * sin(rad)).toFloat(),
                centerX + (radius * cos(rad)).toFloat(),
                centerY + (radius * sin(rad)).toFloat(),
                tickPaint
            )
        }

        val needleRad = Math.toRadians((azimuthDegrees - 90f).toDouble())
        val tipX = centerX + (radius * 0.68f * cos(needleRad)).toFloat()
        val tipY = centerY + (radius * 0.68f * sin(needleRad)).toFloat()
        val tailX = centerX - (radius * 0.32f * cos(needleRad)).toFloat()
        val tailY = centerY - (radius * 0.32f * sin(needleRad)).toFloat()
        val wingRad = needleRad + Math.PI / 2
        val wing = radius * 0.14f

        val needlePath = Path().apply {
            moveTo(tipX, tipY)
            lineTo(centerX + (wing * cos(wingRad)).toFloat(), centerY + (wing * sin(wingRad)).toFloat())
            lineTo(tailX, tailY)
            lineTo(centerX - (wing * cos(wingRad)).toFloat(), centerY - (wing * sin(wingRad)).toFloat())
            close()
        }
        val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(245, 197, 24)
            style = Paint.Style.FILL
        }
        canvas.drawPath(needlePath, needlePaint)
        canvas.drawCircle(centerX, centerY, 5f * baseScale, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
    }

    private fun drawRoundedBitmap(
        canvas: Canvas,
        bitmap: Bitmap,
        rect: RectF,
        radius: Float,
        alphaInt: Int = 255
    ) {
        val bw = bitmap.width.toFloat().coerceAtLeast(1f)
        val bh = bitmap.height.toFloat().coerceAtLeast(1f)
        val scale = maxOf(rect.width() / bw, rect.height() / bh)
        val dx = rect.centerX() - (bw * scale) / 2f
        val dy = rect.centerY() - (bh * scale) / 2f
        val shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val matrix = android.graphics.Matrix().apply {
            setScale(scale, scale)
            postTranslate(dx, dy)
        }
        shader.setLocalMatrix(matrix)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            this.shader = shader
            this.alpha = alphaInt.coerceIn(0, 255)
        }
        canvas.drawRoundRect(rect, radius, radius, paint)
    }

    private fun loadTypeface(font: StampFont): Typeface {
        val resId = when (font) {
            StampFont.SPACE_GROTESK -> R.font.space_grotesk
            StampFont.JETBRAINS_MONO -> R.font.jetbrains_mono
            StampFont.PLUS_JAKARTA_SANS -> R.font.plus_jakarta_sans
        }
        return loadFontCached(resId) ?: Typeface.SANS_SERIF
    }

    private fun loadFontCached(resId: Int): Typeface? {
        typefaceCache[resId]?.let { return it }
        return ResourcesCompat.getFont(context, resId)?.also { loaded ->
            typefaceCache.putIfAbsent(resId, loaded)
        }
    }

    private data class StampTextLine(
        val text: String,
        val size: Float,
        val color: Int,
        val typeface: Typeface,
        val maxLines: Int = 1
    )
}
