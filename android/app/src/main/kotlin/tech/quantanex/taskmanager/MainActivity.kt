package tech.quantanex.taskmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import tech.quantanex.taskmanager.data.EncryptedInboxTaskStoreFactory
import tech.quantanex.taskmanager.ui.InboxScreen
import tech.quantanex.taskmanager.ui.InboxViewModel
import tech.quantanex.taskmanager.ui.TaskManagerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TaskManagerTheme {
                val inboxViewModel: InboxViewModel = viewModel(factory = inboxViewModelFactory())
                InboxScreen(inboxViewModel)
            }
        }
    }

    private fun inboxViewModelFactory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(InboxViewModel::class.java))
            return InboxViewModel(storeProvider = { EncryptedInboxTaskStoreFactory.open(applicationContext) }) as T
        }
    }
}
