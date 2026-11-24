package co.gapfinder.mobile.screens.home

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import co.gapfinder.mobile.data.Caches
import co.gapfinder.mobile.data.ChatDeliveryReport
import co.gapfinder.mobile.data.ChatRepository
import co.gapfinder.mobile.data.DeliveryRoute
import co.gapfinder.mobile.data.lastServerId
import co.gapfinder.mobile.data.local.LocalStore
import co.gapfinder.mobile.data.mergeChatOffMain
import co.gapfinder.mobile.data.outbox.ChatCouriers
import co.gapfinder.mobile.data.outbox.Delivery
import co.gapfinder.mobile.data.processChatPage
import co.gapfinder.mobile.domain.ChatMessage
import co.gapfinder.mobile.domain.ChatStatus
import co.gapfinder.mobile.domain.Member
import co.gapfinder.mobile.foundation.Connectivity
import co.gapfinder.mobile.foundation.SessionVault
import co.gapfinder.mobile.foundation.rememberOnline
import co.gapfinder.mobile.foundation.userText
import co.gapfinder.mobile.ui.kit.Glyphs
import co.gapfinder.mobile.ui.kit.OfflineNote
import co.gapfinder.mobile.ui.kit.PillButton
import co.gapfinder.mobile.ui.kit.Portrait
import co.gapfinder.mobile.ui.kit.inkTap
import co.gapfinder.mobile.ui.nav.StackNavigator
import co.gapfinder.mobile.ui.nav.Toaster
import co.gapfinder.mobile.ui.theme.Palette
import co.gapfinder.mobile.ui.theme.Typo
import co.gapfinder.mobile.ui.theme.fade
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

private const val TAG = "ChatPage"
private const val POLL_EVERY_MS = 5_000L

private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
private val DAY_TIME_FORMAT = DateTimeFormatter.ofPattern("MMM d · HH:mm", Locale.ENGLISH)

/**
 * Estado y lógica del chat con un amigo. Vive mientras la pantalla esté en la pila (remember).
 * Room es la fuente de verdad: todo cambio se escribe primero ahí y después se muestra.
 * Los cambios a la lista pasan por [lock] para que el polling y el envío no se pisen.
 */
private class ChatSession(val friend: Member) {
    var messages by mutableStateOf<List<ChatMessage>>(emptyList())
        private set
    var loaded by mutableStateOf(false)
        private set
    var fault by mutableStateOf<String?>(null)
        private set

    private val lock = Mutex()

    // Sube con cada mensaje escrito o cambiado aquí. El polling lo compara para saber si lo que leyó de Room quedó viejo
    private var edits = 0

    /** Muestra la lista y la deja en memoria. Siempre se llama con [lock] tomado. */
    private fun show(list: List<ChatMessage>) {
        messages = list
        loaded = true
        Caches.chats.put(friend.id, list)
    }

    /** Al abrir: memoria → Room. La red la pide [refresh]. */
    suspend fun open() {
        lock.withLock {
            if (loaded) return
            val me = SessionVault.memberId()
            // Estrategia: caching. Primero la memoria (TtlCache): reabrir un chat se ve al instante
            val cached = Caches.chats.fresh(friend.id)
            Log.d(TAG, "Chat with ${friend.id}: ${if (cached != null) "memory HIT" else "memory MISS, reading Room"}")
            // Estrategia: local storage. Si no está en memoria, lo guardado en Room (también funciona sin red)
            show(cached ?: me?.let { LocalStore.chatWith(it, friend.id) } ?: emptyList())
        }
    }

    /**
     * Pide al backend lo nuevo (afterId = último id que ya llegó por GET) y lo une con lo guardado.
     * Devuelve true si llegó una página llena (50) y avanzó: hay más y conviene pedir de una vez.
     */
    suspend fun refresh(): Boolean {
        val me = SessionVault.memberId() ?: return false
        val editsBefore = edits
        val afterId = lastServerId(messages)
        return try {
            // Estrategia: multithreading. async: el GET al backend y la lectura de Room van en paralelo
            val (savedFirst, body) = coroutineScope {
                val saved = async { LocalStore.chatWith(me, friend.id) }
                val fresh = async { ChatRepository.fetchAfter(me, friend.id, afterId) }
                saved.await() to fresh.await()
            }
            lock.withLock {
                // Si mientras llegaba la respuesta se escribió o cambió un mensaje, lo leído de Room ya está viejo
                val saved = if (edits == editsBefore) savedFirst else LocalStore.chatWith(me, friend.id)
                // Estrategia: multithreading. Parsear el JSON, unir, deduplicar por clientId y ordenar: Dispatchers.Default
                val page = processChatPage(saved, body)
                // Estrategia: local storage. El lote del backend se guarda en una sola transacción
                if (page.arrived.isNotEmpty()) LocalStore.saveChatPage(me, friend.id, page.arrived)
                show(page.messages)
                fault = null
                page.arrived.size >= ChatRepository.PAGE_SIZE && lastServerId(page.messages) > afterId
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "Error loading the chat: $e")
            // Con mensajes en pantalla se siguen mostrando; el error solo se ve si no hay nada
            if (messages.isEmpty()) fault = e.userText()
            false
        }
    }

