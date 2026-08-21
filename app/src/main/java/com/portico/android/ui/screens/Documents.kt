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
import androidx.core.net.toUri
import com.portico.android.data.PorticoFiles
import com.portico.android.data.PorticoStore
import com.portico.android.domain.*
import com.portico.android.ui.PorticoState
import com.portico.android.ui.UploadStage
import com.portico.android.ui.design.*
import com.portico.android.ui.theme.PorticoTheme
import kotlinx.coroutines.launch

/*
 * Documents are private, owner-scoped records. Metadata is synchronized in
 * Firestore and file bytes travel only through the authenticated file bridge.
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
                supporting = "${visible.size} documents · private to this workspace"
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
            "Private files are encrypted in transit and can only be fetched through your signed-in Portico session."
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
    val scope = rememberCoroutineScope()
    val semantic = PorticoTheme.semantic
    var opening by remember(document.id) { mutableStateOf(false) }
    var deleting by remember(document.id) { mutableStateOf(false) }
    var pdfHandle by remember(document.id) { mutableStateOf<PdfDocumentHandle?>(null) }

    // The renderer holds a file descriptor. Leaving the screen without closing
    // it leaks one per document opened.
    DisposableEffect(document.id) {
        onDispose { pdfHandle?.close() }
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
                                .fillMaxWidth(0.5f)
                                .widthIn(max = 160.dp)
                                .aspectRatio(0.72f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(semantic.panel),
                            contentAlignment = Alignment.Center
                        ) {
                            PorticoIcon(Glyph.DOCUMENT, size = 42.dp, tint = semantic.tertiaryText, contentDescription = null)
                        }
                        Spacer(Modifier.height(Space.lg))
                        /*
                         * PDFs open inside Portico. These are private documents,
                         * and handing a content URI to whichever app claims the
                         * MIME type is the moment the product stops having
                         * custody of them. Anything that is not a PDF still goes
                         * out to the system, because rendering arbitrary formats
                         * is not this app's job.
                         */
                        val isPdf = document.fileName.substringAfterLast('.', "")
                            .equals("pdf", ignoreCase = true)
                        PrimaryButton(
                            if (isPdf) "Read document" else "Open in document viewer",
                            glyph = if (isPdf) Glyph.DOCUMENT else Glyph.EXTERNAL,
                            loading = opening
                        ) {
                            scope.launch {
                                opening = true
                                runCatching {
                                    val uri = if (document.storagePath.startsWith("content://")) {
                                        document.storagePath.toUri()
                                    } else {
                                        PorticoFiles.download(context, document)
                                    }
                                    if (isPdf) {
                                        pdfHandle?.close()
                                        pdfHandle = openPdf(context, uri).getOrThrow()
                                    } else {
                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                            setDataAndType(uri, documentMimeType(document.fileName))
                                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(intent)
                                    }
                                }.onFailure { error ->
                                    state.notify(error.message ?: "This document could not be opened.")
                                }
                                opening = false
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

        pdfHandle?.let { handle ->
            Panel(Modifier.padding(horizontal = Space.lg)) {
                PanelHeader(
                    "Document",
                    action = "Close",
                    onAction = {
                        handle.close()
                        pdfHandle = null
                    }
                )
                PdfViewer(handle, Modifier.padding(Space.sm).heightIn(max = 1_400.dp))
            }
        }

        Box(Modifier.padding(horizontal = Space.lg)) {
            SecondaryButton(
                if (deleting) "Deleting…" else "Delete document",
                Modifier.fillMaxWidth(),
                enabled = !deleting,
                glyph = Glyph.DELETE,
                destructive = true
            ) {
                scope.launch {
                    deleting = true
                    runCatching { store.removeDocument(document.id) }
                        .onSuccess {
                            state.viewerDocumentId = null
                            state.notify("Document deleted")
                        }
                        .onFailure { state.notify(it.message ?: "Document could not be deleted") }
                    deleting = false
                }
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
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val semantic = PorticoTheme.semantic
    var failed by remember { mutableStateOf(false) }
    var failureMessage by remember { mutableStateOf("The file couldn't be uploaded. Check your connection and try again.") }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val documentId = PorticoStore.newId("d")
        state.uploadFileName = uri.lastPathSegment?.substringAfterLast('/') ?: "Document"
        state.uploadStage = UploadStage.UPLOADING
        scope.launch {
            runCatching {
                PorticoFiles.upload(
                    context = context,
                    source = uri,
                    resourceId = documentId
                )
            }.onSuccess { stored ->
                state.uploadFileName = stored.fileName
                val document = PortfolioDocument(
                    id = documentId,
                    propertyId = state.uploadPropertyId,
                    fileName = stored.fileName,
                    fileType = stored.contentType.substringAfter('/').uppercase(),
                    category = state.uploadCategory,
                    uploadedAt = SimpleDate.today().format(),
                    sizeBytes = stored.sizeBytes,
                    status = DocumentStatus.READY,
                    storagePath = stored.pathname
                )
                store.addDocument(document)
                state.uploadStage = UploadStage.DONE
            }.onFailure { error ->
                failed = true
                failureMessage = error.message ?: "The private upload did not finish."
                state.uploadStage = UploadStage.FAILED
            }
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
                    body = failureMessage,
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

private fun documentMimeType(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
    "pdf" -> "application/pdf"
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "webp" -> "image/webp"
    else -> "application/octet-stream"
}
