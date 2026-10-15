package pl.slqpl.rodzina.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import pl.slqpl.rodzina.FamilyLocatorApp
import pl.slqpl.rodzina.cloud.FamilyPosition

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
    private val refresh = MutableStateFlow(0)
    val family: StateFlow<List<FamilyPosition>> = combine(user, refresh) { current, _ -> current }
        .flatMapLatest { current ->
            mutableCloudMessage.value = ""
            if (current == null) flowOf(emptyList()) else app.cloud.observeFamily(current.uid)
                .onStart { emit(emptyList()) }
                .catch { error ->
                    if (error is CancellationException) throw error
                    mutableCloudMessage.value = "Brak dostępu do rodziny. Sprawdź przypisanie konta i połączenie."
                    emit(emptyList())
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0, 0), emptyList())

    fun signIn(email: String, password: String) {
        if (status.value.running || mutableBusy.value) return
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
        if (status.value.running || mutableBusy.value) return
        viewModelScope.launch {
            app.cloud.signOut()
            app.database.locations().clear()
            mutableCloudMessage.value = ""
        }
    }
    fun clearLocalData() {
        if (!status.value.running) viewModelScope.launch { app.database.locations().clear() }
    }
    fun refreshFamily() { refresh.value += 1 }
    fun report(message: String) { app.tracking.update { it.copy(message = message) } }
}
