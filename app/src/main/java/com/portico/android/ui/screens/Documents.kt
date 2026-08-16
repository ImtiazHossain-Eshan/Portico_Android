@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.portico.android.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.portico.android.data.PorticoStore
import com.portico.android.domain.*
import com.portico.android.ui.PorticoState
import com.portico.android.ui.UploadStage
import com.portico.android.ui.design.*
import com.portico.android.ui.theme.PorticoTheme
import kotlinx.coroutines.delay

/*
 * Documents are treated as private records: nothing leaves the device, the
 * library states what it can and cannot open, and every failure has a way
 * forward rather than a dead end.
 */
@Composable
fun DocumentsScreen(state: PorticoState, modifier: Modifier = Modifier) {
    val store = state.store
    val categories = listOf("All") + DocumentCategory.entries.map { it.label }

    val visible = store.documents.filter { document ->
        val matchesCategory = state.documentCategory == "All" || document.category == state.documentCategory
        val matchesQuery = state.documentQuery.isBlank() ||
            document.fileName.contains(state.documentQuery, ignoreCase = true)
        matchesCategory && matchesQuery
    }

    // The viewer takes over the surface when a document is open.
    state.viewerDocumentId?.let { id ->
        val document = store.documents.firstOrNull { it.id == id }
        if (document != null) {
            DocumentViewer(state, document, modifier)
            return
        }
        state.viewerDocumentId = null
    }

    Column(modifier.padding(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(Space.lg)) {

        Column(Modifier.padding(horizontal = Space.lg)) {
            PorticoField(
                value = state.documentQuery,
                onValueChange = { state.documentQuery = it },
                label = "Search documents",
                placeholder = "Lease, deed, receipt…",
                trailing = {
                    if (state.documentQuery.isNotBlank()) {
                        GlyphButton(Glyph.CLOSE, "Clear search") { state.documentQuery = "" }
                    } else {
                        PorticoIcon(Glyph.SEARCH, size = 18.dp, tint = PorticoTheme.semantic.tertiaryText, contentDescription = null)
                    }
                }
            )
        }

        SegmentedRow(categories, state.documentCategory) { state.documentCategory = it }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader(
                "Library",
                supporting = "${visible.size} documents · stored on this device"
            )
            when {
                store.documents.isEmpty() -> EmptyState(
                    title = "No documents yet",
                    body = "Keep deeds, leases, insurance and tax receipts with the property they belong to.",
                    glyph = Glyph.DOCUMENT,
                    actionLabel = "Upload a document",
                    onAction = { state.uploadStage = UploadStage.PROPERTY; state.showUploadSheet = true }
                )
                visible.isEmpty() -> EmptyState(
                    title = "Nothing in ${state.documentCategory}",
                    body = "No document matches this category and search.",
                    glyph = Glyph.SEARCH,
                    actionLabel = "Show all",
                    onAction = { state.documentCategory = "All"; state.documentQuery = "" }
                )
                else -> visible.forEachIndexed { index, document ->
                    if (index > 0) Hairline()
                    DocumentRow(
                        document = document,
                        propertyName = store.propertyById(document.propertyId)?.name
                    ) { state.viewerDocumentId = document.id }
                }
            }
        }

        Box(Modifier.padding(horizontal = Space.lg)) {
            PrimaryButton("Upload document", Modifier.fillMaxWidth(), glyph = Glyph.UPLOAD) {
                state.uploadStage = UploadStage.PROPERTY
                state.showUploadSheet = true
            }
        }

        SyntheticNote(
            "Documents stay on this device. Cloud storage with per-user access rules is an integration seam, not a shipped service."
        )
    }

    if (state.showUploadSheet) {
        UploadSheet(state) { state.showUploadSheet = false }
    }
}

// ------------------------------------------------------------------- viewer

