package com.portico.android.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.portico.android.data.PorticoFiles
import com.portico.android.data.PorticoStore
import com.portico.android.ui.theme.PorticoTheme

/*
 * Property imagery.
 *
 * A photograph is the one place in Portico where a rectangle of arbitrary
 * colour is allowed on screen, so it is framed the same way every other
 * surface is: one hairline, the control radius, no shadow. The image sits
 * inside the frame rather than bleeding past it, which keeps a bright holiday
 * snapshot from overpowering a column of figures next to it.
 *
 * When there is no photograph the frame stays and the Portico mark sits in it.
 * An empty slot that keeps its shape reads as "no photo yet"; a collapsed one
 * reads as a layout bug.
 */

/**
 * Turns a stored reference into something the image loader accepts.
 *
 * A picker URI is usable as-is. A blob pathname is private and needs the
 * session header, so it is fetched through the store into the on-disk cache
 * first. Null until that resolves, which is why the frame keeps its placeholder
 * instead of collapsing.
 */
@Composable
private fun rememberPhotoModel(reference: String?, store: PorticoStore?): Any? {
    if (reference.isNullOrBlank()) return null
    if (store == null || !PorticoFiles.isStoredReference(reference)) return reference

    var resolved by remember(reference) { mutableStateOf<Any?>(null) }
    LaunchedEffect(reference) { resolved = store.photoUri(reference) }
    return resolved
}

/** Small square used in the register and on dashboard rows. */
@Composable
fun PropertyThumbnail(
    photoUri: String?,
    propertyName: String,
    modifier: Modifier = Modifier,
    store: PorticoStore? = null,
    size: Dp = 44.dp
) {
    val semantic = PorticoTheme.semantic
    val model = rememberPhotoModel(photoUri, store)
    Box(
        modifier
            .size(size)
            .clip(ControlShape)
            .background(semantic.panelSunk)
            .border(1.dp, semantic.hairline, ControlShape),
        contentAlignment = Alignment.Center
    ) {
        if (model == null) {
            PorticoMark(size = size * 0.5f, contentDescription = null)
        } else {
            AsyncImage(
                model = model,
                contentDescription = "Photograph of $propertyName",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Wide image at the head of a property.
 *
 * 16:9 rather than a taller crop, because the figures underneath are what the
 * screen is for; the photograph confirms which building you are looking at and
 * then gets out of the way.
 */
@Composable
fun PropertyHero(
    photoUri: String?,
    propertyName: String,
    modifier: Modifier = Modifier,
    store: PorticoStore? = null
) {
    val semantic = PorticoTheme.semantic
    val model = rememberPhotoModel(photoUri, store)
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .background(semantic.panelSunk)
            .border(1.dp, semantic.hairline, RoundedCornerShape(12.dp))
            .semantics {
                contentDescription = if (model == null) {
                    "No photograph on file for $propertyName"
                } else {
                    "Photograph of $propertyName"
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (model == null) {
            PorticoMark(size = 48.dp, contentDescription = null)
        } else {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
