package pl.slqpl.rodzina.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import pl.slqpl.rodzina.FamilyLocatorApp
import pl.slqpl.rodzina.cloud.FamilySnapshot
import pl.slqpl.rodzina.cloud.FamilyIcon
import pl.slqpl.rodzina.location.TrackingMode
import pl.slqpl.rodzina.cloud.HistorySelection
import pl.slqpl.rodzina.cloud.HistorySnapshot
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class LocatorViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as FamilyLocatorApp
    val status = app.tracking.status
    val configured = app.cloud.configured
    val user = app.cloud.user
    val pending = app.database.locations().observePendingCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    private val mutableCloudMessage = MutableStateFlow("")
    val cloudMessage = mutableCloudMessage.asStateFlow()
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    private val mutableMemberBusy = MutableStateFlow<String?>(null)
    val memberBusy = mutableMemberBusy.asStateFlow()
    private val mutableMemberMessage = MutableStateFlow("")
    val memberMessage = mutableMemberMessage.asStateFlow()
    private val refresh = MutableStateFlow(0)
    private val historySelection = MutableStateFlow<HistorySelection?>(null)
    private val historyRefresh = MutableStateFlow(0)
    private val mutableHistoryBusy = MutableStateFlow(false)
    val historyBusy = mutableHistoryBusy.asStateFlow()
    private val mutableHistoryMessage = MutableStateFlow("")
    val historyMessage = mutableHistoryMessage.asStateFlow()
    private var lastMaintenance = 0L
    val family: StateFlow<FamilySnapshot> = combine(user, refresh) { current, _ -> current }
        .flatMapLatest { current ->
            mutableCloudMessage.value = ""
            if (current == null) flowOf(FamilySnapshot()) else app.cloud.observeFamily(current.uid)
                .onStart { emit(FamilySnapshot()) }
                .catch { error ->
                    if (error is CancellationException) throw error
                    mutableCloudMessage.value = "Brak dostępu do rodziny. Sprawdź przypisanie konta i połączenie."
                    emit(FamilySnapshot())
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0, 0), FamilySnapshot())

    val latest = combine(app.database.locations().observeLatest(), user, family) { sample, current, snapshot ->
        val deleted = snapshot.members.firstOrNull { it.uid == current?.uid }?.historyDeletedThrough ?: 0
        sample?.takeIf { it.ownerUid == current?.uid && it.measuredAt > deleted }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val history: StateFlow<HistorySnapshot> = combine(user, historySelection, historyRefresh,
        family.map { snapshot -> snapshot.members.map { it.uid }.toSet() }.distinctUntilChanged()) { current, selected, _, roster ->
        current?.uid?.let { uid -> selected?.takeIf { it.uid in roster }?.let { uid to it } }
    }.flatMapLatest { request ->
        if (request == null) flowOf(HistorySnapshot()) else app.cloud.history.observe(request.first, request.second)
            .catch { error ->
                if (error is CancellationException) throw error
                emit(HistorySnapshot(request.second, error = "Brak dostępu do historii. Sprawdź połączenie, członkostwo i reguły Firebase."))
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0, 0), HistorySnapshot())

    fun showHistory(uid: String, day: LocalDate) {
        if (user.value == null) return
        historySelection.value = HistorySelection(uid, day)
        historyRefresh.value += 1
        mutableHistoryMessage.value = ""
        maintainHistory()
    }

    fun maintainHistory() {
        val uid = user.value?.uid ?: return
        val elapsed = android.os.SystemClock.elapsedRealtime()
        if (lastMaintenance != 0L && elapsed - lastMaintenance < 15 * 60 * 1000) return
        lastMaintenance = elapsed
        viewModelScope.launch {
            try { withTimeout(30_000) { app.cloud.history.maintain(uid) } }
            catch (cancelled: CancellationException) { if (cancelled !is TimeoutCancellationException) throw cancelled }
            catch (_: Exception) { /* The next active service or screen retries bounded maintenance. */ }
        }
    }

    fun deleteHistory(target: String, throughDate: LocalDate) {
        val uid = user.value?.uid ?: return
        if (family.value.members.none { it.uid == target } || mutableHistoryBusy.value || mutableMemberBusy.value != null || mutableBusy.value) return
        mutableHistoryBusy.value = true
        mutableHistoryMessage.value = "Usuwanie historii…"
        viewModelScope.launch {
            try {
                val through = withTimeout(120_000) { app.cloud.history.deleteThrough(uid, target, throughDate) }
                app.database.locations().deleteThrough(target, through)
                if (user.value?.uid == uid) mutableHistoryMessage.value = "Historia do wybranej daty została usunięta. Nowe pomiary będą nadal zapisywane."
            } catch (_: TimeoutCancellationException) {
                app.cloud.cancelPendingWrites()
                mutableHistoryMessage.value = "Nie udało się dokończyć usuwania. Sprawdź internet i ponów operację; zapisana granica usunięcia pozostaje aktywna."
            } catch (cancelled: CancellationException) {
                app.cloud.cancelPendingWrites(); throw cancelled
            } catch (_: Exception) {
                mutableHistoryMessage.value = "Nie udało się dokończyć usuwania. Sprawdź internet i reguły Firebase. Jeśli zapisano granicę usunięcia, pomiary z tego zakresu pozostaną ukryte."
            } finally { mutableHistoryBusy.value = false; historyRefresh.value += 1 }
        }
    }

    fun signIn(email: String, password: String) {
        if (status.value.running || mutableBusy.value || mutableMemberBusy.value != null || mutableHistoryBusy.value) return
        viewModelScope.launch {
            mutableBusy.value = true
            try {
                app.cloud.signIn(email, password)
                mutableCloudMessage.value = ""
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                mutableCloudMessage.value = "Nie udało się zalogować. Sprawdź dane konta, internet i konfigurację Firebase."
            } finally { mutableBusy.value = false }
        }
    }
    fun signOut() {
        if (status.value.running || mutableBusy.value || mutableMemberBusy.value != null || mutableHistoryBusy.value) return
        viewModelScope.launch {
            app.cloud.signOut()
            app.database.locations().clear()
            mutableCloudMessage.value = ""
            mutableMemberMessage.value = ""
            historySelection.value = null
            mutableHistoryMessage.value = ""
            lastMaintenance = 0
        }
    }
    fun renameMember(targetUid: String, name: String) = memberAction(targetUid, "Nazwa zapisana dla całej rodziny.") { uid ->
        app.cloud.renameMember(uid, targetUid, name)
    }
    fun requestTracking(targetUid: String, mode: TrackingMode) = memberAction(targetUid, "Polecenie wysłane. Czekamy na potwierdzenie telefonu.") { uid ->
        app.cloud.requestTracking(uid, targetUid, mode)
    }
    fun setOwnIcon(icon: FamilyIcon?) {
        val uid = user.value?.uid ?: return
        memberAction(uid, "Twoja ikonka została zapisana dla całej rodziny.") { currentUid ->
            app.cloud.setOwnIcon(currentUid, icon)
        }
    }
    private fun memberAction(targetUid: String, success: String, action: suspend (String) -> Unit) {
        val uid = user.value?.uid ?: return
        if (mutableMemberBusy.value != null || mutableBusy.value || mutableHistoryBusy.value) return
        mutableMemberBusy.value = targetUid
        mutableMemberMessage.value = ""
        viewModelScope.launch {
            try {
                withTimeout(10_000) { action(uid) }
                if (user.value?.uid == uid) mutableMemberMessage.value = success
            } catch (_: TimeoutCancellationException) {
                // Do not leave a timed-out command queued for an unexpected later delivery.
                app.cloud.cancelPendingWrites()
                mutableMemberMessage.value = "Brak potwierdzenia serwera. Sprawdź internet i spróbuj ponownie."
            } catch (cancelled: CancellationException) {
                app.cloud.cancelPendingWrites()
                throw cancelled
            } catch (_: Exception) {
                mutableMemberMessage.value = "Nie udało się zapisać zmiany. Sprawdź połączenie, członkostwo i reguły Firebase."
            } finally { mutableMemberBusy.value = null }
        }
    }
    fun clearLocalData() {
        if (!status.value.running) viewModelScope.launch { app.database.locations().clear() }
    }
    fun refreshFamily() { refresh.value += 1 }
    fun report(message: String) { app.tracking.update { it.copy(message = message) } }
}
