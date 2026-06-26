# Room Persistence

Use Room when the app stores lists, logs, sessions, cached records, or structured local data.

## Gradle

- Prefer KSP over KAPT.
- Add Room to `libs.versions.toml` when the project uses a version catalog.
- Add `implementation(libs.androidx.room.runtime)`, `implementation(libs.androidx.room.ktx)`, and `ksp(libs.androidx.room.compiler)` in `app/build.gradle.kts`.
- Add the KSP plugin alias only if it is not already applied.

## Rules

- DAO read queries should return `Flow<T>` or `Flow<List<T>>`.
- Mutations should be `suspend`.
- Use repositories between ViewModels and DAOs.
- Do not access DAOs from Composables.
- Add migrations for existing shipped databases; only use destructive migration for throwaway prototypes or when explicitly accepted.

## Pattern

```kotlin
@Entity(tableName = "items")
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface ItemDao {
    @Query("SELECT * FROM items ORDER BY createdAt DESC")
    fun observeItems(): Flow<List<ItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ItemEntity)

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Database(entities = [ItemEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
}
```

Use `exportSchema = true` for production apps unless the existing repo intentionally disables schema export.