@Composable
private fun DocumentViewer(state: PorticoState, document: PortfolioDocument, modifier: Modifier = Modifier) {
    val store = state.store
    val context = androidx.compose.ui.platform.LocalContext.current
    val semantic = PorticoTheme.semantic
    var loading by remember(document.id) { mutableStateOf(true) }

    LaunchedEffect(document.id) {
        loading = true
        delay(450)
        loading = false
    }

    Column(modifier.padding(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(Space.lg)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(document.fileName, style = MaterialTheme.typography.titleLarge)
                Text(
                    listOfNotNull(
                        store.propertyById(document.propertyId)?.name,
                        document.category,
                        document.readableSize,
                        document.uploadedAt
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = semantic.tertiaryText
                )
            }
            GlyphButton(Glyph.CLOSE, "Close document") { state.viewerDocumentId = null }
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(380.dp)
                    .background(semantic.panelSunk),
                contentAlignment = Alignment.Center
            ) {
                when {
                    loading -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.height(Space.md))
                        Text("Opening document", style = MaterialTheme.typography.bodySmall, color = semantic.tertiaryText)
                    }

                    document.status == DocumentStatus.RESTRICTED -> PermissionDeniedState(
                        what = document.fileName,
                        onBack = { state.viewerDocumentId = null }
                    )

                    document.storagePath.isBlank() -> ErrorState(
                        title = "No file attached",
                        body = "This record has details but no stored file. It came from the sample portfolio. Upload a file to attach one.",
                        retryLabel = "Upload a file",
                        onRetry = {
                            state.uploadPropertyId = document.propertyId
                            state.uploadStage = UploadStage.FILE
                            state.showUploadSheet = true
                        }
                    )

                    else -> Column(
                        Modifier.fillMaxSize().padding(Space.lg),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Page frame stands in for the rendered document; the
                        // real file opens in the system viewer.
                        Box(
                            Modifier
                                .fillMaxWidth(0.62f)
                                .aspectRatio(0.72f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(semantic.panel),
                            contentAlignment = Alignment.Center
                        ) {
                            PorticoIcon(Glyph.DOCUMENT, size = 42.dp, tint = semantic.tertiaryText, contentDescription = null)
                        }
                        Spacer(Modifier.height(Space.lg))
                        PrimaryButton("Open in your PDF viewer", glyph = Glyph.EXTERNAL) {
                            runCatching {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                    setDataAndType(android.net.Uri.parse(document.storagePath), "application/pdf")
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(intent)
                            }.onFailure {
                                state.notify("No app on this device can open a PDF.")
                            }
                        }
                    }
                }
            }
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader("Details")
            DataRow("Category", document.category)
            Hairline()
            DataRow("Property", store.propertyById(document.propertyId)?.name ?: "Unassigned")
            Hairline()
            DataRow("Uploaded", document.uploadedAt)
            Hairline()
            DataRow("Size", document.readableSize)
            Hairline()
            DataRow("Visibility", "Private to this workspace")
        }

        Box(Modifier.padding(horizontal = Space.lg)) {
            SecondaryButton("Delete document", Modifier.fillMaxWidth(), glyph = Glyph.DELETE, destructive = true) {
                store.removeDocument(document.id)
                state.viewerDocumentId = null
                state.notify("Document deleted")
            }
        }
    }
}

// ------------------------------------------------------------ upload flow

/**
 * Select property → select file → upload → confirm, exactly the sequence the
 * blueprint specifies, with a real system file picker at the file step.
 */