    /**
     * Escribe un mensaje: sale de una vez con el reloj y se manda sin que la pantalla espere la red.
     * NonCancellable: si el usuario cierra el chat a mitad del envío, el envío termina igual
     * (si no, el mensaje quedaría PENDING sin estar en la cola).
     */
    suspend fun send(text: String) = withContext(NonCancellable) {
        val tappedAt = System.currentTimeMillis()
        val me = SessionVault.memberId() ?: return@withContext
        val content = text.trim().take(ChatMessage.MAX_LENGTH)
        if (content.isEmpty()) return@withContext

        // Estrategia: eventual connectivity (1). clientId nuevo; se guarda en Room como PENDING y sale con el reloj
        val message = ChatMessage(
            clientId = UUID.randomUUID().toString(),
            senderId = me,
            receiverId = friend.id,
            content = content,
            createdAt = LocalDateTime.now(),
            status = ChatStatus.PENDING,
        )

        if (!Connectivity.isOnline()) {
            // Estrategia: eventual connectivity (2). Sin red va directo a la cola
            change { LocalStore.saveChatMessage(me, friend.id, message); message }
            ChatCouriers.enqueueSend(me, message, tappedAt)
            Toaster.show("No connection: your message will be sent when you're back online")
            return@withContext
        }

        coroutineScope {
            // Estrategia: multithreading. async: el POST sale de una vez, mientras el mensaje se guarda en Room
            // y aparece en pantalla
            val posting = async {
                val result = try {
                    ChatCouriers.post(friend.id, message.content, message.clientId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Delivery.Retry(e.toString())
                }
                // BQ 17: la hora en que respondió el servidor, tomada aquí para no contar lo que tarde Room
                result to System.currentTimeMillis()
            }
            change { LocalStore.saveChatMessage(me, friend.id, message); message }

            val (delivery, savedAt) = posting.await()
            when (delivery) {
                // 201, o 409 porque ya estaba guardado
                Delivery.Sent -> {
                    change { LocalStore.markChatSent(message.clientId) }
                    // BQ 17: salió de una vez. El reporte va después de mostrar el ✓, así no retrasa el chat
                    ChatDeliveryReport.report(me, message.clientId, DeliveryRoute.Direct, tappedAt, savedAt)
                }
                // 400 / 422: queda FAILED con el motivo y el botón Retry
                is Delivery.Rejected -> change { LocalStore.markChatFailed(message.clientId, delivery.reason) }
                // Estrategia: eventual connectivity (2). La red falló a mitad: a la cola, sigue con el reloj
                is Delivery.Retry -> ChatCouriers.enqueueSend(me, message, tappedAt)
            }
        }
    }

    /** Retry de un mensaje FAILED: vuelve a PENDING y a la cola con el mismo clientId (el backend no lo duplica). */
    suspend fun retry(message: ChatMessage) = withContext(NonCancellable) {
        val me = SessionVault.memberId() ?: return@withContext
        change { LocalStore.markChatPending(message.clientId) }
        // Un reintento cuenta desde que se tocó Retry
        ChatCouriers.enqueueSend(me, message, tappedAt = System.currentTimeMillis())
    }

    /** Aplica un cambio a un mensaje: primero en Room ([write]) y luego en pantalla. */
    private suspend fun change(write: suspend () -> ChatMessage?) {
        lock.withLock {
            val changed = write() ?: return
            edits++
            show(mergeChatOffMain(messages, changed))
        }
    }
}

/** Conversación con un amigo: enviar y ver mensajes (sin lista de chats, sin leído ni contadores). */
@Composable
fun ChatPage(friend: Member) {
    val session = remember(friend.id) { ChatSession(friend) }
    val me = remember { SessionVault.memberIdNow() }
    var draft by remember { mutableStateOf("") }
    val online by rememberOnline()
    val scope = rememberCoroutineScope()

    // Al abrir: memoria → Room. Después, con red, el polling: cada 5 s pide solo lo nuevo (afterId).
    // Corre solo con el chat abierto y la app en primer plano (repeatOnLifecycle STARTED, igual que las
    // notificaciones de HomeShell). Sin red se pausa y vuelve a arrancar cuando vuelve la red.
    // Es parte de la feature, no la estrategia de multithreading.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(online, lifecycle) {
        session.open()
        if (!online) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                val more = session.refresh()
                // Página llena: quedan mensajes en el backend y se piden de una vez
                if (!more) delay(POLL_EVERY_MS)
            }
        }
    }

    fun send() {
        val text = draft
        if (text.isBlank()) return
        draft = ""
        scope.launch {
            try {
                session.send(text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d(TAG, "Error sending a message: $e")
                Toaster.show(e.userText())
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Palette.Fog)) {
        ChatTopBar(friend)
        if (!online) {
            OfflineNote(
                "No connection · showing saved messages. New ones go out when you're back online",
                Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp),
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            val current = session.messages
            val fault = session.fault
            when {
                !session.loaded -> CircularProgressIndicator(Modifier.size(36.dp).align(Alignment.Center))
                current.isEmpty() && fault != null -> ChatFault(fault) { scope.launch { session.refresh() } }
                current.isEmpty() -> EmptyChat(friend, online)
                else -> MessageList(current, me) { failed -> scope.launch { session.retry(failed) } }
            }
        }

        Composer(
            text = draft,
            onText = { draft = it.take(ChatMessage.MAX_LENGTH) },
            onSend = { send() },
        )
    }
}

// ============================================================ Barra superior

@Composable
private fun ChatTopBar(friend: Member) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Palette.Ink)
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .statusBarsPadding(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Glyphs.backIos,
            contentDescription = "Back",
            tint = Palette.Fog,
            modifier = Modifier
                .inkTap(CircleShape) { StackNavigator.back() }
                .size(20.dp),
        )
        Spacer(Modifier.width(14.dp))
        Portrait(url = friend.avatarUrl, size = 36.dp, border = BorderStroke(1.5.dp, Palette.Fog.fade(0.25f)))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = friend.name.ifBlank { "Friend" },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = Typo.heading(FontWeight.ExtraBold).copy(fontSize = 16.sp, color = Palette.Fog, letterSpacing = 0.5.sp),
            )
            if (friend.career.isNotBlank()) {
                Text(
                    text = friend.career,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = Typo.label().copy(fontSize = 12.sp, color = Palette.Fog.fade(0.65f)),
                )
            }
        }
    }
}

