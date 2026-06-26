# State and Architecture

## Boundaries

- Keep Composables focused on rendering and user events.
- Put business logic in ViewModels, repositories, services, or use-case classes already present in the repo.
- Keep data access behind repositories or existing data-source abstractions.
- Do not query databases, make network calls, or start long-running work directly from Composable bodies.

## UI State

Use a sealed state or data class that represents the screen honestly:

```kotlin
sealed interface MainUiState {
    data object Loading : MainUiState
    data class Ready(val items: List<Item>) : MainUiState
    data class Error(val message: String) : MainUiState
}
```

Expose read-only `StateFlow`:

```kotlin
class MainViewModel(
    private val repository: ItemRepository
) : ViewModel() {
    val uiState: StateFlow<MainUiState> = repository.items
        .map<_, MainUiState> { MainUiState.Ready(it) }
        .catch { emit(MainUiState.Error(it.message ?: "Unable to load items")) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MainUiState.Loading
        )

    fun addItem(name: String) {
        viewModelScope.launch {
            repository.insert(name)
        }
    }
}
```

Collect lifecycle-aware state in Compose:

```kotlin
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MainContent(state = state, onAdd = viewModel::addItem)
}
```

## Errors

- Surface user-actionable errors in UI state.
- Keep debug context in logs or exception messages without exposing secrets.
- Avoid catch-all handlers that silently discard failures.