@Composable
fun UploadSheet(state: PorticoState, onDismiss: () -> Unit) {
    val store = state.store
    val semantic = PorticoTheme.semantic
    var failed by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            state.uploadFileName = uri.lastPathSegment?.substringAfterLast('/') ?: "Document"
            val document = PortfolioDocument(
                id = PorticoStore.newId("d"),
                propertyId = state.uploadPropertyId,
                fileName = state.uploadFileName,
                fileType = "PDF",
                category = state.uploadCategory,
                uploadedAt = SimpleDate.today().format(),
                sizeBytes = 0,
                status = DocumentStatus.READY,
                storagePath = uri.toString()
            )
            store.addDocument(document)
            state.uploadStage = UploadStage.DONE
        }.onFailure {
            failed = true
            state.uploadStage = UploadStage.FAILED
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = semantic.panel) {
        Column(
            Modifier.padding(bottom = Space.xxl),
            verticalArrangement = Arrangement.spacedBy(Space.lg)
        ) {
            when (state.uploadStage) {
                UploadStage.PROPERTY -> {
                    Column(Modifier.padding(horizontal = Space.lg)) {
                        Text("Which property?", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Documents are filed against the property they belong to.",
                            style = MaterialTheme.typography.bodySmall,
                            color = semantic.tertiaryText
                        )
                    }
                    if (store.properties.isEmpty()) {
                        EmptyState(
                            title = "No properties yet",
                            body = "Add a property first, then its documents have somewhere to live.",
                            glyph = Glyph.PORTFOLIO
                        )
                    } else {
                        store.properties.forEach { property ->
                            PropertyPickerRow(
                                property = property,
                                selected = state.uploadPropertyId == property.id
                            ) { state.uploadPropertyId = property.id }
                        }
                        Box(Modifier.padding(horizontal = Space.lg)) {
                            PrimaryButton(
                                "Continue",
                                Modifier.fillMaxWidth(),
                                enabled = state.uploadPropertyId != null
                            ) { state.uploadStage = UploadStage.FILE }
                        }
                    }
                }

                UploadStage.FILE -> {
                    Column(Modifier.padding(horizontal = Space.lg)) {
                        Text("Choose a file", style = MaterialTheme.typography.titleLarge)
                        Text(
                            store.propertyById(state.uploadPropertyId)?.name ?: "Unassigned",
                            style = MaterialTheme.typography.bodySmall,
                            color = semantic.tertiaryText
                        )
                    }
                    ChoiceRow(
                        "Category",
                        DocumentCategory.entries.map { it.label },
                        state.uploadCategory
                    ) { state.uploadCategory = it }
                    Box(Modifier.padding(horizontal = Space.lg)) {
                        PrimaryButton("Browse files", Modifier.fillMaxWidth(), glyph = Glyph.UPLOAD) {
                            failed = false
                            runCatching {
                                picker.launch(arrayOf("application/pdf", "image/*"))
                            }.onFailure {
                                failed = true
                                state.uploadStage = UploadStage.FAILED
                            }
                        }
                    }
                    if (failed) {
                        Box(Modifier.padding(horizontal = Space.lg)) {
                            InlineError("Couldn't open the file picker on this device.")
                        }
                    }
                }

                UploadStage.UPLOADING -> Column(
                    Modifier.fillMaxWidth().padding(Space.xxl),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(Modifier.size(30.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.height(Space.lg))
                    Text("Storing ${state.uploadFileName}", style = MaterialTheme.typography.bodyMedium)
                }

                UploadStage.DONE -> SuccessState(
                    title = "Document saved",
                    body = "${state.uploadFileName} is filed under ${state.uploadCategory}.",
                    actionLabel = "Done",
                    onAction = {
                        state.uploadStage = UploadStage.PROPERTY
                        state.uploadFileName = ""
                        onDismiss()
                    },
                    secondaryLabel = "Upload another",
                    onSecondary = { state.uploadStage = UploadStage.FILE }
                )

                UploadStage.FAILED -> ErrorState(
                    title = "Upload didn't finish",
                    body = "The file couldn't be read. Check it still exists and try again.",
                    retryLabel = "Try again",
                    onRetry = { failed = false; state.uploadStage = UploadStage.FILE },
                    secondaryLabel = "Cancel",
                    onSecondary = {
                        state.uploadStage = UploadStage.PROPERTY
                        onDismiss()
                    }
                )
            }
        }
    }
}