// ============================================================ Mensajes

@Composable
private fun MessageList(messages: List<ChatMessage>, me: Int?, onRetry: (ChatMessage) -> Unit) {
    val listState = rememberLazyListState()
    val maxBubble = (LocalConfiguration.current.screenWidthDp * 0.75f).dp

    // Lista al revés: el mensaje más nuevo queda abajo y la lista arranca ahí. Si llega uno nuevo, se baja a verlo
    val newest = messages.lastOrNull()?.clientId
    LaunchedEffect(newest) { listState.animateScrollToItem(0) }

    LazyColumn(
        state = listState,
        reverseLayout = true,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        items(messages.asReversed(), key = { it.clientId }) { message ->
            Bubble(message, mine = message.isMine(me), maxWidth = maxBubble, onRetry = { onRetry(message) })
        }
    }
}

/** Las mías a la derecha en coral con texto blanco; las del amigo a la izquierda en blanco con borde suave. */
@Composable
private fun Bubble(message: ChatMessage, mine: Boolean, maxWidth: Dp, onRetry: () -> Unit) {
    val shape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = if (mine) 16.dp else 4.dp,
        bottomEnd = if (mine) 4.dp else 16.dp,
    )
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
    ) {
        Text(
            text = message.content,
            style = Typo.paragraph().copy(fontSize = 14.sp, color = if (mine) Palette.White else Palette.Ink),
            modifier = Modifier
                .widthIn(max = maxWidth)
                .background(if (mine) Palette.Coral else Palette.White, shape)
                .border(1.dp, if (mine) Palette.Coral else Palette.Ink.fade(0.1f), shape)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = timeText(message.createdAt),
                style = Typo.paragraph().copy(fontSize = 10.sp, color = Palette.Ink.fade(0.4f)),
            )
            if (mine) {
                Spacer(Modifier.width(4.dp))
                StatusGlyph(message.status)
            }
        }
        if (mine && message.status == ChatStatus.FAILED) {
            Row(Modifier.widthIn(max = maxWidth), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = message.failReason ?: "Couldn't send this message",
                    style = Typo.paragraph().copy(fontSize = 11.sp, color = Palette.Coral),
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = "Retry",
                    style = Typo.paragraph(FontWeight.Bold).copy(fontSize = 12.sp, color = Palette.Coral),
                    modifier = Modifier
                        .inkTap(RoundedCornerShape(8.dp), onTap = onRetry)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/** ✓ enviado · reloj pendiente · ⚠ fallido. */
@Composable
private fun StatusGlyph(status: ChatStatus) {
    val (glyph, tint, label) = when (status) {
        ChatStatus.SENT -> Triple(Glyphs.checkRounded, Palette.Teal, "Sent")
        ChatStatus.PENDING -> Triple(Glyphs.scheduleRounded, Palette.Ink.fade(0.4f), "Waiting to send")
        ChatStatus.FAILED -> Triple(Glyphs.warningAmberRounded, Palette.Coral, "Not sent")
    }
    Icon(glyph, contentDescription = label, tint = tint, modifier = Modifier.size(13.dp))
}

/** "14:05" si es de hoy; "Nov 18 · 14:05" si es de otro día. */
private fun timeText(moment: LocalDateTime): String =
    moment.format(if (moment.toLocalDate() == LocalDate.now()) TIME_FORMAT else DAY_TIME_FORMAT)

// ============================================================ Campo para escribir

@Composable
private fun Composer(text: String, onText: (String) -> Unit, onSend: () -> Unit) {
    val canSend = text.isNotBlank()
    val typed = Typo.paragraph().copy(fontSize = 14.sp, color = Palette.Ink)

    Row(
        Modifier
            .fillMaxWidth()
            .background(Palette.White)
            .navigationBarsPadding()
            .imePadding()
            .padding(start = 16.dp, top = 10.dp, end = 8.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            BasicTextField(
                value = text,
                onValueChange = onText,
                modifier = Modifier.fillMaxWidth(),
                textStyle = typed,
                maxLines = 4,
                cursorBrush = SolidColor(Palette.Coral),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                decorationBox = { field ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(Palette.Fog, RoundedCornerShape(20.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        if (text.isEmpty()) {
                            Text("Write a message", style = typed.copy(color = Palette.Ink.fade(0.4f)))
                        }
                        field()
                    }
                },
            )
            // El backend no acepta más de 500 caracteres: el campo no deja pasar de ahí y avisa cuando falta poco
            if (text.length > ChatMessage.MAX_LENGTH - 50) {
                Text(
                    text = "${text.length}/${ChatMessage.MAX_LENGTH}",
                    textAlign = TextAlign.End,
                    style = Typo.paragraph().copy(fontSize = 11.sp, color = Palette.Ink.fade(0.45f)),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, end = 6.dp),
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier
                .size(48.dp)
                .inkTap(CircleShape, enabled = canSend, onTap = onSend),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Glyphs.sendRounded,
                contentDescription = "Send",
                tint = if (canSend) Palette.Coral else Palette.Ink.fade(0.25f),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

// ============================================================ Vacío y error

@Composable
private fun EmptyChat(friend: Member, online: Boolean) {
    val firstName = friend.name.split(" ").first().ifBlank { "your friend" }
    Column(
        Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Glyphs.chatBubbleOutlineRounded, contentDescription = null, tint = Palette.Ink.fade(0.12f), modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (online) "No messages yet. Say hi to $firstName!" else "No connection and no saved messages with $firstName yet.",
            textAlign = TextAlign.Center,
            style = Typo.paragraph().copy(fontSize = 14.sp, color = Palette.Ink.fade(0.45f)),
        )
    }
}

@Composable
private fun ChatFault(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Glyphs.errorOutlineRounded, contentDescription = null, tint = Palette.Coral, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text("Couldn't load the chat", style = Typo.heading(FontWeight.Bold).copy(fontSize = 16.sp, color = Palette.Ink))
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            textAlign = TextAlign.Center,
            style = Typo.paragraph().copy(fontSize = 14.sp, color = Palette.Ink.fade(0.55f)),
        )
        Spacer(Modifier.height(24.dp))
        PillButton(label = "Try again", onClick = onRetry, modifier = Modifier.widthIn(max = 220.dp))
    }
}
