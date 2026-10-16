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

@OptIn(ExperimentalCoroutinesApi::class)
class LocatorViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as FamilyLocatorApp
    val status = app.tracking.status
    val configured = app.cloud.configured
    val user = app.cloud.user
    val latest = app.database.locations().observeLatest().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
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

    fun signIn(email: String, password: String) {
        if (status.value.running || mutableBusy.value || mutableMemberBusy.value != null) return
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
        if (status.value.running || mutableBusy.value || mutableMemberBusy.value != null) return
        viewModelScope.launch {
            app.cloud.signOut()
            app.database.locations().clear()
            mutableCloudMessage.value = ""
            mutableMemberMessage.value = ""
        }
    }
    fun renameMember(targetUid: String, name: String) = memberAction(targetUid, "Nazwa zapisana dla całej rodziny.") { uid ->
        app.cloud.renameMember(uid, targetUid, name)
    }
    fun requestLive(targetUid: String) = memberAction(targetUid, "Żądanie wysłane. Czekamy na potwierdzenie telefonu.") { uid ->
        app.cloud.requestLive(uid, targetUid)
    }
    fun setOwnIcon(icon: FamilyIcon?) {
        val uid = user.value?.uid ?: return
        memberAction(uid, "Twoja ikonka została zapisana dla całej rodziny.") { currentUid ->
            app.cloud.setOwnIcon(currentUid, icon)
        }
    }
    private fun memberAction(targetUid: String, success: String, action: suspend (String) -> Unit) {
        val uid = user.value?.uid ?: return
        if (mutableMemberBusy.value != null || mutableBusy.value) return
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
